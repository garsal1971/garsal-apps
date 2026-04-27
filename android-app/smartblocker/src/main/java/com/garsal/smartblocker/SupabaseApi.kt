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

    data class QueueResult(
        val matchingIds: List<String>,
        val totalRows: Int,      // pending+scaduti visibili all'anon key
        val totalAny: Int,       // qualsiasi stato/fire_at — per verificare RLS
        val httpCode: Int,
        val errorMsg: String? = null
    )

    /**
     * Legge cm_notification_queue con due query:
     * 1. Filtro completo (pending, fire_at<=now) + match device_token
     * 2. Solo channel=smart_block senza altri filtri → conta righe visibili all'anon (test RLS)
     */
    fun queryQueue(): QueueResult {
        val deviceToken = Prefs.getDeviceToken(ctx)

        return try {
            // Query 1 — righe pronte per il blocco
            val nowIso = isoNow()
            val urlFull = "$base/rest/v1/cm_notification_queue" +
                "?channel=eq.smart_block&status=eq.pending&fire_at=lte.$nowIso" +
                "&select=id,fire_at,metadata"
            val conn1 = openConn(urlFull, "GET")
            val code1 = conn1.responseCode
            if (code1 != 200) {
                val err = conn1.errorStream?.bufferedReader()?.readText() ?: ""
                conn1.disconnect()
                return QueueResult(emptyList(), 0, 0, code1, "HTTP $code1: $err")
            }
            val body1 = conn1.inputStream.bufferedReader().readText()
            conn1.disconnect()

            val arr = JSONArray(body1)
            val totalRows = arr.length()
            val ids = mutableListOf<String>()
            for (i in 0 until totalRows) {
                val item = arr.getJSONObject(i)
                val token = item.optJSONObject("metadata")?.optString("device_token") ?: ""
                if (token == deviceToken) ids.add(item.getString("id"))
            }

            // Query 2 — test RLS: tutte le righe smart_block visibili all'anon (limit 50)
            val urlAny = "$base/rest/v1/cm_notification_queue" +
                "?channel=eq.smart_block&select=id&limit=50"
            val conn2 = openConn(urlAny, "GET")
            val totalAny = if (conn2.responseCode == 200) {
                val b = conn2.inputStream.bufferedReader().readText()
                conn2.disconnect()
                JSONArray(b).length()
            } else { conn2.disconnect(); -1 }

            Log.d("SupabaseApi", "queryQueue: pending=$totalRows match=${ids.size} totalAny=$totalAny")
            QueueResult(ids, totalRows, totalAny, code1)
        } catch (e: Exception) {
            Log.e("SupabaseApi", "queryQueue errore: ${e.message}")
            QueueResult(emptyList(), 0, 0, 0, e.message)
        }
    }

    fun getPendingSmartBlockIds(): List<String> = queryQueue().matchingIds

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
