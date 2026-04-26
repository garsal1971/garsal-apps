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

    /**
     * Legge cm_notification_queue: cerca item con channel='smart_block', status='pending',
     * fire_at <= now, e device_token corrispondente nel JSON reminder_presets.
     * Ritorna la lista di ID degli item da triggerare.
     */
    fun getPendingSmartBlockIds(): List<String> {
        val deviceToken = Prefs.getDeviceToken(ctx)
        if (deviceToken.isEmpty()) {
            Log.d("SupabaseApi", "Nessun device_token configurato, skip polling")
            return emptyList()
        }

        return try {
            val nowIso = isoNow()
            // Filtra per canale e stato; il filtro device_token è lato client (JSON JSONB)
            val urlStr = "$base/rest/v1/cm_notification_queue" +
                "?channel=eq.smart_block" +
                "&status=eq.pending" +
                "&fire_at=lte.$nowIso" +
                "&select=id,fire_at,metadata"

            val conn = openConn(urlStr, "GET")
            val code = conn.responseCode
            if (code != 200) {
                Log.w("SupabaseApi", "getPendingSmartBlockIds HTTP $code")
                conn.disconnect()
                return emptyList()
            }

            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            val arr = JSONArray(body)
            val ids = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                // Controlla device_token nel campo metadata (JSONB)
                val metadata = item.optJSONObject("metadata")
                val token = metadata?.optString("device_token") ?: ""
                if (token == deviceToken) {
                    ids.add(item.getString("id"))
                }
            }
            Log.d("SupabaseApi", "Trovati ${ids.size} item Smart Block pending")
            ids
        } catch (e: Exception) {
            Log.e("SupabaseApi", "getPendingSmartBlockIds errore: ${e.message}")
            emptyList()
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
