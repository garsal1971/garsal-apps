package com.garsal.appsphere.peso

import com.garsal.appsphere.core.Supabase
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

// ── Righe ────────────────────────────────────────────────────────────────
//
// ⚠️ `ps_weight_tracking` e `ps_objectives` non stanno in nessuna migration:
// sono nate a mano prima che esistesse la cartella `migrations/`, e
// `weight-quest.html` le legge con `select=*`. Si decodificano quindi come
// `JsonObject` e non come data class serializzate — la stessa scelta fatta per
// `ts_tasks`, e per la stessa ragione: qui `id` può essere un numero o un uuid
// e `weight` un intero o un decimale, e con una data class una colonna del tipo
// inatteso non darebbe un campo storto ma farebbe fallire la decodifica
// dell'intera lista, cioè la schermata vuota.

/** Una pesata: `timestamp` è l'istante in millisecondi, ed è la chiave. */
data class Pesata(
    val giorno: String,
    val ora: String?,
    val timestamp: Long,
    val peso: Double,
    val target: Double?,
) {
    /** Vera per le pesate scritte a mano, che sono le uniche modificabili. */
    val manuale: Boolean get() = ora == "Manuale"

    companion object {
        fun da(o: JsonObject): Pesata? {
            val giorno = testo(o, "date") ?: return null
            val peso = decimale(o, "weight") ?: return null
            return Pesata(
                giorno = giorno,
                ora = testo(o, "time"),
                timestamp = intero(o, "timestamp") ?: 0L,
                peso = peso,
                target = decimale(o, "target_weight"),
            )
        }
    }
}

/** Un traguardo della curva: a quella data si dovrebbe pesare quel tanto. */
data class Traguardo(val giorno: String, val peso: Double)

data class Obiettivo(
    /** Testo e non numero: la colonna può essere `bigint` o `uuid`, e a noi
     *  serve solo per rifiltrare le righe — mai per fare conti. */
    val id: String,
    val nome: String,
    val tipo: String,
    val inizio: String,
    val fine: String,
    val pesoIniziale: Double?,
    val pesoFinale: Double?,
    val bonusGiornaliero: Int,
    val malusGiornaliero: Int,
    val bonusFinale: Int,
    val malusFinale: Int,
    val stato: String,
    val punteggioFinale: Int?,
    val traguardi: List<Traguardo>,
) {
    val attivo: Boolean get() = stato != "success" && stato != "failed"

    companion object {
        fun da(o: JsonObject): Obiettivo? {
            val id = testo(o, "id") ?: return null
            return Obiettivo(
                id = id,
                nome = testo(o, "objective_name") ?: "Senza nome",
                tipo = testo(o, "objective_type") ?: "perdere",
                inizio = testo(o, "start_date").orEmpty(),
                fine = testo(o, "end_date").orEmpty(),
                pesoIniziale = decimale(o, "start_weight"),
                pesoFinale = decimale(o, "end_weight"),
                // Gli stessi ripieghi del web: `obj.daily_bonus || 10`.
                bonusGiornaliero = intero(o, "daily_bonus")?.toInt()?.takeIf { it != 0 } ?: 10,
                malusGiornaliero = intero(o, "daily_malus")?.toInt()?.takeIf { it != 0 } ?: 5,
                bonusFinale = intero(o, "final_bonus")?.toInt()?.takeIf { it != 0 } ?: 100,
                malusFinale = intero(o, "final_malus")?.toInt()?.takeIf { it != 0 } ?: 50,
                stato = testo(o, "status") ?: "active",
                punteggioFinale = intero(o, "total_score")?.toInt(),
                traguardi = traguardiDa(o["milestones"]),
            )
        }

        /**
         * I traguardi arrivano come **stringa JSON** (`JSON.stringify` nel
         * web), ma una riga scritta a mano potrebbe averli come array jsonb:
         * si accettano tutt'e due invece di fidarsi di come sono stati scritti
         * la prima volta.
         */
        private fun traguardiDa(valore: kotlinx.serialization.json.JsonElement?): List<Traguardo> {
            val array = when {
                valore == null || valore is JsonNull -> return emptyList()
                valore is JsonArray -> valore
                valore is JsonPrimitive && valore.isString ->
                    runCatching { Json.parseToJsonElement(valore.content) as? JsonArray }
                        .getOrNull() ?: return emptyList()
                else -> return emptyList()
            }
            return array.mapNotNull { voce ->
                val o = voce as? JsonObject ?: return@mapNotNull null
                val giorno = testo(o, "date") ?: return@mapNotNull null
                val peso = decimale(o, "weight") ?: return@mapNotNull null
                Traguardo(giorno, peso)
            }.sortedBy { it.giorno }
        }
    }
}

