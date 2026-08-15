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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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
}
