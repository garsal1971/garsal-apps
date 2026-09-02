package com.garsal.appsphere.memo

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// ── Perché qui non ci sono @Serializable data class ──────────────────────────
//
// `mm_cards`, `mm_card_categories` e `mm_images` non stanno in nessuna
// migration: nascono dal SQL che `memo.html` mostra in Impostazioni, da
// incollare a mano nella dashboard di Supabase. Come per `ts_tasks`, le colonne
// si conoscono solo da come la pagina le scrive, e una colonna del tipo
// inatteso con una data class farebbe fallire la decodifica dell'intera lista —
// cioè la schermata vuota invece di una scheda storta.

/**
 * Il tipo di una scheda (`mm_cards.kind`).
 *
 * È una colonna e non tre tabelle, quindi ricerca, categorie, colore, 📌 e foto
 * valgono uguale per note, liste e diari. Una scheda di tipo sconosciuto — o
 * nata prima della colonna — è una [NOTA], che è il `DEFAULT` del database.
 */
enum class TipoScheda(val chiave: String, val icona: String, val etichetta: String) {
    NOTA("nota", "📄", "Nota"),
    LISTA("lista", "☑️", "Lista"),
    DIARIO("diario", "📊", "Diario");

    /** Come `KIND_TITLES` nel web: il nome della Tab. */
    val plurale: String
        get() = when (this) {
            NOTA -> "Note"
            LISTA -> "Liste"
            DIARIO -> "Diari"
        }

    /** «Nuova nota», «Nuova lista», «Nuovo diario». */
    val nuova: String
        get() = when (this) {
            NOTA -> "Nuova nota"
            LISTA -> "Nuova lista"
            DIARIO -> "Nuovo diario"
        }

    companion object {
        fun da(valore: String?): TipoScheda =
            entries.firstOrNull { it.chiave == valore } ?: NOTA

        /**
         * Come [da], ma distingue «tipo che non conosco» da «tipo che non c'è».
         *
         * ⚠️ Serve a non far collassare su [NOTA] un tipo che il web ha e qui
         * non c'è ancora — oggi `'link'`. Mostrata come nota, quella scheda
         * perderebbe il suo indirizzo (che sta in `mm_attachments`, letta solo
         * di là) e **risalvandola il form riscriverebbe `kind: "nota"`**: la
         * scheda link diventerebbe una nota in silenzio, e l'allegato
         * resterebbe agganciato a una riga che non lo mostra più.
         *
         * Torna `null` in quel caso, e la scheda non viene proprio caricata:
         * non vederla è meglio che vederla sbagliata e poterla rovinare.
         * Chiave assente o vuota resta [NOTA] — è il `DEFAULT` del database,
         * cioè le schede nate prima della colonna.
         */
        fun daONull(valore: String?): TipoScheda? =
            if (valore.isNullOrBlank()) NOTA
            else entries.firstOrNull { it.chiave == valore }
    }
}

/** Una scheda di Memo. `contenuto` è **HTML**, come lo scrive il web. */
data class MmScheda(
    val id: String,
    val titolo: String,
    val contenuto: String,
    val tipo: TipoScheda,
    val riservato: Boolean,
    val fissata: Boolean,
    val colore: String,
    val creata: String?,
    val aggiornata: String?,
    val categorie: List<String>,
    val immagini: Int,
    /** Voci di una lista: quante in tutto e quante spuntate. */
    val vociTotali: Int = 0,
    val vociFatte: Int = 0,
    /** Registrazioni di un diario, e la data dell'ultima. */
    val registrazioni: Int = 0,
    val ultimaRegistrazione: String? = null,
) {
    /** Il testo senza tag, per l'anteprima nella scheda e per la ricerca. */
    val anteprima: String get() = MemoHtml.aTestoSemplice(contenuto)

    val dataItaliana: String get() = dataOra(aggiornata ?: creata)

    /** La barra di avanzamento della lista, 0..1. Zero voci = niente barra. */
    val avanzamento: Float?
        get() = if (tipo == TipoScheda.LISTA && vociTotali > 0)
            vociFatte.toFloat() / vociTotali else null

    companion object {
        fun da(o: JsonObject): MmScheda? {
            val id = testo(o, "id") ?: return null
            val voci = (o["mm_list_items"] as? JsonArray).orEmpty()
            val registrazioni = (o["mm_diary_entries"] as? JsonArray).orEmpty()
            return MmScheda(
                id = id,
                titolo = testo(o, "title").orEmpty(),
                contenuto = testo(o, "content").orEmpty(),
                // un tipo che questa implementazione non conosce (oggi 'link')
                // si salta invece di diventare una nota — vedi daONull
                tipo = TipoScheda.daONull(testo(o, "kind")) ?: return null,
                riservato = testo(o, "riservato")?.toBooleanStrictOrNull() ?: false,
                fissata = testo(o, "pinned")?.toBooleanStrictOrNull() ?: false,
                colore = testo(o, "color")?.takeIf { it.isNotBlank() } ?: BIANCO,
                creata = testo(o, "created_at"),
                aggiornata = testo(o, "updated_at"),
                categorie = (o["mm_card_categories"] as? JsonArray)
                    ?.mapNotNull { (it as? JsonObject)?.let { r -> testo(r, "category_id") } }
                    .orEmpty(),
                immagini = (o["mm_images"] as? JsonArray)?.size ?: 0,
                vociTotali = voci.size,
                vociFatte = voci.count {
                    (it as? JsonObject)?.let { r -> testo(r, "done")?.toBooleanStrictOrNull() } == true
                },
                registrazioni = registrazioni.size,
                ultimaRegistrazione = registrazioni
                    .mapNotNull { (it as? JsonObject)?.let { r -> testo(r, "entry_date") } }
                    .maxOrNull(),
            )
        }

        const val BIANCO = "#FFFFFF"

        /** Gli stessi sette campioni della tavolozza di `memo.html`. */
        val COLORI = listOf(
            BIANCO to "Bianco",
            "#FEF9C3" to "Giallo",
            "#DCFCE7" to "Verde",
            "#DBEAFE" to "Blu",
            "#FAE8FF" to "Viola",
            "#FFE4E6" to "Rosa",
            "#FED7AA" to "Arancione",
        )
    }
}

