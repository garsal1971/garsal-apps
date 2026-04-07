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
            // Timeout globale 12 secondi — se HC non risponde il callback scatta comunque
            val json = withTimeoutOrNull(12_000L) {
                try {
                    val sdkStatus = HealthConnectClient.getSdkStatus(activity)
                    if (sdkStatus != HealthConnectClient.SDK_AVAILABLE) {
                        val msg = when (sdkStatus) {
                            HealthConnectClient.SDK_UNAVAILABLE -> "Health Connect non installato"
                            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> "Aggiorna Health Connect dal Play Store"
                            else -> "HC non disponibile (status $sdkStatus)"
                        }
                        return@withTimeoutOrNull """{"ok":false,"error":"$msg"}"""
                    }

                    val client = HealthConnectClient.getOrCreate(activity)

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

                } catch (e: SecurityException) {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        activity.requestHealthConnectPermissions()
                    }
                    """{"ok":false,"error":"PERMISSION_REQUESTED","retry":true}"""

                } catch (e: Exception) {
                    val msg = (e.message ?: "Errore sconosciuto").take(200).replace("\"", "'")
                    """{"ok":false,"error":"$msg"}"""
                }
            } ?: """{"ok":false,"error":"Timeout: Health Connect non risponde (12s)"}"""

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
