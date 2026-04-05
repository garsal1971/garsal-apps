package com.garsalapps

import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Bridge JavaScript esposto al WebView come window.HealthConnectBridge.
 * Permette a weight-quest.html di leggere i dati peso da Android Health Connect
 * senza esporre token o credenziali Google: i dati sono on-device.
 *
 * Metodi esposti:
 *  - isAvailable()         → Boolean (HC installato + permessi concessi)
 *  - requestPermissions()  → Unit (apre schermata permessi HC)
 *  - requestWeightSync(id) → async, chiama window.__hcCallback_<id>(json)
 */
class HealthConnectBridge(
    private val activity: MainActivity,
    private val webView: WebView
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val client: HealthConnectClient? by lazy {
        try {
            if (HealthConnectClient.getSdkStatus(activity) == HealthConnectClient.SDK_AVAILABLE) {
                HealthConnectClient.getOrCreate(activity)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private val readWeightPermission = HealthPermission.getReadPermission(WeightRecord::class)

    /**
     * Ritorna true se Health Connect è installato e il permesso READ_WEIGHT è stato concesso.
     * Chiamata dal thread JavaScript — deve essere sincrona.
     */
    @JavascriptInterface
    fun isAvailable(): Boolean {
        val c = client ?: return false
        return try {
            runBlocking {
                val granted = c.permissionController.getGrantedPermissions()
                readWeightPermission in granted
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Chiede all'Activity di aprire la schermata di consenso permessi Health Connect.
     * Dopo che l'utente concede i permessi, può richiamare requestWeightSync.
     */
    @JavascriptInterface
    fun requestPermissions() {
        activity.runOnUiThread {
            activity.requestHealthConnectPermissions()
        }
    }

    /**
     * Legge i record peso degli ultimi 90 giorni da Health Connect (asincrono).
     * Al termine chiama window.__hcCallback_<callbackId>(json) nel WebView.
     *
     * JSON successo:  {"ok":true,"points":[{"timestamp":1234567890,"weight":79.5},...]}
     * JSON errore:    {"ok":false,"error":"messaggio"}
     */
    @JavascriptInterface
    fun requestWeightSync(callbackId: String) {
        scope.launch {
            val json = try {
                val c = client ?: throw Exception("Health Connect non disponibile")

                val end = Instant.now()
                val start = end.minus(90, ChronoUnit.DAYS)

                val response = c.readRecords(
                    ReadRecordsRequest(
                        recordType = WeightRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(start, end)
                    )
                )

                val points = response.records.joinToString(",") { r ->
                    val ts = r.time.toEpochMilli()
                    val kg = r.weight.inKilograms
                    // Usa format con punto decimale (locale-safe)
                    """{"timestamp":$ts,"weight":${String.format("%.2f", kg)}}"""
                }
                """{"ok":true,"points":[$points]}"""
            } catch (e: Exception) {
                val msg = (e.message ?: "Errore sconosciuto")
                    .take(200)
                    .replace("\"", "'")
                """{"ok":false,"error":"$msg"}"""
            }

            webView.post {
                webView.evaluateJavascript(
                    "if(window.__hcCallback_$callbackId)window.__hcCallback_$callbackId($json);",
                    null
                )
            }
        }
    }
}
