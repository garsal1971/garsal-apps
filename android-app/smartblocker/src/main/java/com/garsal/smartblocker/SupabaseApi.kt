package com.garsal.smartblocker

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.time.OffsetDateTime
import java.util.*

class SupabaseApi(private val ctx: Context) {

    private val base    = "https://jajlmmdsjlvzgcxiiypk.supabase.co"
    private val anonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImphamxtbWRzamx2emdjeGlpeXBrIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Njk5NTU0NjYsImV4cCI6MjA4NTUzMTQ2Nn0.ikaipwxOvIn43epayQ4mSZQkXtin3aaGEPouafwJFxU"

    data class BlockEntry(
        val id:       String,
        val entityId: String,   // task UUID (per completamento)
        val title:    String,
        val fireAt:   String,   // ISO UTC
        val status:   String,
        val myToken:  Boolean   // true se device_token corrisponde
    )

    data class QueueResult(
        val entries:      List<BlockEntry>,
        val dueIds:       List<String>,
        val nextFireAtMs: Long?,          // epoch ms del prossimo blocco futuro (myToken, pending)
        val httpCode:     Int,
        val errorMsg:     String? = null
    )

    /**
     * Legge TUTTE le righe smart_block visibili all'anon (qualsiasi status/fire_at).
     * Separa quelle pronte per il blocco (dueIds).
     */
    fun queryQueue(): QueueResult {
        val deviceToken = Prefs.getDeviceToken(ctx)
        return try {
            val nowMs = System.currentTimeMillis()
            val url = "$base/rest/v1/cm_notification_queue" +
                "?channel=eq.smart_block&select=id,entity_id,title,body,fire_at,status,metadata&limit=50"
            val conn = openConn(url, "GET")
            val code = conn.responseCode
            if (code != 200) {
                val err = conn.errorStream?.bufferedReader()?.readText() ?: ""
                conn.disconnect()
                return QueueResult(emptyList(), emptyList(), null, code, "HTTP $code: $err")
            }
            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            val arr = JSONArray(body)
            val entries     = mutableListOf<BlockEntry>()
            val dueIds      = mutableListOf<String>()
            var nextFireAtMs: Long? = null

            for (i in 0 until arr.length()) {
                val item     = arr.getJSONObject(i)
                val id       = item.getString("id")
                val entityId = item.optString("entity_id", "")
                val title    = item.optString("title", "").ifEmpty { item.optString("body", "Blocco") }
                val fireAt   = item.optString("fire_at", "")
                val status   = item.optString("status", "")
                val token    = item.optJSONObject("metadata")?.optString("device_token") ?: ""
                val myToken  = token == deviceToken

                entries.add(BlockEntry(id, entityId, title, fireAt, status, myToken))

                val fireAtMs = parseIsoMs(fireAt)
                if (myToken && status == "pending" && fireAtMs > 0L) {
                    if (fireAtMs <= nowMs) {
                        dueIds.add(id)
                    } else if (nextFireAtMs == null || fireAtMs < nextFireAtMs!!) {
                        nextFireAtMs = fireAtMs   // traccia il prossimo blocco futuro
                    }
                }
            }
            Log.d("SupabaseApi", "queryQueue: totale=${entries.size} due=${dueIds.size} nextFireAt=${nextFireAtMs?.let { java.util.Date(it) } ?: "none"}")
            QueueResult(entries, dueIds, nextFireAtMs, code)
        } catch (e: Exception) {
            Log.e("SupabaseApi", "queryQueue errore: ${e.message}")
            QueueResult(emptyList(), emptyList(), null, 0, e.message)
        }
    }

    /** Parsifica un ISO 8601 con qualsiasi offset ("+02:00", "Z", "+00:00") → epoch ms UTC. */
    private fun parseIsoMs(iso: String): Long {
        return try {
            OffsetDateTime.parse(iso.replace(Regex("\\.\\d+"), "")).toInstant().toEpochMilli()
        } catch (e: Exception) {
            Log.w("SupabaseApi", "parseIsoMs fallback per: $iso")
            -1L
        }
    }

    fun getPendingSmartBlockIds(): List<String> = queryQueue().dueIds

    companion object {
        const val ALARM_REQUEST_CODE = 42
    }

    /**
     * Legge il device token da Supabase (RPC get_smart_block_token, anon key)
     * e lo cache in Prefs. Chiamata all'avvio se Prefs è vuoto.
     * Il token è generato una sola volta tramite SQL in comandi.html.
     */
    fun fetchAndCacheDeviceToken(): String {
        return try {
            val conn = openConn("$base/rest/v1/rpc/get_smart_block_token", "POST")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.outputStream.write("{}".toByteArray())
            val code = conn.responseCode
            if (code == 200) {
                val raw = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                val token = raw.trim().removeSurrounding("\"")
                if (token.isNotBlank()) {
                    Prefs.setDeviceToken(ctx, token)
                    Log.d("SupabaseApi", "fetchAndCacheDeviceToken: ${token.take(8)}…")
                    token
                } else ""
            } else {
                conn.disconnect()
                Log.w("SupabaseApi", "fetchAndCacheDeviceToken: HTTP $code")
                ""
            }
        } catch (e: Exception) {
            Log.e("SupabaseApi", "fetchAndCacheDeviceToken errore: ${e.message}")
            ""
        }
    }

    /**
     * Marca un item della coda come 'sent'.
     */
    fun markSent(id: String) {
        try {
            val urlStr = "$base/rest/v1/cm_notification_queue?id=eq.$id"
            val conn = openConn(urlStr, "PATCH")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.outputStream.write("{\"status\":\"sent\"}".toByteArray())
            val code = conn.responseCode
            conn.disconnect()
            Log.d("SupabaseApi", "markSent $id → HTTP $code")
        } catch (e: Exception) {
            Log.e("SupabaseApi", "markSent errore: ${e.message}")
        }
    }

    /**
     * Chiama la RPC task_complete per marcare il task come completato.
     * Da chiamare su un thread in background dopo che l'utente sblocca con PIN.
     */
    fun completeTask(entityId: String) {
        if (entityId.isBlank()) return
        try {
            val today = romeDateStr()
            val deviceToken = Prefs.getDeviceToken(ctx)
            val urlStr = "$base/rest/v1/rpc/smart_block_complete_task"
            val conn = openConn(urlStr, "POST")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            val body = "{\"p_device_token\":\"$deviceToken\",\"p_task_id\":\"$entityId\",\"p_today\":\"$today\"}"
            conn.outputStream.write(body.toByteArray())
            val code = conn.responseCode
            val resp = conn.inputStream?.bufferedReader()?.readText() ?: ""
            conn.disconnect()
            AppLogger.log(ctx, "SUPABASE", "completeTask $entityId → HTTP $code $resp")
        } catch (e: Exception) {
            AppLogger.log(ctx, "SUPABASE", "completeTask errore: ${e.message}")
        }
    }

    private fun romeDateStr(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone("Europe/Rome")
        return sdf.format(Date())
    }

    private fun openConn(urlStr: String, method: String): HttpURLConnection {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.setRequestProperty("apikey", anonKey)
        conn.setRequestProperty("Authorization", "Bearer $anonKey")
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        return conn
    }

    private fun isoNow(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }
}
