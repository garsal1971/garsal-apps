package com.garsal.smartblocker

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.*
import java.text.SimpleDateFormat
import java.util.*

class BlockOverlayActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private var pinBuffer = ""

    private lateinit var tvClock: TextView
    private lateinit var tvSnoozeInfo: TextView
    private lateinit var tvPinHint: TextView
    private lateinit var tvPinError: TextView
    private lateinit var btnSnooze: Button
    private val pinDots = arrayOfNulls<TextView>(4)

    private val clockTick = object : Runnable {
        override fun run() {
            tvClock.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mostra sopra la lock screen e tieni lo schermo acceso
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_FULLSCREEN or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        setContentView(buildView())
        handler.post(clockTick)
        refreshUI()
    }

    override fun onResume() {
        super.onResume()
        pinBuffer = ""
        updateDots()
        refreshUI()
    }

    override fun onDestroy() {
        handler.removeCallbacks(clockTick)
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Blocco attivo: ignora il tasto back
    }

    // ── UI ──────────────────────────────────────────────────────────────────

    private fun refreshUI() {
        val state = Prefs.getState(this)
        val count = Prefs.getSnoozeCount(this)

        btnSnooze.visibility =
            if (state == Prefs.STATE_TRIGGERED && count < Config.MAX_SNOOZES) View.VISIBLE else View.GONE

        tvSnoozeInfo.text = when {
            count == 0 -> "Puoi rinviare ancora ${Config.MAX_SNOOZES} volt${if (Config.MAX_SNOOZES == 1) "a" else "e"}"
            count < Config.MAX_SNOOZES -> "Rinvii rimasti: ${Config.MAX_SNOOZES - count}"
            else -> ""
        }

        tvPinHint.text = if (state == Prefs.STATE_LOCKED)
            "⚠️ Rinvii esauriti — solo PIN"
        else
            "Oppure sblocca con PIN"
    }

    private fun onSnooze() {
        startService(Intent(this, BlockerService::class.java).apply {
            action = BlockerService.ACTION_SNOOZE
        })
        finish()
    }

    private fun onDigit(d: String) {
        when (d) {
            "C"  -> { pinBuffer = ""; tvPinError.text = "" }
            "⌫" -> if (pinBuffer.isNotEmpty()) pinBuffer = pinBuffer.dropLast(1)
            else -> if (pinBuffer.length < 4) pinBuffer += d
        }
        updateDots()
        if (pinBuffer.length == 4) handler.postDelayed({ checkPin() }, 150)
    }

    private fun checkPin() {
        if (pinBuffer == Config.PIN) {
            val entityIds = Prefs.getBlockEntityIds(this)
            startService(Intent(this, BlockerService::class.java).apply {
                action = BlockerService.ACTION_UNBLOCK
            })
            if (entityIds.isNotEmpty()) {
                Thread {
                    val api = SupabaseApi(this)
                    entityIds.forEach { api.completeTask(it) }
                }.start()
            }
            finish()
        } else {
            tvPinError.text = "PIN errato, riprova"
            pinBuffer = ""
            updateDots()
            handler.postDelayed({ tvPinError.text = "" }, 2500)
        }
    }

    private fun updateDots() {
        pinDots.forEachIndexed { i, tv ->
            tv?.text = if (i < pinBuffer.length) "●" else "○"
        }
    }

    // ── Layout programmatico ─────────────────────────────────────────────────

    private fun buildView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#1A1A2E"))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setPadding(48, 80, 48, 80)
        }

        fun lp(w: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
                h: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
                topMargin: Int = 0) =
            LinearLayout.LayoutParams(w, h).apply { this.topMargin = topMargin }

        // Icona lucchetto
        val tvIcon = TextView(this).apply {
            text = "🔒"; textSize = 56f; gravity = Gravity.CENTER
        }
        root.addView(tvIcon, lp(ViewGroup.LayoutParams.MATCH_PARENT, topMargin = 0))

        // Orologio
        tvClock = TextView(this).apply {
            textSize = 64f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        root.addView(tvClock, lp(ViewGroup.LayoutParams.MATCH_PARENT, topMargin = 16))

        // Titolo blocco
        val tvTitle = TextView(this).apply {
            text = "Telefono bloccato"
            textSize = 22f
            setTextColor(Color.parseColor("#A78BFA"))
            gravity = Gravity.CENTER
            letterSpacing = 0.05f
        }
        root.addView(tvTitle, lp(ViewGroup.LayoutParams.MATCH_PARENT, topMargin = 8))

        // Info rinvii
        tvSnoozeInfo = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
        }
        root.addView(tvSnoozeInfo, lp(ViewGroup.LayoutParams.MATCH_PARENT, topMargin = 24))

        // Pulsante Rinvia
        btnSnooze = Button(this).apply {
            text = "Rinvia 15 min"
            textSize = 16f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#7C3AED"))
            setPadding(48, 24, 48, 24)
            setOnClickListener { onSnooze() }
        }
        root.addView(btnSnooze, lp(ViewGroup.LayoutParams.MATCH_PARENT, topMargin = 16))

        // Divisore
        val divider = View(this).apply { setBackgroundColor(Color.parseColor("#334155")) }
        root.addView(divider, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply { topMargin = 32 })

        // Hint PIN
        tvPinHint = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
        }
        root.addView(tvPinHint, lp(ViewGroup.LayoutParams.MATCH_PARENT, topMargin = 24))

        // Dot PIN
        val dotsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        for (i in 0..3) {
            val dot = TextView(this).apply {
                text = "○"; textSize = 28f
                setTextColor(Color.parseColor("#A78BFA"))
                setPadding(16, 0, 16, 0)
            }
            pinDots[i] = dot
            dotsRow.addView(dot)
        }
        root.addView(dotsRow, lp(ViewGroup.LayoutParams.MATCH_PARENT, topMargin = 16))

        // Errore PIN
        tvPinError = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.parseColor("#F87171"))
            gravity = Gravity.CENTER
        }
        root.addView(tvPinError, lp(ViewGroup.LayoutParams.MATCH_PARENT, topMargin = 8))

        // Tastierino PIN (3×4)
        val keys = listOf("1","2","3","4","5","6","7","8","9","C","0","⌫")
        val grid = GridLayout(this).apply {
            columnCount = 3
            rowCount = 4
        }
        keys.forEach { k ->
            val btn = Button(this).apply {
                text = k
                textSize = 20f
                setTextColor(if (k == "C") Color.parseColor("#F87171") else Color.WHITE)
                setBackgroundColor(Color.parseColor("#0F172A"))
                setPadding(0, 0, 0, 0)
                setOnClickListener { onDigit(k) }
            }
            val p = GridLayout.LayoutParams().apply {
                width = 0
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(4, 4, 4, 4)
            }
            grid.addView(btn, p)
        }
        root.addView(grid, lp(ViewGroup.LayoutParams.MATCH_PARENT, topMargin = 16))

        return root
    }
}
