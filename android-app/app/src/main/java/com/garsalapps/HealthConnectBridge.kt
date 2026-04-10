package com.garsalapps

import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.CancellationException
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
    // Guard anti-loop: apre la schermata permessi solo una volta per sessione
    private var permissionsRequested = false

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
            // Timeout 30s — HC su cold start può impiegare > 12s
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

                } catch (e: CancellationException) {
                    // Re-throw: permette a withTimeoutOrNull di funzionare correttamente
                    throw e

                } catch (e: SecurityException) {
                    Log.w("HCBridge", "SecurityException: ${e.message}")
                    if (!permissionsRequested) {
                        permissionsRequested = true
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            activity.requestHealthConnectPermissions()
                        }
                        """{"ok":false,"error":"PERMISSION_REQUESTED","retry":true}"""
                    } else {
                        permissionsRequested = false
                        """{"ok":false,"error":"Permessi HC non attivi. Vai in Impostazioni → Connessione Salute → GarsalApps e abilita la lettura del Peso."}"""
                    }

                } catch (e: Exception) {
                    Log.e("HCBridge", "Errore: ${e.javaClass.name}: ${e.message}", e)
                    val cls = e.javaClass.simpleName
                    val msg = (e.message ?: "Errore sconosciuto").take(150)
                        .replace("\\", "/").replace("\"", "'").replace("\n", " ")
                    """{"ok":false,"error":"[$cls] $msg"}"""
                }
            } ?: run {
                Log.e("HCBridge", "Timeout 30s scaduto")
                """{"ok":false,"error":"Timeout 30s: HC non risponde. Apri Health Connect una volta e riprova."}"""
            }

            Log.d("HCBridge", "Invio callback: ${json.take(80)}")
            callback(callbackId, json)
        }
    }

    /**
     * Callback via Base64 per evitare problemi di escaping JS con caratteri speciali nel JSON.
     * atob() in JavaScript decodifica la stringa Base64 → JSON.parse() crea l'oggetto.
     */
    private fun callback(callbackId: String, json: String) {
        val b64 = Base64.encodeToString(json.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        webView.post {
            webView.evaluateJavascript(
                "try{var _d=JSON.parse(atob('$b64'));if(window.__hcCallback_$callbackId)window.__hcCallback_$callbackId(_d);}catch(_e){console.error('HC callback error',_e);}",
                null
            )
        }
    }
}