/**
 * Quello che compila il form di Gestione Obiettivo — i campi di
 * `weight-quest.html`, comuni ai due tipi più quelli specifici di
 * «mantenere peso».
 *
 * ⚠️ Non è un dettaglio di forma: `valida` e [PesoRepository.rigaObiettivo]
 * ricalcano `doSaveMilestones()` / `saveMaintainObjective()` — cambiando una
 * regola qui va cambiata anche là.
 */
data class BozzaObiettivo(
    val nome: String = "",
    /** `"perdere"` o `"mantenere"`. */
    val tipo: String = "perdere",
    val bonusGiornaliero: Int = 10,
    val malusGiornaliero: Int = 5,
    val bonusFinale: Int = 100,
    val malusFinale: Int = 50,
    /** Milestone progressive — solo per `"perdere"`. */
    val traguardi: List<Traguardo> = emptyList(),
    /** Campi specifici di `"mantenere"`: N settimane a peso piatto. */
    val mantInizio: LocalDate = LocalDate.now(),
    val mantSettimane: Int = 4,
    val mantPeso: Double? = null,
) {
    val validaPerdere: Boolean get() = nome.isNotBlank() && traguardi.size >= 2
    val validaMantenere: Boolean get() = nome.isNotBlank() && mantSettimane >= 1 && (mantPeso ?: 0.0) > 0.0
    val valida: Boolean get() = if (tipo == "mantenere") validaMantenere else validaPerdere

    companion object {
        fun nuova() = BozzaObiettivo()

        /** Ricostruita da un obiettivo esistente, per modificarlo. */
        fun da(o: Obiettivo): BozzaObiettivo {
            val inizio = PesoRegole.giornoDa(o.inizio) ?: LocalDate.now()
            // Le settimane non stanno in colonna: si ricavano dal periodo,
            // come fa `loadObjectiveById()` nel web.
            val settimane = PesoRegole.giornoDa(o.fine)?.let { fine ->
                maxOf(1, Math.round((fine.toEpochDay() - inizio.toEpochDay()) / 7.0).toInt())
            } ?: 4
            return BozzaObiettivo(
                nome = o.nome,
                tipo = o.tipo,
                bonusGiornaliero = o.bonusGiornaliero,
                malusGiornaliero = o.malusGiornaliero,
                bonusFinale = o.bonusFinale,
                malusFinale = o.malusFinale,
                traguardi = o.traguardi,
                mantInizio = inizio,
                mantSettimane = settimane,
                mantPeso = o.pesoIniziale,
            )
        }
    }
}

// ── Lettura dei campi JSON ───────────────────────────────────────────────

private fun campo(o: JsonObject, chiave: String) = o[chiave]?.takeIf { it !is JsonNull }

internal fun testo(o: JsonObject, chiave: String): String? =
    (campo(o, chiave) as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }

internal fun decimale(o: JsonObject, chiave: String): Double? =
    (campo(o, chiave) as? JsonPrimitive)?.content?.toDoubleOrNull()