/** Una voce di lista (`mm_list_items`). */
data class MmVoce(
    val id: String,
    val testo: String,
    val fatta: Boolean,
    val posizione: Int,
    val fattaIl: String?,
) {
    companion object {
        fun da(o: JsonObject): MmVoce? {
            val id = testo(o, "id") ?: return null
            return MmVoce(
                id = id,
                testo = testo(o, "text").orEmpty(),
                fatta = testo(o, "done")?.toBooleanStrictOrNull() ?: false,
                posizione = testo(o, "position")?.toIntOrNull() ?: 0,
                fattaIl = testo(o, "done_at"),
            )
        }
    }
}

/** Che cosa chiede una misura di diario (`mm_diary_metrics.kind`). */
enum class TipoMisura(val chiave: String, val etichetta: String) {
    SCALA("scala", "Scala"),
    NUMERO("numero", "Numero"),
    BOOL("bool", "Sì/No"),
    SCELTA("scelta", "Scelta");

    companion object {
        fun da(valore: String?): TipoMisura =
            entries.firstOrNull { it.chiave == valore } ?: SCALA
    }
}

/** Un'opzione di una misura a scelta: l'**id** è quello che si archivia. */
data class MmOpzione(val id: String, val etichetta: String)

/**
 * Una misura di un diario (`mm_diary_metrics`).
 *
 * ⚠️ Le misure sono righe vere e i valori no, ed è il motivo per cui non si
 * cancellano per ricrearle: le registrazioni le citano per id dentro
 * `measures`, e ricreandole tutto lo storico resterebbe senza nome.
 */
data class MmMisura(
    val id: String,
    val nome: String,
    val tipo: TipoMisura,
    val minimo: Double?,
    val massimo: Double?,
    val unita: String,
    val opzioni: List<MmOpzione>,
    val nota: String,
    val posizione: Int,
) {
    /** La riga sotto il nome nel riepilogo: «scala 1-20», «numero in kg»… */
    val descrizione: String
        get() = when (tipo) {
            TipoMisura.SCALA -> "scala ${numero(minimo)}-${numero(massimo)}"
            TipoMisura.NUMERO -> if (unita.isBlank()) "numero" else "numero in $unita"
            TipoMisura.BOOL -> "sì / no"
            TipoMisura.SCELTA -> "scelta fra ${opzioni.size} opzioni"
        }

    /**
     * Il valore archiviato come numero — i sì/no valgono 1 e 0.
     *
     * ⚠️ Una **scelta torna `null`**, come `numericValue()` nel web: l'ordine
     * delle opzioni è un elenco, non una scala, e farne una media o una
     * spezzata vorrebbe dire trattare la quarta opzione come il doppio della
     * seconda.
     */
    fun numerico(grezzo: JsonPrimitive?): Double? {
        if (grezzo == null || grezzo is JsonNull) return null
        if (tipo == TipoMisura.SCELTA) return null
        grezzo.content.toBooleanStrictOrNull()?.let { return if (it) 1.0 else 0.0 }
        return grezzo.content.toDoubleOrNull()
    }

    /**
     * Il nome dell'opzione scelta. Un'opzione tolta dopo lascia il suo id nelle
     * registrazioni: si dice che è stata tolta invece di far sparire il valore.
     */
    fun etichettaOpzione(id: String?): String =
        opzioni.firstOrNull { it.id == id }?.etichetta ?: "opzione tolta"

    /** Che cosa scrivere per un valore archiviato, qualunque sia il tipo. */
    fun etichettaValore(grezzo: JsonPrimitive?): String {
        if (grezzo == null || grezzo is JsonNull) return "—"
        if (tipo == TipoMisura.SCELTA) return etichettaOpzione(grezzo.content)
        return mostra(numerico(grezzo))
    }

    /** Come `displayValue()`: «7/10», «sì», «72,5 kg». */
    fun mostra(valore: Double?): String {
        if (valore == null) return "—"
        return when (tipo) {
            TipoMisura.BOOL -> if (valore != 0.0) "sì" else "no"
            TipoMisura.SCALA -> "${numero(valore)}/${numero(massimo)}"
            TipoMisura.SCELTA -> "—"
            TipoMisura.NUMERO -> numero(valore) + if (unita.isBlank()) "" else " $unita"
        }
    }

    companion object {
        fun da(o: JsonObject): MmMisura? {
            val id = testo(o, "id") ?: return null
            return MmMisura(
                id = id,
                nome = testo(o, "name").orEmpty(),
                tipo = TipoMisura.da(testo(o, "kind")),
                minimo = testo(o, "min_value")?.toDoubleOrNull(),
                massimo = testo(o, "max_value")?.toDoubleOrNull(),
                unita = testo(o, "unit").orEmpty(),
                opzioni = (o["options"] as? JsonArray).orEmpty().mapNotNull { voce ->
                    val riga = voce as? JsonObject ?: return@mapNotNull null
                    val oid = testo(riga, "id") ?: return@mapNotNull null
                    MmOpzione(oid, testo(riga, "label").orEmpty())
                },
                nota = testo(o, "hint").orEmpty(),
                posizione = testo(o, "position")?.toIntOrNull() ?: 0,
            )
        }
    }
}

