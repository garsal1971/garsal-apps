package com.garsal.smartblocker

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class SupabaseApi(private val ctx: Context) {

    private val base    = "https://jajlmmdsjlvzgcxiiypk.supabase.co"
    private val anonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImphamxtbWRzamx2emdjeGlpeXBrIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Njk5NTU0NjYsImV4cCI6MjA4NTUzMTQ2Nn0.ikaipwxOvIn43epayQ4mSZQkXtin3aaGEPouafwJFxU"

    data class BlockEntry(
        val id:       String,
        val title:    String,
        val fireAt:   String,   // ISO UTC
        val status:   String,
        val myToken:  Boolean   // true se device_token corrisponde
    )

    data class QueueResult(
        val entries:  List<BlockEntry>,   // tutte le righe smart_block visibili
        val dueIds:   List<String>,       // id con status=pending e fire_at<=now e myToken
        val httpCode: Int,
        val errorMsg: String? = null
    )

    /**
     * Legge TUTTE le righe smart_block visibili all'anon (qualsiasi status/fire_at).
     * Separa quelle pronte per il blocco (dueIds).
     */
    fun queryQueue(): QueueResult {
        val deviceToken = Prefs.getDeviceToken(ctx)
        return try {
            val nowIso = isoNow()
            val url = "$base/rest/v1/cm_notification_queue" +
                "?channel=eq.smart_block&select=id,title,body,fire_at,status,metadata&limit=50"
            val conn = openConn(url, "GET")
            val code = conn.responseCode
            if (code != 200) {
                val err = conn.errorStream?.bufferedReader()?.readText() ?: ""
                conn.disconnect()
                return QueueResult(emptyList(), emptyList(), code, "HTTP $code: $err")
            }
            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            val arr = JSONArray(body)
            val entries = mutableListOf<BlockEntry>()
            val dueIds  = mutableListOf<String>()

            for (i in 0 until arr.length()) {
                val item    = arr.getJSONObject(i)
                val id      = item.getString("id")
                val title   = item.optString("title", "").ifEmpty {
                                  item.optString("body", "Blocco") }
                val fireAt  = item.optString("fire_at", "")
                val status  = item.optString("status", "")
                val token   = item.optJSONObject("metadata")?.optString("device_token") ?: ""
                val myToken = token == deviceToken

                entries.add(BlockEntry(id, title, fireAt, status, myToken))

                if (myToken && status == "pending" && fireAt <= nowIso) {
                    dueIds.add(id)
                }
            }
            Log.d("SupabaseApi", "queryQueue: totale=${entries.size} due=${dueIds.size}")
            QueueResult(entries, dueIds, code)
        } catch (e: Exception) {
            Log.e("SupabaseApi", "queryQueue errore: ${e.message}")
            QueueResult(emptyList(), emptyList(), 0, e.message)
        }
    }

    fun getPendingSmartBlockIds(): List<String> = queryQueue().dueIds

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
