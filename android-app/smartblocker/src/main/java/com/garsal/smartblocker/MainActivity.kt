package com.garsal.smartblocker

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildLayout())
        startBlockerService()
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
            text = "v1.1.4 · PIN: ${Config.PIN}"
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

        // Sezione Device Token
        root.addView(TextView(this).apply {
            text = "Device Token"
            textSize = 18f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }, lp(32))

        root.addView(TextView(this).apply {
            text = "Copia questo token e incollalo in tasks.html → Impostazioni → 🔐 Smart Block"
            textSize = 13f
            setTextColor(0xFF6B7280.toInt())
        }, lp(8))

        // Box token
        tvDeviceToken = TextView(this).apply {
            textSize = 12f
            setTextColor(0xFF6C5CE7.toInt())
            setPadding(20, 20, 20, 20)
            setBackgroundColor(0xFFF3F0FF.toInt())
            typeface = android.graphics.Typeface.MONOSPACE
        }
        root.addView(tvDeviceToken, lp(8))

        root.addView(Button(this).apply {
            text = "📋 Copia token"
            setBackgroundColor(0xFF6C5CE7.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setOnClickListener {
                val token = Prefs.getDeviceToken(this@MainActivity)
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("device_token", token))
                Toast.makeText(this@MainActivity, "✅ Token copiato negli appunti", Toast.LENGTH_SHORT).show()
            }
        }, lp(8))

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
                startActivity(Intent(this@MainActivity, BlockOverlayActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
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
                    result.totalAny == 0 -> {
                        tvQueueStatus.text = "❌ RLS blocca ancora: 0 righe visibili\nEsegui il DDL in Supabase SQL Editor e riprova"
                        tvQueueStatus.setTextColor(0xFFE74C3C.toInt())
                    }
                    result.totalRows == 0 && result.matchingIds.isEmpty() -> {
                        tvQueueStatus.text = "✅ RLS ok — ${result.totalAny} righe visibili\n⏳ Nessun blocco pending/scaduto ora\nSalva un task con Smart Block e riprova"
                        tvQueueStatus.setTextColor(0xFF00B894.toInt())
                    }
                    result.totalRows > 0 && result.matchingIds.isEmpty() -> {
                        tvQueueStatus.text = "⚠️ ${result.totalRows} righe pending ma nessuna\ncorrisponde al token ${token.take(8)}…\nIncolla questo token in tasks.html"
                        tvQueueStatus.setTextColor(0xFFF39C12.toInt())
                    }
                    else -> {
                        // Trovati blocchi — invia comando al servizio per triggerarli
                        tvQueueStatus.text = "🔔 ${result.matchingIds.size} blocco/i trovati — avvio blocco…"
                        tvQueueStatus.setTextColor(0xFFF39C12.toInt())
                        startService(Intent(this@MainActivity, BlockerService::class.java).apply {
                            action = BlockerService.ACTION_CHECK_NOW
                        })
                    }
                }
            }
        }.start()
    }

    private fun updateStatus() {
        val hasOverlay = Settings.canDrawOverlays(this)
        tvStatusOverlay.text = if (hasOverlay) "✅ Overlay: concesso" else "❌ Overlay: mancante"
        tvStatusOverlay.setTextColor(if (hasOverlay) 0xFF00B894.toInt() else 0xFFE74C3C.toInt())

        val accEnabled = isAccessibilityEnabled()
        tvStatusAccessibility.text = if (accEnabled) "✅ Accessibilità: attiva" else "❌ Accessibilità: disabilitata"
        tvStatusAccessibility.setTextColor(if (accEnabled) 0xFF00B894.toInt() else 0xFFE74C3C.toInt())

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