/**
 * Una registrazione di diario (`mm_diary_entries`).
 *
 * ⚠️ In `misure` c'è **solo quello che è stato misurato davvero**: una misura
 * non toccata non compare come zero, la sua chiave non c'è proprio. «Non l'ho
 * misurata» e «vale zero» sono due cose diverse.
 */
data class MmRegistrazione(
    val id: String,
    val titolo: String,
    val data: String,
    val nota: String,
    val misure: Map<String, JsonPrimitive>,
) {
    companion object {
        fun da(o: JsonObject): MmRegistrazione? {
            val id = testo(o, "id") ?: return null
            return MmRegistrazione(
                id = id,
                titolo = testo(o, "title").orEmpty(),
                data = testo(o, "entry_date").orEmpty(),
                nota = testo(o, "note").orEmpty(),
                misure = (o["measures"] as? JsonObject).orEmpty()
                    .mapNotNull { (k, v) -> (v as? JsonPrimitive)?.let { k to it } }
                    .toMap(),
            )
        }
    }
}

/** Una foto allegata a una scheda (`mm_images` + il file nel bucket). */
data class MmImmagine(
    val id: String,
    val percorso: String,
    val nome: String,
    val mime: String?,
    /** URL firmato, riempito al caricamento: il bucket è privato. */
    val url: String = "",
) {
    companion object {
        fun da(o: JsonObject): MmImmagine? {
            val id = testo(o, "id") ?: return null
            return MmImmagine(
                id = id,
                percorso = testo(o, "storage_path").orEmpty(),
                nome = testo(o, "file_name").orEmpty(),
                mime = testo(o, "mime_type"),
            )
        }
    }
}

/** Una categoria condivisa (`cm_categories`), la stessa tabella di Tasks. */
data class CmCategoria(
    val id: String,
    val nome: String,
    val icona: String,
    val colore: String,
) {
    val etichetta: String get() = if (icona.isBlank()) nome else "$icona $nome"

    companion object {
        fun da(o: JsonObject): CmCategoria? {
            val id = testo(o, "id") ?: return null
            return CmCategoria(
                id = id,
                nome = testo(o, "name").orEmpty(),
                icona = testo(o, "icon").orEmpty(),
                colore = testo(o, "color")?.takeIf { it.isNotBlank() } ?: "#6B7280",
            )
        }
    }
}

internal fun testo(o: JsonObject, chiave: String): String? =
    (o[chiave] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content

/**
 * Un numero come lo scrive `fmtNum()` nel web: intero senza decimali, altrimenti
 * arrotondato al centesimo. Serve perché una scala 1-10 non si legga «7.0».
 */
internal fun numero(n: Double?): String {
    if (n == null || !n.isFinite()) return "—"
    return if (n == Math.floor(n)) n.toLong().toString()
    else (Math.round(n * 100) / 100.0).toString()
}

/** Una data `YYYY-MM-DD` in europeo, come `fmtDay()`. */
internal fun giorno(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    val p = iso.take(10).split('-')
    return if (p.size == 3) "${p[2]}/${p[1]}/${p[0]}" else iso
}

/**
 * Data e ora nel fuso del telefono, come `fmtDate()` nel web.
 *
 * Se non si legge si mostra il valore grezzo: una data storta è meglio di una
 * scheda che non compare.
 */
private fun dataOra(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    val formato = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
    return runCatching {
        OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).format(formato)
    }.recoverCatching {
        LocalDateTime.parse(iso).format(formato)
    }.getOrDefault(iso)
}
