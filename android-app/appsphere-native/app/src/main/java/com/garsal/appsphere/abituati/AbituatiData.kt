package com.garsal.appsphere.abituati

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate

// ── Perché qui non ci sono @Serializable data class ──────────────────────────
//
// `hb_habits`, `hb_completions` e `hb_archived_stacks` non stanno in nessuna
// migration: sono nate a mano, come `ts_tasks`. E come là alcune colonne sono
// ambigue di natura — `weekdays` può essere `text[]` o `integer[]`,
// `daily_times` una lista di stringhe o di orari — quindi si legge un
// JsonObject e si converte campo per campo: un valore inatteso costa quel
// campo, non l'intera schermata.

/** Un'abitudine, cioè uno «stack» in corso. */
data class HbAbitudine(
    val id: String,
    val nome: String,
    val descrizione: String?,
    val categoriaId: String?,
    val frequenza: String,
    val giorniSettimana: List<Int>,
    val orari: List<String>,
    val inizio: String?,
    val obiettivo: Int,
    val jollyMassimi: Int,
    val jollyUsati: Int,
    val stato: String,
    val puntiPremio: Int,
    val puntiPenalita: Int,
    val riservata: Boolean,
) {
    val aPiuOrari: Boolean get() = frequenza == "daily_multiple" && orari.isNotEmpty()

    val settimanale: Boolean get() = frequenza == "weekly" && giorniSettimana.isNotEmpty()

    val giornoInizio: LocalDate? get() = inizio?.take(10)?.let {
        runCatching { LocalDate.parse(it) }.getOrNull()
    }

    /**
     * Se l'abitudine va spuntata in un certo giorno — la stessa domanda di
     * `isDayApplicable()`: le giornaliere sempre, le settimanali solo nei
     * giorni scelti, numerati alla JavaScript (0 = domenica).
     */
    fun cadeIl(giorno: LocalDate): Boolean {
        val da = giornoInizio
        if (da != null && giorno < da) return false
        // `dayOfWeek.value` va da 1 (lunedì) a 7 (domenica); il resto della
        // divisione per sette dà la numerazione di JavaScript, che è quella
        // con cui i giorni sono salvati.
        return if (settimanale) (giorno.dayOfWeek.value % 7) in giorniSettimana
        else frequenza == "daily" || frequenza == "daily_multiple"
    }

    companion object {
        fun da(o: JsonObject): HbAbitudine? {
            val id = testo(o, "id") ?: return null
            return HbAbitudine(
                id = id,
                nome = testo(o, "name").orEmpty(),
                descrizione = testo(o, "description"),
                categoriaId = testo(o, "category_id"),
                frequenza = testo(o, "frequency") ?: "daily",
                giorniSettimana = lista(o, "weekdays").mapNotNull { it.toIntOrNull() },
                orari = lista(o, "daily_times").map { it.take(5) },
                inizio = testo(o, "started_at"),
                obiettivo = numero(o, "goal") ?: 0,
                jollyMassimi = numero(o, "max_failures") ?: 0,
                jollyUsati = numero(o, "current_failures") ?: 0,
                stato = testo(o, "status") ?: "active",
                puntiPremio = numero(o, "points_reward") ?: 0,
                puntiPenalita = numero(o, "points_penalty") ?: 0,
                riservata = testo(o, "riservato")?.toBooleanStrictOrNull() ?: false,
            )
        }
    }
}

/** Una spunta: `completed`, `failed`, `skipped` o `missed`. */
data class HbSpunta(
    val id: String,
    val abitudineId: String,
    val quando: String,
    val stato: String,
    val chiave: String?,
) {
    /**
     * Il giorno della spunta, letto dai primi dieci caratteri come fa il web
     * con `completed_at.startsWith(dateStr)`. Non si converte il fuso: la riga
     * è stata scritta a mezzogiorno (o all'ora dello slot) proprio perché la
     * data restasse quella, comunque la si legga.
     */
    val giorno: String get() = quando.take(10)

    /** L'orario `HH:MM`, per le abitudini a più slot. */
    val orario: String get() = quando.drop(11).take(5)

    companion object {
        fun da(o: JsonObject): HbSpunta? {
            val id = testo(o, "id") ?: return null
            return HbSpunta(
                id = id,
                abitudineId = testo(o, "habit_id").orEmpty(),
                quando = testo(o, "completed_at").orEmpty(),
                stato = testo(o, "status") ?: "completed",
                chiave = testo(o, "period_key"),
            )
        }
    }
}

