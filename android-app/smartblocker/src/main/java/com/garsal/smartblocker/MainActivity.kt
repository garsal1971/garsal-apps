package com.garsal.smartblocker

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
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
    private lateinit var etDeviceToken: EditText
    private lateinit var tvCurrentToken: TextView
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
            text = "v1.0.0 · PIN: ${Config.PIN} · Schedule: 08:00 e 22:00"
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

        // Info schedule
        root.addView(TextView(this).apply {
            text = "Schedule attivi"
            textSize = 18f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }, lp(32))

        val schedInfo = Config.SCHEDULES.joinToString("\n") { "• Ogni giorno alle %02d:%02d".format(it.hour, it.minute) }
        root.addView(TextView(this).apply {
            text = "$schedInfo\n\nRinvii: ${Config.MAX_SNOOZES}  ·  Durata rinvio: ${Config.SNOOZE_DURATION_MS / 60000} min"
            textSize = 15f
            setTextColor(0xFF374151.toInt())
        }, lp(8))

        // Sezione Integrazione Tasks
        root.addView(TextView(this).apply {
            text = "Integrazione Tasks"
            textSize = 18f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }, lp(32))

        root.addView(TextView(this).apply {
            text = "Incolla il Device Token da tasks.html → Impostazioni → Smart Block"
            textSize = 13f
            setTextColor(0xFF6B7280.toInt())
        }, lp(8))

        tvCurrentToken = TextView(this).apply {
            textSize = 12f
            setTextColor(0xFF888888.toInt())
        }
        root.addView(tvCurrentToken, lp(8))

        etDeviceToken = EditText(this).apply {
            hint = "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
            inputType = InputType.TYPE_CLASS_TEXT
            textSize = 13f
            setPadding(16, 12, 16, 12)
            setBackgroundColor(0xFFF9FAFB.toInt())
        }
        root.addView(etDeviceToken, lp(8))

        root.addView(Button(this).apply {
            text = "💾 Salva token"
            setBackgroundColor(0xFF6C5CE7.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setOnClickListener {
                val token = etDeviceToken.text.toString().trim()
                if (token.isNotEmpty()) {
                    Prefs.setDeviceToken(this@MainActivity, token)
                    etDeviceToken.setText("")
                    updateTokenDisplay()
                    Toast.makeText(this@MainActivity, "Token salvato", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "Inserisci un token valido", Toast.LENGTH_SHORT).show()
                }
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
        if (token.isEmpty()) {
            tvQueueStatus.text = "⚠️ Nessun token configurato"
            tvQueueStatus.setTextColor(0xFFE74C3C.toInt())
            return
        }
        tvQueueStatus.text = "⏳ Interrogo Supabase…"
        tvQueueStatus.setTextColor(0xFF6B7280.toInt())
        Thread {
            try {
                val ids = SupabaseApi(this).getPendingSmartBlockIds()
                runOnUiThread {
                    if (ids.isEmpty()) {
                        tvQueueStatus.text = "✅ Nessun blocco in coda"
                        tvQueueStatus.setTextColor(0xFF00B894.toInt())
                    } else {
                        tvQueueStatus.text = "🔔 ${ids.size} blocco/i in coda:\n${ids.joinToString("\n") { "• $it" }}"
                        tvQueueStatus.setTextColor(0xFFF39C12.toInt())
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    tvQueueStatus.text = "❌ Errore: ${e.message}"
                    tvQueueStatus.setTextColor(0xFFE74C3C.toInt())
                }
            }
        }.start()
    }

    private fun updateTokenDisplay() {
        val token = Prefs.getDeviceToken(this)
        tvCurrentToken.text = if (token.isEmpty()) "Nessun token configurato" else "Token attivo: ${token.take(8)}…"
    }

    private fun updateStatus() {
        val hasOverlay = Settings.canDrawOverlays(this)
        tvStatusOverlay.text = if (hasOverlay) "✅ Overlay: concesso" else "❌ Overlay: mancante"
        tvStatusOverlay.setTextColor(if (hasOverlay) 0xFF00B894.toInt() else 0xFFE74C3C.toInt())

        val accEnabled = isAccessibilityEnabled()
        tvStatusAccessibility.text = if (accEnabled) "✅ Accessibilità: attiva" else "❌ Accessibilità: disabilitata"
        tvStatusAccessibility.setTextColor(if (accEnabled) 0xFF00B894.toInt() else 0xFFE74C3C.toInt())

        updateTokenDisplay()
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
