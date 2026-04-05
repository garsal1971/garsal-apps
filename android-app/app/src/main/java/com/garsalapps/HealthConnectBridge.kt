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
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.temporal.ChronoUnit

class HealthConnectBridge(
    private val activity: MainActivity,
    private val webView: WebView
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val readWeightPermission = HealthPermission.getReadPermission(WeightRecord::class)

    /** Controlla solo se l'SDK è installato sul dispositivo (sincrono, no runBlocking). */
    @JavascriptInterface
    fun isSdkAvailable(): Boolean {
        return try {
            HealthConnectClient.getSdkStatus(activity) == HealthConnectClient.SDK_AVAILABLE
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Punto di ingresso principale chiamato dal JS.
     *
     * Flusso:
     *  1. Verifica che HC sia installato
     *  2. Verifica i permessi
     *  3a. Se permessi mancanti → apre la schermata HC e ritorna {"ok":false,"error":"PERMISSION_REQUESTED"}
     *  3b. Se permessi ok → legge i record e ritorna {"ok":true,"points":[...]}
     */
    @JavascriptInterface
    fun requestWeightSync(callbackId: String) {
        scope.launch {
            val json = try {
                // 1. SDK installato?
                val sdkStatus = HealthConnectClient.getSdkStatus(activity)
                if (sdkStatus != HealthConnectClient.SDK_AVAILABLE) {
                    val msg = when (sdkStatus) {
                        HealthConnectClient.SDK_UNAVAILABLE -> "Health Connect non installato"
                        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> "Aggiorna Health Connect dal Play Store"
                        else -> "Health Connect non disponibile (status $sdkStatus)"
                    }
                    return@launch callback(callbackId, """{"ok":false,"error":"$msg"}""")
                }

                val client = HealthConnectClient.getOrCreate(activity)

                // 2. Permessi concessi?
                val granted = client.permissionController.getGrantedPermissions()
                if (readWeightPermission !in granted) {
                    // Apre la schermata permessi HC sul thread UI
                    withContext(Dispatchers.Main) {
                        activity.requestHealthConnectPermissions()
                    }
                    return@launch callback(callbackId,
                        """{"ok":false,"error":"PERMISSION_REQUESTED","retry":true}""")
                }

                // 3. Legge i dati peso (ultimi 90 giorni)
                val end   = Instant.now()
                val start = end.minus(90, ChronoUnit.DAYS)
                val response = client.readRecords(
                    ReadRecordsRequest(
                        recordType = WeightRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(start, end)
                    )
                )

                val points = response.records.joinToString(",") { r ->
                    """{"timestamp":${r.time.toEpochMilli()},"weight":${String.format("%.2f", r.weight.inKilograms)}}"""
                }
                """{"ok":true,"points":[$points]}"""

            } catch (e: Exception) {
                val msg = (e.message ?: "Errore sconosciuto").take(200).replace("\"", "'")
                """{"ok":false,"error":"$msg"}"""
            }

            callback(callbackId, json)
        }
    }

    private fun callback(callbackId: String, json: String) {
        webView.post {
            webView.evaluateJavascript(
                "if(window.__hcCallback_$callbackId)window.__hcCallback_$callbackId($json);",
                null
            )
        }
    }
}