internal fun intero(o: JsonObject, chiave: String): Long? =
    (campo(o, chiave) as? JsonPrimitive)?.content?.toDoubleOrNull()?.toLong()

object PesoRepository {

    private val db get() = Supabase.client().postgrest

    /**
     * Le pesate, dalla più vecchia alla più recente.
     *
     * Il web pagina a mille per volta perché PostgREST non ne dà di più in una
     * richiesta; qui si chiede una finestra — l'ultimo anno e mezzo, che copre
     * qualunque obiettivo in corso — invece di scaricare tutto lo storico a
     * ogni apertura. Se un giorno servisse più indietro, si allarga di qui.
     */
    suspend fun pesate(da: LocalDate): List<Pesata> = withContext(Dispatchers.IO) {
        db.from("ps_weight_tracking")
            .select(Columns.ALL) {
                filter { gte("date", da.toString()) }
                order("date", Order.ASCENDING)
                limit(5000L)
            }
            .decodeList<JsonObject>()
            .mapNotNull { Pesata.da(it) }
    }

    suspend fun obiettivi(): List<Obiettivo> = withContext(Dispatchers.IO) {
        db.from("ps_objectives")
            .select(Columns.ALL) { order("created_at", Order.DESCENDING) }
            .decodeList<JsonObject>()
            .mapNotNull { Obiettivo.da(it) }
    }

    /**
     * Scrive una pesata, come `saveDayDetailEntry()` nel web: `time` vale
     * sempre `Manuale`, il `timestamp` è l'istante di giorno + ora in
     * millisecondi — la chiave su cui l'upsert si appoggia — e il target è
     * quello interpolato di quel giorno, congelato nella riga.
     *
     * ⚠️ Il target si scrive **al momento della pesata** e non si ricalcola
     * mai: se un domani si spostano i traguardi, i giorni già passati devono
     * restare giudicati con la curva che c'era allora.
     */
    suspend fun salvaPesata(
        giorno: LocalDate,
        ora: LocalTime,
        peso: Double,
        target: Double?,
    ): Pesata = withContext(Dispatchers.IO) {
        val istante = giorno.atTime(ora).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val riga = buildJsonObject {
            put("date", giorno.toString())
            put("time", "Manuale")
            put("timestamp", istante)
            put("weight", Math.round(peso * 100.0) / 100.0)
            put("target_weight", target)
        }
        db.from("ps_weight_tracking").upsert(riga) { onConflict = "timestamp" }
        Pesata(giorno.toString(), "Manuale", istante, peso, target)
    }

    suspend fun eliminaPesata(timestamp: Long) = withContext(Dispatchers.IO) {
        db.from("ps_weight_tracking").delete { filter { eq("timestamp", timestamp) } }
        Unit
    }

    // ── Gestione obiettivi ──────────────────────────────────────────────

    /**
     * La riga da scrivere per un obiettivo, ricalcata da `doSaveMilestones()`
     * / `saveMaintainObjective()`: per «mantenere» le milestone sono due
     * punti allo stesso peso (target piatto per N settimane), per «perdere»
     * sono quelle compilate a mano — la prima e l'ultima decidono
     * `start_date`/`start_weight`/`end_date`/`end_weight`.
     */
    fun rigaObiettivo(bozza: BozzaObiettivo): JsonObject {
        val traguardi = if (bozza.tipo == "mantenere") {
            val peso = bozza.mantPeso ?: 0.0
            val fine = bozza.mantInizio.plusDays(bozza.mantSettimane * 7L)
            listOf(Traguardo(bozza.mantInizio.toString(), peso), Traguardo(fine.toString(), peso))
        } else {
            bozza.traguardi.sortedBy { it.giorno }
        }
        val primo = traguardi.first()
        val ultimo = traguardi.last()

        return buildJsonObject {
            put("objective_name", bozza.nome.trim())
            put("objective_type", bozza.tipo)
            put("start_date", primo.giorno)
            put("start_weight", primo.peso)
            put("end_date", ultimo.giorno)
            put("end_weight", ultimo.peso)
            put("daily_bonus", bozza.bonusGiornaliero)
            put("daily_malus", bozza.malusGiornaliero)
            put("final_bonus", bozza.bonusFinale)
            put("final_malus", bozza.malusFinale)
            put("milestones", milestoniJson(traguardi))
            put("status", "active")
        }
    }

