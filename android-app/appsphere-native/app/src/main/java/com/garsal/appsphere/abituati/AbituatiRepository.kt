package com.garsal.appsphere.abituati

import com.garsal.appsphere.core.Supabase
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.add
import java.time.LocalDate

/**
 * Abituati: letture dirette, **scritture solo via RPC**.
 *
 * ⚠️ È la stessa regola dei task, e qui vale ancora di più: streak, jolly,
 * chiusura degli stack e giorni mancati vivono in `hb_streak`,
 * `hb_set_completion`, `hb_reconcile` e `hb_chiudi_stack`
 * (`20260815120000_hb_regole_rpc.sql`), che `habit-tracker.html` chiama
 * esattamente come questo file. **Nessun calcolo di streak o di jolly in
 * Kotlin**: sarebbero una seconda regola per lo stesso stack, diversa a
 * seconda dell'app da cui lo tocchi, e in ballo ci sono punti e archivi.
 *
 * Scrivere l'abitudine — crearla, modificarla — è invece un `insert`/`update`
 * diretto, come `saveTask()`: le RPC governano il ciclo di vita, non com'è
 * fatta l'abitudine.
 */
object AbituatiRepository {

    private val db get() = Supabase.client().postgrest

    // ── Letture ──────────────────────────────────────────────────────────

    /**
     * Le abitudini vive. Come nel web si prendono `active` e `stopped`, e le
     * riservate restano fuori: la modalità nascosta qui non c'è, e una
     * schermata che si apre senza chiedere niente è il posto sbagliato per
     * mostrarle.
     */
    suspend fun abitudini(): List<HbAbitudine> = withContext(Dispatchers.IO) {
        db.from("hb_habits")
            .select(Columns.ALL) {
                filter { isIn("status", listOf("active", "stopped")) }
                order("created_at", Order.DESCENDING)
            }
            .decodeList<JsonObject>()
            .mapNotNull { HbAbitudine.da(it) }
            .filterNot { it.riservata }
    }

    suspend fun spunte(): List<HbSpunta> = withContext(Dispatchers.IO) {
        db.from("hb_completions")
            .select(Columns.ALL) { order("completed_at", Order.DESCENDING) }
            .decodeList<JsonObject>()
            .mapNotNull { HbSpunta.da(it) }
    }

    suspend fun archivio(): List<HbArchiviato> = withContext(Dispatchers.IO) {
        db.from("hb_archived_stacks")
            .select(Columns.ALL) { order("ended_at", Order.DESCENDING) }
            .decodeList<JsonObject>()
            .mapNotNull { HbArchiviato.da(it) }
    }

    suspend fun categorie(): List<HbCategoria> = withContext(Dispatchers.IO) {
        db.from("cm_categories")
            .select(Columns.raw("id,name,icon,color")) { order("name", Order.ASCENDING) }
            .decodeList<JsonObject>()
            .mapNotNull { HbCategoria.da(it) }
    }

    /** Lo streak di un'abitudine, calcolato dal database e non qui. */
    suspend fun streak(abitudineId: String, oggi: LocalDate): Int = withContext(Dispatchers.IO) {
        runCatching {
            db.rpc(
                "hb_streak",
                buildJsonObject {
                    put("p_habit_id", abitudineId)
                    put("p_oggi", oggi.toString())
                }
            ).decodeAs<Int>()
        }.getOrDefault(0)
    }

    /**
     * Quanti jolly costerebbe riprendere un'abitudine interrotta da una certa
     * data: uno per ogni giorno dovuto senza spunta completata, fra `da` e
     * ieri.
     *
     * ⚠️ Il conto lo fa il database (`hb_giorni_da_recuperare`), non questo
     * file — lo stesso avviso serve a `habit-tracker.html`, e due copie della
     * formula sono due avvisi diversi il giorno che una delle due cambia. È la
     * stessa ragione per cui lo streak non si calcola qui.
     *
     * Torna `null` — non zero — se la chiamata non riesce: «non lo so» e
     * «nessun giorno da recuperare» sono due cose diverse, e la seconda al
     * posto della prima farebbe riprendere un'abitudine dicendo che non costa
     * niente.
     */
    suspend fun giorniDaRecuperare(
        abitudineId: String,
        da: LocalDate,
        oggi: LocalDate = LocalDate.now(),
    ): Int? = withContext(Dispatchers.IO) {
        runCatching {
            db.rpc(
                "hb_giorni_da_recuperare",
                buildJsonObject {
                    put("p_habit_id", abitudineId)
                    put("p_da", da.toString())
                    put("p_oggi", oggi.toString())
                }
            ).decodeAs<Int>()
        }.getOrNull()
    }

