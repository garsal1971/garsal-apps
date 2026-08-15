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

/** Una scheda di Memo. `contenuto` è **HTML**, come lo scrive il web. */
data class MmScheda(
    val id: String,
    val titolo: String,
    val contenuto: String,
    val fissata: Boolean,
    val colore: String,
    val creata: String?,
    val aggiornata: String?,
    val categorie: List<String>,
    val immagini: Int,
) {
    /** Il testo senza tag, per l'anteprima nella scheda e per la ricerca. */
    val anteprima: String get() = MemoHtml.aTestoSemplice(contenuto)

    val dataItaliana: String get() = dataOra(aggiornata ?: creata)

    companion object {
        fun da(o: JsonObject): MmScheda? {
            val id = testo(o, "id") ?: return null
            return MmScheda(
                id = id,
                titolo = testo(o, "title").orEmpty(),
                contenuto = testo(o, "content").orEmpty(),
                fissata = testo(o, "pinned")?.toBooleanStrictOrNull() ?: false,
                colore = testo(o, "color")?.takeIf { it.isNotBlank() } ?: BIANCO,
                creata = testo(o, "created_at"),
                aggiornata = testo(o, "updated_at"),
                categorie = (o["mm_card_categories"] as? JsonArray)
                    ?.mapNotNull { (it as? JsonObject)?.let { r -> testo(r, "category_id") } }
                    .orEmpty(),
                immagini = (o["mm_images"] as? JsonArray)?.size ?: 0,
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
