package com.garsal.smartblocker

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
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

    // ── Categorizzazione interattiva Analisi Costi (Smart Block "cost_analysis") ────────────
    private var isCaPickerView = false
    private var caQueueId = ""
    private var caMetadata: JSONObject? = null          // metadata grezzo (device_token + cost_analysis)
    private var caTransactions = mutableListOf<JSONObject>()
    private var caCategories = listOf<JSONObject>()
    private lateinit var tvCaProgress: TextView
    private lateinit var tvCaTransaction: TextView
    private lateinit var etCaSearch: EditText
    private lateinit var llCaCategories: LinearLayout

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
    private fun buildOverlayParams() = WindowManager.LayoutParams(
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

    fun show() {
        if (rootView != null) return
        if (!Settings.canDrawOverlays(ctx)) return

        val view = buildView()
        rootView = view
        try {
            wm.addView(view, buildOverlayParams())
            handler.post(clockTick)
            refreshUI()
        } catch (e: Exception) {
            AppLogger.log(ctx, "WINDOW", "errore show(): ${e.message}")
            rootView = null
        }
    }

    /** Passa dal picker categorie alla schermata di blocco standard (PIN/sfida), senza
        sbloccare — usata quando il picker termina ma restano altre entità (task/sfide) da
        gestire. Vedi finishCaPickerAndMaybeUnblock(). */
    private fun switchToStandardView() {
        handler.removeCallbacks(clockTick)
        rootView?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        rootView = null
        isCaPickerView = false
        val view = buildStandardView()
        rootView = view
        try {
            wm.addView(view, buildOverlayParams())
            handler.post(clockTick)
            refreshUI()
        } catch (e: Exception) {
            AppLogger.log(ctx, "WINDOW", "errore switchToStandardView(): ${e.message}")
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
        // Il picker categorie ha i suoi bottoni dedicati (nessun rinvio/PIN standard) e non
        // inizializza btnSnooze/tvSnoozeInfo/tvPinHint — uscire subito evita un crash per
        // lateinit non inizializzato.
        if (isCaPickerView) return
        val state = Prefs.getState(ctx)
        val count = Prefs.getSnoozeCount(ctx)
        btnSnooze.visibility =
            if (state == Prefs.STATE_TRIGGERED && count < Config.MAX_SNOOZES) View.VISIBLE else View.GONE
        tvSnoozeInfo.text = when {
            count == 0 -> "Puoi rinviare ancora ${Config.MAX_SNOOZES} volt${if (Config.MAX_SNOOZES == 1) "a" else "e"}"
            count < Config.MAX_SNOOZES -> "Rinvii rimasti: ${Config.MAX_SNOOZES - count}"
            else -> ""
        }
        if (!Prefs.isChallengeOnlyBlock(ctx) && !Prefs.isInfoOnlyBlock(ctx)) {
            tvPinHint.text = if (state == Prefs.STATE_LOCKED)
                "⚠️ Rinvii esauriti — solo PIN"
            else
                "Oppure sblocca con PIN"
        }
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
            val entities = Prefs.getBlockEntities(ctx)
            ctx.startService(Intent(ctx, BlockerService::class.java).apply {
                action = BlockerService.ACTION_UNBLOCK
            })
            if (entities.isNotEmpty()) {
                Thread {
                    val api = SupabaseApi(ctx)
                    entities.forEach { api.completeEntity(it.app, it.entityId) }
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

    /** Dismiss semplice per blocchi informativi (es. cost_analysis): nessuna RPC, nessun PIN —
        la riga cm_notification_queue è già passata a 'sent' nel momento in cui l'overlay è stato
        mostrato (BlockerService → markSent), quindi non serve completare/confermare nulla lato
        server, si chiude e basta. */
    private fun onInfoDismiss() {
        ctx.startService(Intent(ctx, BlockerService::class.java).apply {
            action = BlockerService.ACTION_UNBLOCK
        })
        dismiss()
    }

    /** Invia la risposta SÌ/NO della sfida Ta Firi? (tenuta premuta) e sblocca. */
    private fun onChallengeResponse(status: String) {
        val entities = Prefs.getBlockEntities(ctx)
        ctx.startService(Intent(ctx, BlockerService::class.java).apply {
            action = BlockerService.ACTION_UNBLOCK
        })
        if (entities.isNotEmpty()) {
            Thread {
                val api = SupabaseApi(ctx)
                entities.forEach { api.completeChallengeCheckin(it.entityId, status) }
                api.triggerFillQueue()
            }.start()
        }
        dismiss()
    }

    /** Legge da Prefs il metadata del blocco cost_analysis corrente e lo scompone in
        caTransactions/caCategories. caTransactions vuoto o caCategories vuoto → niente picker,
        si ricade sul blocco informativo semplice ("Ho capito"). */
    private fun loadCaDataFromPrefs() {
        caQueueId = Prefs.getBlockCaQueueId(ctx)
        caMetadata = null
        caTransactions = mutableListOf()
        caCategories = listOf()
        val raw = Prefs.getBlockCaData(ctx)
        if (raw.isBlank()) return
        try {
            val meta = JSONObject(raw)
            caMetadata = meta
            val ca = meta.optJSONObject("cost_analysis") ?: return
            val txArr = ca.optJSONArray("transactions") ?: JSONArray()
            val txList = mutableListOf<JSONObject>()
            for (i in 0 until txArr.length()) txList.add(txArr.getJSONObject(i))
            caTransactions = txList
            val catArr = ca.optJSONArray("categories") ?: JSONArray()
            val catList = mutableListOf<JSONObject>()
            for (i in 0 until catArr.length()) catList.add(catArr.getJSONObject(i))
            caCategories = catList
        } catch (e: Exception) {
            AppLogger.log(ctx, "CA_CATEGORY", "parse metadata fallito: ${e.message}")
            caMetadata = null
            caTransactions = mutableListOf()
            caCategories = listOf()
        }
    }

    private fun buildCategoryPickerView(): View {
        isCaPickerView = true
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1E3A5F"))
            layoutParams = ViewGroup.LayoutParams(MP, MP)
            setPadding(48, 72, 48, 40)
        }
        fun lp(w: Int = MP, h: Int = WC, top: Int = 0) = LinearLayout.LayoutParams(w, h).apply { topMargin = top }

        root.addView(TextView(ctx).apply {
            text = "🔒"; textSize = 36f; gravity = Gravity.CENTER
        }, lp(top = 0))

        tvClock = TextView(ctx).apply {
            textSize = 36f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        root.addView(tvClock, lp(top = 8))

        root.addView(TextView(ctx).apply {
            text = "Categorizza le transazioni Revolut"
            textSize = 16f; setTextColor(Color.parseColor("#A78BFA"))
            gravity = Gravity.CENTER
        }, lp(top = 8))

        tvCaProgress = TextView(ctx).apply {
            textSize = 13f; setTextColor(Color.parseColor("#94A3B8")); gravity = Gravity.CENTER
        }
        root.addView(tvCaProgress, lp(top = 10))

        tvCaTransaction = TextView(ctx).apply {
            textSize = 17f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(16, 16, 16, 16)
        }
        root.addView(tvCaTransaction, lp(top = 16))

        // Stesso pattern del filtro categoria in cost-analysis.html (renderTxCategorySuggestions):
        // campo di ricerca + lista raggruppata (principale in grassetto, sottocategorie indentate
        // subito sotto), entrambe toccabili, filtrate live per nome man mano che si scrive.
        etCaSearch = EditText(ctx).apply {
            hint = "Cerca categoria…"
            textSize = 15f; setTextColor(Color.WHITE); setHintTextColor(Color.parseColor("#64748B"))
            setBackgroundColor(Color.parseColor("#0F172A"))
            setSingleLine(true)
            setPadding(24, 20, 24, 20)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) { renderCaCategoryList(s?.toString() ?: "") }
            })
        }
        root.addView(etCaSearch, lp(top = 14))

        llCaCategories = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(ctx).apply { addView(llCaCategories) }
        root.addView(scroll, LinearLayout.LayoutParams(MP, 0, 1f).apply { topMargin = 12 })

        val btnSkip = Button(ctx).apply {
            text = "Rinvia — decido dopo"
            textSize = 14f; setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#7C3AED"))
            setPadding(32, 20, 32, 20)
            setOnClickListener { onSnooze() }
        }
        root.addView(btnSkip, lp(top = 16))

        renderCaCurrentTransaction()
        return root
    }

    /** true se la categoria non ha un parent_id (categoria principale). */
    private fun isCaTopCategory(cat: JSONObject): Boolean =
        cat.isNull("parent_id") || cat.optString("parent_id", "").isBlank()

    /** id della categoria principale attualmente "aperta" (sottocategorie visibili) quando non
        c'è una ricerca in corso — solo una alla volta (fisarmonica), nessuna aperta di default. */
    private var caExpandedTopId: String? = null

    /** Mostra la prima transazione ancora in lista; se è vuota (tutte categorizzate) chiude il
        blocco come per un blocco informativo normale. Altrimenti azzera ricerca/apertura e
        ridisegna la lista categorie (principali chiuse). */
    private fun renderCaCurrentTransaction() {
        if (caTransactions.isEmpty()) {
            tvCaProgress.text = ""
            tvCaTransaction.text = "✅ Fatto! Nessuna transazione da categorizzare."
            etCaSearch.visibility = View.GONE
            llCaCategories.removeAllViews()
            handler.postDelayed({ if (rootView != null) finishCaPickerAndMaybeUnblock() }, 1200)
            return
        }
        val tx = caTransactions[0]
        val desc = tx.optString("description", "").ifBlank { "Transazione" }
        val amount = tx.optDouble("amount", 0.0)
        val currency = tx.optString("currency", "").ifBlank { "EUR" }
        val date = tx.optString("date", "")
        tvCaProgress.text = "1 di ${caTransactions.size}" + if (date.isNotBlank()) " — $date" else ""
        tvCaTransaction.text = "$desc\n${String.format(Locale.ITALY, "%.2f", amount)} $currency"
        etCaSearch.visibility = View.VISIBLE
        etCaSearch.setText("")
        caExpandedTopId = null
        renderCaCategoryList("")
    }

    /** Ridisegna la lista categorie. Senza ricerca: solo le principali, chiuse — toccarne una
        la apre/chiude mostrando le sottocategorie subito sotto (fisarmonica, una alla volta) più
        una riga per assegnare la principale stessa. Con una ricerca in corso: ignora lo stato
        aperto/chiuso e mostra tutte le principali+sottocategorie che matchano, come prima
        (comportamento identico a renderTxCategorySuggestions in cost-analysis.html). */
    private fun renderCaCategoryList(query: String) {
        llCaCategories.removeAllViews()
        if (caTransactions.isEmpty()) return
        val tx = caTransactions[0]
        val q = query.trim().lowercase(Locale.getDefault())
        val searching = q.isNotBlank()

        val topCats = caCategories.filter { isCaTopCategory(it) && it.optString("id", "").isNotBlank() }
        val kidsByParent = caCategories.filter { !isCaTopCategory(it) }
            .groupBy { it.optString("parent_id", "") }

        fun addCategoryButton(label: String, sub: Boolean, onClick: () -> Unit) {
            llCaCategories.addView(Button(ctx).apply {
                text = label
                textSize = if (sub) 14f else 15f
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor(if (sub) "#1F2937" else "#334155"))
                setAllCaps(false)
                setPadding(24, if (sub) 18 else 22, 24, if (sub) 18 else 22)
                setOnClickListener { onClick() }
            }, LinearLayout.LayoutParams(MP, WC).apply {
                topMargin = if (sub) 6 else 10
                if (sub) marginStart = 32
            })
        }

        var shown = 0
        topCats.forEach { top ->
            val topId = top.optString("id", "")
            val topName = top.optString("name", "")
            val kids = kidsByParent[topId].orEmpty()
            val topMatches = !searching || topName.lowercase(Locale.getDefault()).contains(q)
            val matchingKids = kids.filter { topMatches || it.optString("name", "").lowercase(Locale.getDefault()).contains(q) }
            if (searching && !topMatches && matchingKids.isEmpty()) return@forEach
            shown++

            val topIcon = top.optString("icon", "")
            val isExpanded = searching || caExpandedTopId == topId
            // Durante la ricerca il tap seleziona subito la principale (niente fisarmonica):
            // la freccia si mostra solo fuori ricerca, dove indica davvero apri/chiudi.
            val arrow = if (kids.isEmpty() || searching) "" else if (isExpanded) " ▾" else " ▸"
            val topLabel = (if (topIcon.isNotBlank()) "$topIcon " else "") + topName + arrow
            addCategoryButton(topLabel, sub = false) {
                if (searching || kids.isEmpty()) {
                    onCategoryChosen(tx, topId)
                } else if (caExpandedTopId == topId) {
                    caExpandedTopId = null
                    renderCaCategoryList("")
                } else {
                    caExpandedTopId = topId
                    renderCaCategoryList("")
                }
            }

            if (!isExpanded) return@forEach
            if (!searching && kids.isNotEmpty()) {
                addCategoryButton("→ usa \"$topName\"", sub = true) { onCategoryChosen(tx, topId) }
            }
            (if (searching) matchingKids else kids).forEach { kid ->
                val kidId = kid.optString("id", "")
                if (kidId.isBlank()) return@forEach
                val kidIcon = kid.optString("icon", "")
                val kidLabel = (if (kidIcon.isNotBlank()) "$kidIcon " else "") + kid.optString("name", "")
                addCategoryButton(kidLabel, sub = true) { onCategoryChosen(tx, kidId) }
            }
        }
        if (shown == 0) {
            llCaCategories.addView(TextView(ctx).apply {
                text = "Nessuna categoria trovata."
                textSize = 13f; setTextColor(Color.parseColor("#94A3B8"))
                gravity = Gravity.CENTER
                setPadding(0, 16, 0, 0)
            })
        }
    }

    /** Chiama la RPC in background, poi rimuove la transazione dalla lista locale e da
        metadata.cost_analysis.transactions (PATCH) così un eventuale rinvio non la ripropone. */
    private fun onCategoryChosen(tx: JSONObject, categoryId: String) {
        val txId = tx.optString("id", "")
        if (txId.isBlank()) return
        llCaCategories.removeAllViews()
        tvCaTransaction.text = "Salvataggio…"
        Thread {
            val ok = SupabaseApi(ctx).setTransactionCategory(txId, categoryId)
            handler.post {
                if (rootView == null) return@post // blocco chiuso nel frattempo (es. rinvio)
                if (ok) {
                    caTransactions.removeAll { it.optString("id") == txId }
                    persistCaData()
                    renderCaCurrentTransaction()
                } else {
                    tvCaTransaction.text = "Errore di salvataggio, riprovo…"
                    handler.postDelayed({ if (rootView != null) renderCaCurrentTransaction() }, 1500)
                }
            }
        }.start()
    }

    /** Salva la lista transazioni residue sia in Prefs (per un eventuale rebuild della view)
        sia su Supabase (PATCH dell'intero metadata, device_token incluso — vedi
        SupabaseApi.patchQueueMetadata). */
    private fun persistCaData() {
        val meta = caMetadata ?: return
        val ca = meta.optJSONObject("cost_analysis") ?: JSONObject().also { meta.put("cost_analysis", it) }
        val arr = JSONArray()
        caTransactions.forEach { arr.put(it) }
        ca.put("transactions", arr)
        Prefs.setBlockCaData(ctx, meta.toString())
        if (caQueueId.isNotBlank()) {
            val metaSnapshot = meta.toString()
            Thread { SupabaseApi(ctx).patchQueueMetadata(caQueueId, metaSnapshot) }.start()
        }
    }

    /** Il picker categorie può comparire insieme ad altri blocchi (task/sfide) dovuti nello
        stesso momento — in quel caso, terminato il picker, NON si sblocca il telefono: restano
        le altre entità da gestire con PIN, altrimenti l'utente eviterebbe di completarle
        davvero (e completeEntity() chiamerebbe erroneamente task_complete con l'id fittizio
        della riga cost_analysis, vedi log "task non trovato"). Si passa quindi alla schermata
        standard, filtrando via l'entità cost_analysis da Prefs. Se non restava nient'altro, si
        sblocca normalmente. */
    private fun finishCaPickerAndMaybeUnblock() {
        val remaining = Prefs.getBlockEntities(ctx).filter { it.app != "cost_analysis" }
        Prefs.clearBlockCaData(ctx)
        if (remaining.isNotEmpty()) {
            Prefs.setBlockEntities(ctx, remaining)
            switchToStandardView()
        } else {
            onInfoDismiss()
        }
    }

    /** Il picker ha priorità ogni volta che ci sono dati cost_analysis pendenti, anche se nello
        stesso blocco sono dovute anche altre entità (task/sfide) — prima non veniva mostrato in
        quel caso (serviva isInfoOnlyBlock, cioè TUTTE le entità dovevano essere cost_analysis),
        facendo apparire per errore la schermata PIN rossa standard invece del picker. */
    private fun buildView(): View {
        isCaPickerView = false
        loadCaDataFromPrefs()
        if (caTransactions.isNotEmpty() && caCategories.isNotEmpty()) {
            return buildCategoryPickerView()
        }
        return buildStandardView()
    }

    private fun buildStandardView(): View {
        // Verde per le sfide Ta Firi?, blu per le notifiche informative (es. cost_analysis),
        // rosso per i task (comportamento invariato).
        val bgColor = when {
            Prefs.isChallengeOnlyBlock(ctx) -> "#0F3D24"
            Prefs.isInfoOnlyBlock(ctx)      -> "#1E3A5F"
            else                             -> "#4A1414"
        }

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor(bgColor))
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

        if (Prefs.isChallengeOnlyBlock(ctx)) {
            root.addView(TextView(ctx).apply {
                text = "Hai portato a termine la sfida oggi?"
                textSize = 15f; setTextColor(Color.parseColor("#94A3B8"))
                gravity = Gravity.CENTER
            }, lp(MP, top = 24))

            root.addView(TextView(ctx).apply {
                text = "Tieni premuto per confermare"
                textSize = 12f; setTextColor(Color.parseColor("#64748B"))
                gravity = Gravity.CENTER
            }, lp(MP, top = 4))

            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            }
            val btnSi = buildHoldConfirmButton(ctx, handler, "SÌ", "#16A34A", Config.CHALLENGE_HOLD_MS) {
                onChallengeResponse("done")
            }
            val btnNo = buildHoldConfirmButton(ctx, handler, "NO", "#DC2626", Config.CHALLENGE_HOLD_MS) {
                onChallengeResponse("not_done")
            }
            row.addView(btnSi, LinearLayout.LayoutParams(0, HOLD_BTN_HEIGHT, 1f).apply { marginEnd = 12 })
            row.addView(btnNo, LinearLayout.LayoutParams(0, HOLD_BTN_HEIGHT, 1f).apply { marginStart = 12 })
            root.addView(row, lp(MP, top = 20))
        } else if (Prefs.isInfoOnlyBlock(ctx)) {
            root.addView(TextView(ctx).apply {
                text = "Informazione"
                textSize = 15f; setTextColor(Color.parseColor("#94A3B8"))
                gravity = Gravity.CENTER
            }, lp(MP, top = 24))

            val btnOk = Button(ctx).apply {
                text = "Ho capito"
                textSize = 16f; setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#2563EB"))
                setPadding(48, 24, 48, 24)
                setOnClickListener { onInfoDismiss() }
            }
            root.addView(btnOk, lp(MP, top = 20))
        } else {
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
        }

        return root
    }

    companion object {
        private const val MP = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WC = ViewGroup.LayoutParams.WRAP_CONTENT
        private const val HOLD_BTN_HEIGHT = 170
    }
}