    private fun milestoniJson(traguardi: List<Traguardo>): String {
        val array = buildJsonArray {
            traguardi.forEach { t ->
                add(buildJsonObject {
                    put("date", t.giorno)
                    put("weight", t.peso)
                })
            }
        }
        return array.toString()
    }

    /**
     * Crea (`id == null`) o aggiorna un obiettivo — `persistObjective()`.
     * Torna l'id, nuovo o quello passato, perché il chiamante possa
     * riselezionarlo dopo il ricaricamento.
     */
    suspend fun salvaObiettivo(id: String?, riga: JsonObject): String = withContext(Dispatchers.IO) {
        if (id == null) {
            db.from("ps_objectives").insert(riga) { select(Columns.raw("id")) }
                .decodeList<JsonObject>()
                .firstOrNull()
                ?.let { testo(it, "id") }
                ?: error("L'obiettivo non ha restituito un id.")
        } else {
            db.from("ps_objectives").update(riga) { filter { eq("id", id) } }
            id
        }
    }

    /** Chiude un obiettivo con lo stato e il punteggio finale — `closeObjective()`. */
    suspend fun chiudiObiettivo(id: String, stato: String, punteggioFinale: Int) = withContext(Dispatchers.IO) {
        db.from("ps_objectives").update(
            buildJsonObject {
                put("status", stato)
                put("total_score", punteggioFinale)
            }
        ) { filter { eq("id", id) } }
        Unit
    }

    suspend fun eliminaObiettivo(id: String) = withContext(Dispatchers.IO) {
        db.from("ps_objectives").delete { filter { eq("id", id) } }
        Unit
    }

    // ── Sincronizzazione con la bilancia (Health Connect) ────────────────

    /**
     * La riga da scrivere per una pesata letta da Health Connect —
     * `processWeights()` nel web: data e ora locali dell'istante, **peso
     * troncato a un decimale** (non arrotondato — `Math.floor`, come là) e il
     * target interpolato sull'obiettivo che si sta guardando in quel momento.
     */
    fun rigaPuntoSalute(punto: PuntoSalute, obiettivo: Obiettivo?): JsonObject {
        val locale = Instant.ofEpochMilli(punto.timestamp).atZone(ZoneId.systemDefault())
        val giorno = punto.giorno()
        val ora = "%02d:%02d".format(locale.hour, locale.minute)
        val peso = Math.floor(punto.pesoKg * 10.0) / 10.0
        return buildJsonObject {
            put("date", giorno)
            put("time", ora)
            put("timestamp", punto.timestamp)
            put("weight", peso)
            put("target_weight", obiettivo?.let { PesoRegole.targetInterpolato(it.traguardi, giorno) })
        }
    }

    /**
     * Scrive le pesate sincronizzate e toglie le pesate manuali che
     * diventano ridondanti — `processWeights()`: prima elimina, poi
     * scrive, così una pesata manuale del giorno non resta a fare da
     * doppione quando arriva quella vera dalla bilancia.
     */
    suspend fun sincronizzaPunti(righe: List<JsonObject>, manualiDaRimuovere: List<Long>) =
        withContext(Dispatchers.IO) {
            if (manualiDaRimuovere.isNotEmpty()) {
                db.from("ps_weight_tracking").delete { filter { isIn("timestamp", manualiDaRimuovere) } }
            }
            if (righe.isNotEmpty()) {
                db.from("ps_weight_tracking").upsert(righe) { onConflict = "timestamp" }
            }
            Unit
        }
}
