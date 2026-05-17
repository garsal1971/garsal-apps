package com.garsal.smartblocker

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.*
import android.widget.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Mostra la schermata di blocco tramite WindowManager (TYPE_APPLICATION_OVERLAY).
 * Richiede solo il permesso SYSTEM_ALERT_WINDOW (overlay), già concesso dall'utente.
 * Non richiede POST_NOTIFICATIONS né SCHEDULE_EXACT_ALARM.
 */
class BlockWindowManager(private val ctx: Context) {

    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var rootView: View? = null
    private var pinBuffer = ""

    private lateinit var tvClock: TextView
    private lateinit var tvSnoozeInfo: TextView
    private lateinit var tvPinHint: TextView
    private lateinit var tvPinError: TextView
    private lateinit var btnSnooze: Button
    private val pinDots = arrayOfNulls<TextView>(4)

    private val clockTick = object : Runnable {
        override fun run() {
            if (rootView != null) {
                tvClock.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                handler.postDelayed(this, 1000)
            }
        }
    }

    fun isShowing() = rootView != null

    @Suppress("DEPRECATION")
    fun show() {
        if (rootView != null) return
        if (!Settings.canDrawOverlays(ctx)) return

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD,
            PixelFormat.OPAQUE
        )

        val view = buildView()
        rootView = view
        try {
            wm.addView(view, params)
            handler.post(clockTick)
            refreshUI()
        } catch (e: Exception) {
            AppLogger.log(ctx, "WINDOW", "errore show(): ${e.message}")
            rootView = null
        }
    }

    fun dismiss() {
        handler.removeCallbacks(clockTick)
        rootView?.let {
            try { wm.removeView(it) } catch (_: Exception) {}
            rootView = null
        }
        pinBuffer = ""
    }

    private fun refreshUI() {
        val state = Prefs.getState(ctx)
        val count = Prefs.getSnoozeCount(ctx)
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
        ctx.startService(Intent(ctx, BlockerService::class.java).apply {
            action = BlockerService.ACTION_SNOOZE
        })
        dismiss()
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
            val entityIds = Prefs.getBlockEntityIds(ctx)
            ctx.startService(Intent(ctx, BlockerService::class.java).apply {
                action = BlockerService.ACTION_UNBLOCK
            })
            if (entityIds.isNotEmpty()) {
                Thread {
                    val api = SupabaseApi(ctx)
                    entityIds.forEach { api.completeTask(it) }
                    api.triggerFillQueue()
                }.start()
            }
            dismiss()
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

    private fun buildView(): View {
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#1A1A2E"))
            layoutParams = ViewGroup.LayoutParams(MP, MP)
            setPadding(48, 80, 48, 80)
        }

        fun lp(w: Int = WC, h: Int = WC, top: Int = 0) =
            LinearLayout.LayoutParams(w, h).apply { topMargin = top }

        root.addView(TextView(ctx).apply {
            text = "🔒"; textSize = 56f; gravity = Gravity.CENTER
        }, lp(MP))

        tvClock = TextView(ctx).apply {
            textSize = 64f; setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        root.addView(tvClock, lp(MP, top = 16))

        root.addView(TextView(ctx).apply {
            text = "Telefono bloccato"; textSize = 22f
            setTextColor(Color.parseColor("#A78BFA"))
            gravity = Gravity.CENTER; letterSpacing = 0.05f
        }, lp(MP, top = 8))

        val blockTitle = Prefs.getBlockTitle(ctx)
        if (blockTitle.isNotBlank()) {
            root.addView(TextView(ctx).apply {
                text = blockTitle
                textSize = 15f
                setTextColor(Color.parseColor("#CBD5E1"))
                gravity = Gravity.CENTER
                setPadding(24, 0, 24, 0)
            }, lp(MP, top = 6))
        }

        tvSnoozeInfo = TextView(ctx).apply {
            textSize = 14f; setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
        }
        root.addView(tvSnoozeInfo, lp(MP, top = 24))

        btnSnooze = Button(ctx).apply {
            text = "Rinvia ${Config.SNOOZE_DURATION_MS / 60000} min"
            textSize = 16f; setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#7C3AED"))
            setPadding(48, 24, 48, 24)
            setOnClickListener { onSnooze() }
        }
        root.addView(btnSnooze, lp(MP, top = 16))

        root.addView(View(ctx).apply {
            setBackgroundColor(Color.parseColor("#334155"))
        }, LinearLayout.LayoutParams(MP, 1).apply { topMargin = 32 })

        tvPinHint = TextView(ctx).apply {
            textSize = 13f; setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
        }
        root.addView(tvPinHint, lp(MP, top = 24))

        val dotsRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
        }
        for (i in 0..3) {
            val dot = TextView(ctx).apply {
                text = "○"; textSize = 28f
                setTextColor(Color.parseColor("#A78BFA"))
                setPadding(16, 0, 16, 0)
            }
            pinDots[i] = dot; dotsRow.addView(dot)
        }
        root.addView(dotsRow, lp(MP, top = 16))

        tvPinError = TextView(ctx).apply {
            textSize = 13f; setTextColor(Color.parseColor("#F87171"))
            gravity = Gravity.CENTER
        }
        root.addView(tvPinError, lp(MP, top = 8))

        val keys = listOf("1","2","3","4","5","6","7","8","9","C","0","⌫")
        val grid = GridLayout(ctx).apply { columnCount = 3; rowCount = 4 }
        keys.forEach { k ->
            val btn = Button(ctx).apply {
                text = k; textSize = 20f
                setTextColor(if (k == "C") Color.parseColor("#F87171") else Color.WHITE)
                setBackgroundColor(Color.parseColor("#0F172A"))
                setPadding(0, 0, 0, 0)
                setOnClickListener { onDigit(k) }
            }
            grid.addView(btn, GridLayout.LayoutParams().apply {
                width = 0; height = WC
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(4, 4, 4, 4)
            })
        }
        root.addView(grid, lp(MP, top = 16))

        return root
    }

    companion object {
        private const val MP = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WC = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
