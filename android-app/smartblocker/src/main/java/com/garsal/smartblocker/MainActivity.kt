package com.garsal.smartblocker

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildLayout())
        startBlockerService()
        requestNotificationPermissionIfNeeded()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun startBlockerService() {
        ContextCompat.startForegroundService(this, Intent(this, BlockerService::class.java))
    }

    // ── Layout ───────────────────────────────────────────────────────────────

    private lateinit var tvStatusOverlay: TextView
    private lateinit var tvStatusAccessibility: TextView
    private lateinit var tvStatusAlarm: TextView
    private lateinit var tvStatusFullScreen: TextView
    private lateinit var tvBlockState: TextView
    private lateinit var tvDeviceToken: TextView
    private lateinit var tvQueueStatus: TextView

    private fun buildLayout(): ScrollView {
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 80, 48, 48)
        }
        scroll.addView(root)

        fun lp(topMargin: Int = 16) = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { this.topMargin = topMargin }

        // Titolo
        root.addView(TextView(this).apply {
            text = "🔐 Smart Blocker"
            textSize = 28f
            setTextColor(0xFF6C5CE7.toInt())
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }, lp(0))

        root.addView(TextView(this).apply {
            text = "v1.2.2 · PIN: ${Config.PIN}"
            textSize = 12f
            setTextColor(0xFF888888.toInt())
        }, lp(4))

        // Stato blocco
        tvBlockState = TextView(this).apply {
            textSize = 16f
            setPadding(24, 24, 24, 24)
            setBackgroundColor(0xFF1F2937.toInt())
            setTextColor(0xFFFFFFFF.toInt())
        }
        root.addView(tvBlockState, lp(32))

        // Sezione permessi
        root.addView(TextView(this).apply {
            text = "Permessi necessari"
            textSize = 18f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }, lp(32))

        // Overlay
        tvStatusOverlay = TextView(this).apply { textSize = 14f }
        root.addView(tvStatusOverlay, lp(8))
        root.addView(Button(this).apply {
            text = "Concedi permesso Overlay"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")))
            }
        }, lp(4))

        // Accessibilità
        tvStatusAccessibility = TextView(this).apply { textSize = 14f }
        root.addView(tvStatusAccessibility, lp(16))
        root.addView(Button(this).apply {
            text = "Abilita Accessibilità"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }, lp(4))

        // Alarm esatto
        tvStatusAlarm = TextView(this).apply { textSize = 14f }
        root.addView(tvStatusAlarm, lp(16))
        root.addView(Button(this).apply {
            text = "Concedi Alarm Esatto"
            setOnClickListener {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                    startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:$packageName")))
            }
        }, lp(4))

        // Full-screen intent (necessario per aprire blocco da background/sleep)
        tvStatusFullScreen = TextView(this).apply { textSize = 14f }
        root.addView(tvStatusFullScreen, lp(16))
        root.addView(Button(this).apply {
            text = "Concedi Notifiche Full-Screen"
            setOnClickListener {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                    startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENTS,
                        Uri.parse("package:$packageName")))
                else
                    startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                    })
            }
        }, lp(4))

        // Impostazioni limitate (Android 13+ sideload)
        root.addView(TextView(this).apply {
            text = "⚠️ Se l'accessibilità è bloccata, abilita prima le impostazioni limitate per questa app:"
            textSize = 12f
            setTextColor(0xFF6B7280.toInt())
        }, lp(6))
        root.addView(Button(this).apply {
            text = "🔓 Impostazioni limitate"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName")))
            }
        }, lp(4))

        // Configurazione blocco
        root.addView(TextView(this).apply {
            text = "Configurazione blocco"
            textSize = 18f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }, lp(32))

        root.addView(TextView(this).apply {
            text = "Rinvii: ${Config.MAX_SNOOZES}  ·  Durata rinvio: ${Config.SNOOZE_DURATION_MS / 60000} min\nI blocchi arrivano da tasks.html via Supabase"
            textSize = 15f
            setTextColor(0xFF374151.toInt())
        }, lp(8))

        // Toggle avvio automatico al boot
        val rowBoot = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val switchBoot = Switch(this).apply {
            isChecked = Prefs.getAutoStart(this@MainActivity)
            setOnCheckedChangeListener { _, checked ->
                Prefs.setAutoStart(this@MainActivity, checked)
            }
        }
        rowBoot.addView(TextView(this).apply {
            text = "Avvia in background all'accensione"
            textSize = 15f
            setTextColor(0xFF374151.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        rowBoot.addView(switchBoot)
        root.addView(rowBoot, lp(12))

        // Sezione Device Token (sola lettura — impostato da Supabase)
        root.addView(TextView(this).apply {
            text = "Device Token"
            textSize = 18f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }, lp(32))

        root.addView(TextView(this).apply {
            text = "Token impostato da Supabase — nessuna configurazione richiesta"
            textSize = 13f
            setTextColor(0xFF6B7280.toInt())
        }, lp(8))

        tvDeviceToken = TextView(this).apply {
            textSize = 12f
            setTextColor(0xFF6C5CE7.toInt())
            setPadding(20, 20, 20, 20)
            setBackgroundColor(0xFFF3F0FF.toInt())
            typeface = android.graphics.Typeface.MONOSPACE
        }
        root.addView(tvDeviceToken, lp(8))

        // Sezione Queue Supabase
        root.addView(TextView(this).apply {
            text = "Coda Supabase"
            textSize = 18f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }, lp(32))

        tvQueueStatus = TextView(this).apply {
            text = "—"
            textSize = 13f
            setTextColor(0xFF374151.toInt())
            setPadding(0, 4, 0, 4)
        }
        root.addView(tvQueueStatus, lp(8))

        root.addView(Button(this).apply {
            text = "🔄 Controlla ora"
            setBackgroundColor(0xFF374151.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setOnClickListener { checkQueueNow() }
        }, lp(8))

        // Pulsante test blocco
        root.addView(Button(this).apply {
            text = "🧪 Testa blocco ora"
            setBackgroundColor(0xFF6C5CE7.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setOnClickListener {
                Prefs.setState(this@MainActivity, Prefs.STATE_TRIGGERED)
                Prefs.setSnoozeCount(this@MainActivity, 0)
                startService(Intent(this@MainActivity, BlockerService::class.java).apply {
                    action = BlockerService.ACTION_SHOW_OVERLAY
                })
            }
        }, lp(32))

        return scroll
    }

    private fun checkQueueNow() {
        val token = Prefs.getDeviceToken(this)
        tvQueueStatus.text = "⏳ Interrogo Supabase…"
        tvQueueStatus.setTextColor(0xFF6B7280.toInt())
        Thread {
            val result = SupabaseApi(this).queryQueue()
            runOnUiThread {
                when {
                    result.errorMsg != null -> {
                        tvQueueStatus.text = "❌ ${result.errorMsg}"
                        tvQueueStatus.setTextColor(0xFFE74C3C.toInt())
                    }
                    result.httpCode != 200 -> {
                        tvQueueStatus.text = "❌ HTTP ${result.httpCode} — controlla RLS Supabase"
                        tvQueueStatus.setTextColor(0xFFE74C3C.toInt())
                    }
                    result.entries.isEmpty() -> {
                        tvQueueStatus.text = "❌ 0 righe visibili — esegui il DDL RLS in Supabase"
                        tvQueueStatus.setTextColor(0xFFE74C3C.toInt())
                    }
                    else -> {
                        // Mostra lista blocchi
                        val sb = StringBuilder()
                        for (e in result.entries) {
                            val time = formatFireAt(e.fireAt)
                            val stato = when {
                                !e.myToken          -> "⚪ altro dispositivo"
                                e.status == "sent"  -> "✅ inviato"
                                e.status == "pending" && parseIsoMs(e.fireAt) <= System.currentTimeMillis() -> "🔔 PRONTO"
                                e.status == "pending" -> "⏳ in attesa"
                                else                -> e.status
                            }
                            sb.append("$time — ${e.title.take(30)}\n$stato\n\n")
                        }
                        tvQueueStatus.text = sb.toString().trimEnd()
                        tvQueueStatus.setTextColor(0xFF374151.toInt())

                        // Se ci sono blocchi pronti → triggera il servizio
                        if (result.dueIds.isNotEmpty()) {
                            startService(Intent(this@MainActivity, BlockerService::class.java).apply {
                                action = BlockerService.ACTION_CHECK_NOW
                            })
                        }
                    }
                }
            }
        }.start()
    }

    /** Parsifica ISO 8601 con offset ("+02:00", "Z", "+00:00") → epoch ms UTC. */
    private fun parseIsoMs(iso: String): Long {
        return try {
            OffsetDateTime.parse(iso.replace(Regex("\\.\\d+"), "")).toInstant().toEpochMilli()
        } catch (e: Exception) { Long.MAX_VALUE }
    }

    /** Converte ISO 8601 (con qualsiasi offset) in "dd/MM HH:mm" ora locale del dispositivo. */
    private fun formatFireAt(iso: String): String {
        return try {
            val odt = OffsetDateTime.parse(iso.replace(Regex("\\.\\d+"), ""))
            val local = odt.atZoneSameInstant(ZoneId.systemDefault())
            DateTimeFormatter.ofPattern("dd/MM HH:mm").format(local)
        } catch (e: Exception) { iso.take(16) }
    }

    private fun updateStatus() {
        val hasOverlay = Settings.canDrawOverlays(this)
        tvStatusOverlay.text = if (hasOverlay) "✅ Overlay: concesso" else "❌ Overlay: mancante"
        tvStatusOverlay.setTextColor(if (hasOverlay) 0xFF00B894.toInt() else 0xFFE74C3C.toInt())

        val accEnabled = isAccessibilityEnabled()
        tvStatusAccessibility.text = if (accEnabled) "✅ Accessibilità: attiva" else "❌ Accessibilità: disabilitata"
        tvStatusAccessibility.setTextColor(if (accEnabled) 0xFF00B894.toInt() else 0xFFE74C3C.toInt())

        val alarmOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
        else true
        tvStatusAlarm.text = if (alarmOk) "✅ Alarm esatto: concesso" else "❌ Alarm esatto: mancante"
        tvStatusAlarm.setTextColor(if (alarmOk) 0xFF00B894.toInt() else 0xFFE74C3C.toInt())

        val nm = getSystemService(NotificationManager::class.java)
        val fsOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            nm.canUseFullScreenIntent()
        else nm.areNotificationsEnabled()
        tvStatusFullScreen.text = if (fsOk) "✅ Full-screen intent: concesso" else "❌ Full-screen intent: mancante (blocco non visibile da background)"
        tvStatusFullScreen.setTextColor(if (fsOk) 0xFF00B894.toInt() else 0xFFE74C3C.toInt())

        // Mostra il token (generato automaticamente da Prefs se non esiste)
        tvDeviceToken.text = Prefs.getDeviceToken(this)

        val state = Prefs.getState(this)
        val count = Prefs.getSnoozeCount(this)
        tvBlockState.text = when (state) {
            Prefs.STATE_NONE      -> "🟢 Telefono libero"
            Prefs.STATE_TRIGGERED -> "🟡 BLOCCATO — rinvii usati: $count/${Config.MAX_SNOOZES}"
            Prefs.STATE_LOCKED    -> "🔴 BLOCCATO — solo PIN"
            else -> "?"
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val service = "$packageName/${BlockerAccessibilityService::class.java.canonicalName}"
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any { it.equals(service, ignoreCase = true) }
    }
}
