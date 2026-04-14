package com.garsalapps

import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.health.connect.client.HealthConnectClient
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
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Bridge JS ↔ Health Connect.
 *
 * Problema: dopo il primo grant dei permessi, readRecords() blocca il thread
 * su IBinder.transact() che è una chiamata nativa non interrompibile via
 * Thread.interrupt(). Il servizio HC non risponde finché non viene aperto
 * almeno una volta dall'utente.
 *
 * Soluzione: quando il timeout scatta, apriamo automaticamente l'app
 * Connessione Salute così HC si inizializza e la prossima sync funziona.
 */
class HealthConnectBridge(
    private val activity: MainActivity,
    private val webView: WebView
) {
    private val scope    = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val executor = Executors.newCachedThreadPool()

    // Guard anti-loop permessi
    private var permissionsRequested = false
    // Flag: true dopo PERMISSION_REQUESTED, usato per aprire HC su timeout
    private var needsHcWarmup = false

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
        scope.launch(Dispatchers.IO) {
            val json = syncWithHardTimeout(25_000L)
            Log.d("HCBridge", "Invio callback: ${json.take(80)}")
            callback(callbackId, json)
        }
    }

    /**
     * Timeout "duro" via Future.get() — interrompe il thread con Thread.interrupt()
     * quando il timeout scatta. Se HC non risponde e sappiamo che le permissions
     * sono state appena concesse (needsHcWarmup), apriamo HC automaticamente.
     */
    private fun syncWithHardTimeout(timeoutMs: Long): String {
        val future = executor.submit<String> { doSync() }
        return try {
            future.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: java.util.concurrent.TimeoutException) {
            future.cancel(true)
            Log.e("HCBridge", "Timeout hard ${timeoutMs / 1000}s: HC non ha risposto")
            // Se è la prima sync dopo il grant, apriamo HC per inizializzare il servizio
            if (needsHcWarmup) {
                needsHcWarmup = false
                openHealthConnectApp()
                """{"ok":false,"error":"HC_NEEDS_WARMUP"}"""
            } else {
                """{"ok":false,"error":"HC non risponde (${timeoutMs / 1000}s). Apri Connessione Salute manualmente e riprova."}"""
            }
        } catch (e: Exception) {
            val cause = e.cause ?: e
            Log.e("HCBridge", "Errore future: ${cause.javaClass.name}: ${cause.message}")
            val cls = cause.javaClass.simpleName
            val msg = (cause.message ?: "Errore sconosciuto").take(150)
                .replace("\\", "/").replace("\"", "'").replace("\n", " ")
            """{"ok":false,"error":"[$cls] $msg"}"""
        }
    }

    /** Apre l'app Connessione Salute per inizializzare il servizio HC. */
    private fun openHealthConnectApp() {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try {
                val pkg = listOf(
                    "com.android.healthconnect.controller",   // Android 14+ integrato
                    "com.google.android.apps.healthdata"      // Play Store (Android 9-13)
                ).firstOrNull { activity.packageManager.getLaunchIntentForPackage(it) != null }
                val intent = pkg?.let { activity.packageManager.getLaunchIntentForPackage(it) }
                if (intent != null) {
                    Log.d("HCBridge", "Apro HC per warmup: $pkg")
                    activity.startActivity(intent)
                } else {
                    Log.w("HCBridge", "App HC non trovata per warmup")
                }
            } catch (e: Exception) {
                Log.e("HCBridge", "Errore apertura HC: ${e.message}")
            }
        }
    }

    private fun doSync(): String {
        return try {
            val sdkStatus = HealthConnectClient.getSdkStatus(activity)
            Log.d("HCBridge", "SDK status: $sdkStatus")
            if (sdkStatus != HealthConnectClient.SDK_AVAILABLE) {
                val msg = when (sdkStatus) {
                    HealthConnectClient.SDK_UNAVAILABLE -> "Health Connect non installato"
                    HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> "Aggiorna Health Connect dal Play Store"
                    else -> "HC non disponibile (status $sdkStatus)"
                }
                return """{"ok":false,"error":"$msg"}"""
            }

            Log.d("HCBridge", "Creazione client HC...")
            val client = HealthConnectClient.getOrCreate(activity.applicationContext)

            val end   = Instant.now().plus(25, ChronoUnit.HOURS)
            val start = Instant.now().minus(90, ChronoUnit.DAYS)
            Log.d("HCBridge", "readRecords: avvio (90 gg)...")

            val response = runBlocking {
                client.readRecords(
                    ReadRecordsRequest(
                        recordType = WeightRecord::class,
                        timeRangeFilter = TimeRangeFilter(startTime = start, endTime = end)
                    )
                )
            }

            Log.d("HCBridge", "readRecords: ${response.records.size} record")
            needsHcWarmup = false  // sync riuscita: reset flag
            val points = response.records.joinToString(",") { r ->
                """{"timestamp":${r.time.toEpochMilli()},"weight":${String.format("%.2f", r.weight.inKilograms)}}"""
            }
            """{"ok":true,"points":[$points]}"""

        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            Log.w("HCBridge", "Thread interrotto (timeout hard)")
            """{"ok":false,"error":"Timeout: HC non ha risposto in tempo."}"""

        } catch (e: SecurityException) {
            Log.w("HCBridge", "SecurityException: ${e.message}")
            if (!permissionsRequested) {
                permissionsRequested = true
                needsHcWarmup = true   // la prossima sync potrebbe avere bisogno di warmup
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
    }

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