    // ── Scritture: solo RPC ──────────────────────────────────────────────

    /**
     * Segna un periodo: `completed`, `failed`, `skipped` o `none` (toglie la
     * spunta). `orario` serve solo alle abitudini a più slot.
     *
     * La risposta dice anche se lo stack è arrivato all'obiettivo o se i jolly
     * sono finiti: sono i due segnali che aprono una cerimonia, e la cerimonia
     * la fa il client — la RPC non archivia niente da sé.
     */
    suspend fun segna(
        abitudineId: String,
        giorno: LocalDate,
        stato: String,
        orario: String? = null,
        oggi: LocalDate = LocalDate.now(),
    ): JsonObject = withContext(Dispatchers.IO) {
        db.rpc(
            "hb_set_completion",
            buildJsonObject {
                put("p_habit_id", abitudineId)
                put("p_giorno", giorno.toString())
                put("p_stato", stato)
                if (orario != null) put("p_orario", orario)
                put("p_oggi", oggi.toString())
            }
        ).decodeAs<JsonObject>()
    }

    /**
     * Il giro di riconciliazione, da fare all'apertura come fa il web a ogni
     * disegno della dashboard: giorni mancati, jolly riallineati, stack
     * completati e scaduti. Torna cosa è successo.
     */
    suspend fun riconcilia(oggi: LocalDate = LocalDate.now()): Pair<List<HbEsito>, List<HbEsito>> =
        withContext(Dispatchers.IO) {
            val esito = db.rpc(
                "hb_reconcile",
                buildJsonObject { put("p_oggi", oggi.toString()) }
            ).decodeAs<JsonObject>()

            val completati = (esito["completati"] as? JsonArray)
                ?.mapNotNull { (it as? JsonObject)?.let { r -> HbEsito.da(r) } }
                .orEmpty()
            val gameOver = (esito["game_over"] as? JsonArray)
                ?.mapNotNull { (it as? JsonObject)?.let { r -> HbEsito.da(r) } }
                .orEmpty()
            completati to gameOver
        }

    /**
     * Le due uscite del «game over»: `nuovoInizio` nullo interrompe lo stack,
     * una data lo riapre da lì.
     */
    suspend fun chiudiStack(
        abitudineId: String,
        nuovoInizio: LocalDate?,
        oggi: LocalDate = LocalDate.now(),
    ): JsonObject = withContext(Dispatchers.IO) {
        db.rpc(
            "hb_chiudi_stack",
            buildJsonObject {
                put("p_habit_id", abitudineId)
                put("p_oggi", oggi.toString())
                if (nuovoInizio != null) put("p_nuovo_inizio", nuovoInizio.toString())
            }
        ).decodeAs<JsonObject>()
    }

    /** Riapre uno stack appena completato, dalla data scelta. */
    suspend fun nuovoCiclo(abitudineId: String, inizio: LocalDate): Unit =
        withContext(Dispatchers.IO) {
            db.rpc(
                "hb_clona",
                buildJsonObject {
                    put("p_habit_id", abitudineId)
                    put("p_inizio", inizio.toString())
                }
            )
            Unit
        }

    // ── L'abitudine in sé ────────────────────────────────────────────────

