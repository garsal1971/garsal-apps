package com.garsalapps

import android.util.Log
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
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.temporal.ChronoUnit

class HealthConnectBridge(
    private val activity: MainActivity,
    private val webView: WebView
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @JavascriptInterface
    fun isSdkAvailable(): Boolean {
        return try {
            HealthConnectClient.getSdkStatus(activity) == HealthConnectClient.SDK_AVAILABLE
        } catch (e: Exception) {
            false
        }
    }

    @JavascriptInterface
    fun requestWeightSync(callbackId: String) {
        scope.launch {
            // Timeout 30s — HC su cold start (servizio non attivo) può impiegare > 12s
            val json = withTimeoutOrNull(30_000L) {
                try {
                    val sdkStatus = HealthConnectClient.getSdkStatus(activity)
                    Log.d("HCBridge", "SDK status: $sdkStatus")
                    if (sdkStatus != HealthConnectClient.SDK_AVAILABLE) {
                        val msg = when (sdkStatus) {
                            HealthConnectClient.SDK_UNAVAILABLE -> "Health Connect non installato"
                            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> "Aggiorna Health Connect dal Play Store"
                            else -> "HC non disponibile (status $sdkStatus)"
                        }
                        return@withTimeoutOrNull """{"ok":false,"error":"$msg"}"""
                    }

                    Log.d("HCBridge", "Creazione client HC...")
                    val client = HealthConnectClient.getOrCreate(activity)

                    val end   = Instant.now()
                    val start = end.minus(90, ChronoUnit.DAYS)
                    Log.d("HCBridge", "Lettura record peso (90 giorni)...")
                    val response = client.readRecords(
                        ReadRecordsRequest(
                            recordType = WeightRecord::class,
                            timeRangeFilter = TimeRangeFilter(startTime = start, endTime = end)
                        )
                    )

                    Log.d("HCBridge", "Record ricevuti: ${response.records.size}")
                    val points = response.records.joinToString(",") { r ->
                        """{"timestamp":${r.time.toEpochMilli()},"weight":${String.format("%.2f", r.weight.inKilograms)}}"""
                    }
                    """{"ok":true,"points":[$points]}"""

                } catch (e: SecurityException) {
                    Log.w("HCBridge", "SecurityException — permessi mancanti: ${e.message}")
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        activity.requestHealthConnectPermissions()
                    }
                    """{"ok":false,"error":"PERMISSION_REQUESTED","retry":true}"""

                } catch (e: Exception) {
                    // Include il tipo di eccezione nel messaggio per diagnostica
                    Log.e("HCBridge", "Errore HC: ${e.javaClass.name}: ${e.message}", e)
                    val msg = "[${e.javaClass.simpleName}] ${e.message ?: "Errore sconosciuto"}".take(200).replace("\"", "'")
                    """{"ok":false,"error":"$msg"}"""
                }
            } ?: run {
                Log.e("HCBridge", "Timeout 30s scaduto senza risposta da HC")
                """{"ok":false,"error":"Timeout 30s: HC non risponde. Prova ad aprire Health Connect una volta e riprova."}"""
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