/** Uno stack finito, in archivio. */
data class HbArchiviato(
    val id: String,
    val nome: String,
    val inizio: String?,
    val fine: String?,
    val streak: Int,
    val giorni: Int,
    val completamenti: Int,
    val fallimenti: Int,
    val punti: Int,
    val motivo: String,
) {
    /** Come lo racconta la scheda in archivio nel web. */
    val esito: String get() = when (motivo) {
        "completato" -> "🏆 Completato"
        "completato_con_jolly" -> "🏆 Completato coi jolly"
        "scadenza_calendario" -> "⏰ Scaduto"
        "jolly_esauriti" -> "💀 Jolly esauriti"
        else -> motivo
    }

    companion object {
        fun da(o: JsonObject): HbArchiviato? {
            val id = testo(o, "id") ?: return null
            return HbArchiviato(
                id = id,
                nome = testo(o, "habit_name").orEmpty(),
                inizio = testo(o, "started_at"),
                fine = testo(o, "ended_at"),
                streak = numero(o, "final_streak") ?: 0,
                giorni = numero(o, "total_days") ?: 0,
                completamenti = numero(o, "total_completions") ?: 0,
                fallimenti = numero(o, "total_failures") ?: 0,
                punti = numero(o, "points_earned") ?: 0,
                motivo = testo(o, "reason").orEmpty(),
            )
        }
    }
}

/** Una categoria condivisa (`cm_categories`), la stessa tabella di Tasks. */
data class HbCategoria(val id: String, val nome: String, val icona: String, val colore: String) {
    val etichetta: String get() = if (icona.isBlank()) nome else "$icona $nome"

    companion object {
        fun da(o: JsonObject): HbCategoria? {
            val id = testo(o, "id") ?: return null
            return HbCategoria(
                id = id,
                nome = testo(o, "name").orEmpty(),
                icona = testo(o, "icon").orEmpty(),
                colore = testo(o, "color")?.takeIf { it.isNotBlank() } ?: "#6B7280",
            )
        }
    }
}

/**
 * Un esito che `hb_reconcile` segnala e che vuole una cerimonia: uno stack
 * completato o un game over.
 */
data class HbEsito(
    val abitudineId: String,
    val nome: String,
    val streak: Int,
    val punti: Int,
    val motivo: String,
    val mancati: Int = 0,
    val giaArchiviato: Boolean = true,
) {
    val vinto: Boolean get() = motivo == "completato" || motivo == "completato_con_jolly"

    companion object {
        fun da(o: JsonObject) = HbEsito(
            abitudineId = testo(o, "habit_id").orEmpty(),
            nome = testo(o, "nome").orEmpty(),
            streak = numero(o, "streak") ?: 0,
            punti = numero(o, "punti") ?: 0,
            motivo = testo(o, "motivo").orEmpty(),
            mancati = numero(o, "mancati") ?: 0,
            giaArchiviato = testo(o, "archiviato")?.toBooleanStrictOrNull() ?: true,
        )
    }
}

internal fun testo(o: JsonObject, chiave: String): String? =
    (o[chiave] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content

internal fun numero(o: JsonObject, chiave: String): Int? =
    testo(o, chiave)?.toDoubleOrNull()?.toInt()

internal fun lista(o: JsonObject, chiave: String): List<String> =
    (o[chiave] as? JsonArray)
        ?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p !is JsonNull }?.content }
        .orEmpty()