    /**
     * Crea o modifica un'abitudine. Il **tipo non si cambia** su una che
     * esiste già, come per i task: la frequenza decide quali colonne quella
     * riga usa — `daily_times` su una settimanale non la pulisce nessuno — e i
     * giorni già spuntati sono stati contati con la regola di prima.
     */
    suspend fun salva(id: String?, b: BozzaAbitudine) = withContext(Dispatchers.IO) {
        val riga = buildJsonObject {
            put("name", b.nome.trim())
            put("description", b.descrizione.trim())
            b.categoriaId?.let { put("category_id", it) }
            put("goal", b.obiettivo)
            put("max_failures", b.jolly)
            put("points_reward", b.puntiPremio)
            put("points_penalty", b.puntiPenalita)
            if (id == null) {
                put("frequency", b.frequenza)
                put("started_at", b.inizio.toString())
                put("status", "active")
                put("current_failures", 0)
                put("riservato", false)
                if (b.frequenza == "weekly") {
                    put("weekdays", buildJsonArray { b.giorniSettimana.sorted().forEach { add(it) } })
                }
                if (b.frequenza == "daily_multiple") {
                    put("daily_times", buildJsonArray { b.orari.sorted().forEach { add(it) } })
                }
            } else {
                // Su un'abitudine che esiste si aggiornano anche i giorni e gli
                // orari, che non cambiano il *tipo*: cambiano quando la si
                // spunta, ed è una modifica che il web permette.
                if (b.frequenza == "weekly") {
                    put("weekdays", buildJsonArray { b.giorniSettimana.sorted().forEach { add(it) } })
                }
                if (b.frequenza == "daily_multiple") {
                    put("daily_times", buildJsonArray { b.orari.sorted().forEach { add(it) } })
                }
            }
        }

        if (id == null) db.from("hb_habits").insert(riga)
        else db.from("hb_habits").update(riga) { filter { eq("id", id) } }
        Unit
    }

    /**
     * INTERROMPI: l'abitudine passa a `stopped`. Non si cancella niente — la
     * riga e le sue spunte restano — ma esce dalla riconciliazione, quindi da
     * lì in poi non genera `missed`, non consuma jolly e non può né vincere né
     * fallire.
     *
     * Il promemoria se ne va con lo stack: una regola che continuasse a
     * chiedere di spuntare un'abitudine ferma sarebbe solo una sveglia da
     * spegnere. È quello che fa `stopHabit()` nel web.
     */
    suspend fun interrompi(id: String) = withContext(Dispatchers.IO) {
        db.from("hb_habits")
            .update(buildJsonObject { put("status", "stopped") }) { filter { eq("id", id) } }
        runCatching {
            // Prima le notifiche già in coda, poi la regola: una notifica
            // partita dopo l'interruzione chiederebbe di spuntare un'abitudine
            // ferma, e la regola cancellata non la fermerebbe — è già uscita.
            // Stesso ordine di `deleteHabitNotificationRule()` nel web.
            db.from("cm_notification_queue").update(
                buildJsonObject { put("status", "cancelled") }
            ) {
                filter {
                    eq("app", "habits")
                    eq("entity_id", id)
                    isIn("status", listOf("pending", "snoozed"))
                }
            }
            db.from("cm_notification_rules").delete {
                filter {
                    eq("app", "habits")
                    eq("entity_id", id)
                }
            }
        }
        Unit
    }

    /**
     * RIPRENDI: l'abitudine torna `active` **da una data scelta**, che diventa
     * il suo `started_at`.
     *
     * ⚠️ La data non è un vezzo. Tenendo quella di partenza, il primo giro di
     * `hb_reconcile` — che guarda da `started_at` a ieri — marcherebbe `missed`
     * ogni giorno passato dall'interruzione: i jolly finirebbero sul posto e il
     * game over scatterebbe prima ancora di rivedere la scheda. Quanto costi
     * una certa data lo dice `giorniDaRecuperare()`, prima di scrivere.
     *
     * `current_failures` torna a zero perché è una **cache**: la
     * riconciliazione la ricalcola dai completamenti al primo giro.
     */
    suspend fun riprendi(id: String, inizio: LocalDate) = withContext(Dispatchers.IO) {
        db.from("hb_habits").update(
            buildJsonObject {
                put("status", "active")
                put("started_at", inizio.toString())
                put("current_failures", 0)
            }
        ) { filter { eq("id", id) } }
        Unit
    }

    /**
     * Elimina un'abitudine e la sua regola di promemoria.
     *
     * Le spunte se ne vanno per cascata; l'archivio no, ed è giusto — uno
     * stack finito è memoria, non dipende dalla riga viva.
     */
    suspend fun elimina(id: String) = withContext(Dispatchers.IO) {
        runCatching {
            db.from("cm_notification_rules").delete {
                filter {
                    eq("app", "habits")
                    eq("entity_id", id)
                }
            }
        }
        db.from("hb_habits").delete { filter { eq("id", id) } }
        Unit
    }
}
