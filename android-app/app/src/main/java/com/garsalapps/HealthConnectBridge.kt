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
 * Il pattern coroutine con withTimeoutOrNull NON funziona quando readRecords()
 * blocca il thread su una chiamata Binder IPC senza mai cedere il controllo
 * (Health Connect cold-start dopo il primo grant dei permessi).
 *
 * Soluzione: ExecutorService + Future.get(timeout) che usa Thread.interrupt()
 * sotto il cofano — l'unico meccanismo affidabile per interrompere un thread
 * bloccato su IPC.
 */
class HealthConnectBridge(
    private val activity: MainActivity,
    private val webView: WebView
) {
    private val scope    = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val executor = Executors.newCachedThreadPool()

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
        // Lancia su un thread separato dal pool principale così il main thread
        // non viene bloccato; il risultato arriva via callback JS.
        scope.launch(Dispatchers.IO) {
            val json = syncWithHardTimeout(25_000L)
            Log.d("HCBridge", "Invio callback: ${json.take(80)}")
            callback(callbackId, json)
        }
    }

    /**
     * Esegue la sync HC in un thread del pool con un timeout "duro" via
     * Future.get(). Se il thread rimane bloccato oltre [timeoutMs] millisecondi,
     * future.cancel(true) chiama Thread.interrupt() — interrompe anche le
     * chiamate Binder bloccate su alcune versioni Android.
     */
    private fun syncWithHardTimeout(timeoutMs: Long): String {
        val future = executor.submit<String> { doSync() }
        return try {
            future.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: java.util.concurrent.TimeoutException) {
            future.cancel(true)          // Thread.interrupt() sul thread bloccato
            Log.e("HCBridge", "Timeout hard ${timeoutMs / 1000}s: HC non ha risposto")
            """{"ok":false,"error":"HC non risponde (${timeoutMs / 1000}s). Apri Health Connect manualmente una volta e riprova."}"""
        } catch (e: Exception) {
            val cause = e.cause ?: e
            Log.e("HCBridge", "Errore future: ${cause.javaClass.name}: ${cause.message}")
            val cls = cause.javaClass.simpleName
            val msg = (cause.message ?: "Errore sconosciuto").take(150)
                .replace("\\", "/").replace("\"", "'").replace("\n", " ")
            """{"ok":false,"error":"[$cls] $msg"}"""
        }
    }

    /**
     * Logica di sincronizzazione effettiva — eseguita su un thread del pool
     * (contesto non-suspending). Usa runBlocking solo per chiamare readRecords
     * che è una suspend function.
     */
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

            // applicationContext: evita memory leak con Activity context
            Log.d("HCBridge", "Creazione client HC...")
            val client = HealthConnectClient.getOrCreate(activity.applicationContext)

            val end   = Instant.now().plus(25, ChronoUnit.HOURS)   // +25h: copre sfasamenti orologio
            val start = Instant.now().minus(90, ChronoUnit.DAYS)
            Log.d("HCBridge", "readRecords: avvio (90 gg)...")

            // runBlocking qui è intenzionale: stiamo su un thread del pool,
            // non su un thread coroutine, quindi possiamo bloccare in sicurezza.
            val response = runBlocking {
                client.readRecords(
                    ReadRecordsRequest(
                        recordType = WeightRecord::class,
                        timeRangeFilter = TimeRangeFilter(startTime = start, endTime = end)
                    )
                )
            }

            Log.d("HCBridge", "readRecords: ${response.records.size} record")
            val points = response.records.joinToString(",") { r ->
                """{"timestamp":${r.time.toEpochMilli()},"weight":${String.format("%.2f", r.weight.inKilograms)}}"""
            }
            """{"ok":true,"points":[$points]}"""

        } catch (e: InterruptedException) {
            // Thread.interrupt() ricevuto dal Future.cancel(true) — timeout scattato
            Thread.currentThread().interrupt()
            Log.w("HCBridge", "Thread interrotto (timeout hard)")
            """{"ok":false,"error":"Timeout: HC non ha risposto. Apri Health Connect una volta e riprova."}"""

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
    }

    /**
     * Callback via Base64 per evitare problemi di escaping JS con caratteri speciali.
     * atob() in JavaScript decodifica → JSON.parse() crea l'oggetto.
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
