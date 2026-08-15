package com.garsal.appsphere.memo

import com.garsal.appsphere.core.AuthRepo
import com.garsal.appsphere.core.Supabase
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import kotlin.random.Random
import kotlin.time.Duration.Companion.hours

/**
 * Le schede di Memo, le loro categorie e le loro foto — le stesse tabelle di
 * `memo.html` e lo stesso ordine di operazioni.
 *
 * ⚠️ Due cose che **sono** la funzionalità e vanno cambiate nelle due
 * implementazioni insieme:
 *
 * - **le categorie si riscrivono da capo a ogni salvataggio** (cancella tutte
 *   le righe di `mm_card_categories` della scheda, poi reinserisce quelle
 *   scelte). È quello che fa `saveCard()`: senza la cancellazione una
 *   categoria tolta resterebbe attaccata alla scheda per sempre;
 * - **cancellando una scheda si cancellano prima i file dal bucket**, e solo
 *   dopo la riga. `mm_images` sparisce da sé (`ON DELETE CASCADE`), ma il
 *   bucket non sa niente di quel vincolo: nell'ordine inverso i file
 *   resterebbero lì senza più nessuna riga che dica dove sono.
 */
object MemoRepository {

    private const val BUCKET = "mm-images"

    private val db get() = Supabase.client().postgrest
    private val bucket get() = Supabase.client().storage.from(BUCKET)

    /**
     * Le schede con le categorie e il **conteggio** delle foto, in un colpo
     * solo come `loadCards()`: fissate prima, poi le più recenti.
     */
    suspend fun schede(): List<MmScheda> = withContext(Dispatchers.IO) {
        db.from("mm_cards")
            .select(Columns.raw("*,mm_card_categories(category_id),mm_images(id)")) {
                order("pinned", Order.DESCENDING)
                order("updated_at", Order.DESCENDING)
            }
            .decodeList<JsonObject>()
            .mapNotNull { MmScheda.da(it) }
    }

    /** Le categorie condivise, le stesse che usano Tasks e Habit Tracker. */
    suspend fun categorie(): List<CmCategoria> = withContext(Dispatchers.IO) {
        db.from("cm_categories")
            .select(Columns.raw("id,name,icon,color")) { order("name", Order.ASCENDING) }
            .decodeList<JsonObject>()
            .mapNotNull { CmCategoria.da(it) }
    }

    /**
     * Le foto di una scheda, ciascuna col suo URL firmato: il bucket è
     * privato, un URL diretto darebbe 400 a chiunque.
     *
     * Le due ore di validità sono quelle del web. Una firma scaduta non è un
     * dramma — si riapre la scheda e se ne chiede un'altra — ma vale la pena
     * ricordarsene se un giorno le foto smettono di comparire dopo che l'app è
     * rimasta aperta mezza giornata.
     */
    suspend fun immagini(schedaId: String): List<MmImmagine> = withContext(Dispatchers.IO) {
        db.from("mm_images")
            .select(Columns.raw("id,storage_path,file_name,mime_type")) {
                filter { eq("card_id", schedaId) }
                order("created_at", Order.ASCENDING)
            }
            .decodeList<JsonObject>()
            .mapNotNull { MmImmagine.da(it) }
            .map { img ->
                val url = runCatching { bucket.createSignedUrl(img.percorso, 2.hours) }
                    .getOrDefault("")
                img.copy(url = url)
            }
    }

    /**
     * Crea o aggiorna una scheda e riscrive le sue categorie. Torna l'id, che
     * per una scheda nuova serve subito dopo per caricarci le foto.
     */
    suspend fun salva(
        id: String?,
        titolo: String,
        contenutoHtml: String,
        fissata: Boolean,
        colore: String,
        categorie: List<String>,
    ): String = withContext(Dispatchers.IO) {
        val utente = AuthRepo.userId() ?: error("Sessione scaduta: rientra e riprova.")
        val adesso = Instant.now().toString()

        val riga = buildJsonObject {
            put("title", titolo)
            put("content", contenutoHtml)
            put("pinned", fissata)
            put("color", colore)
            put("updated_at", adesso)
            put("user_id", utente)
        }

        val schedaId = if (id != null) {
            db.from("mm_cards").update(riga) { filter { eq("id", id) } }
            id
        } else {
            val creata = buildJsonObject {
                riga.forEach { (k, v) -> put(k, v) }
                put("created_at", adesso)
            }
            db.from("mm_cards").insert(creata) { select(Columns.raw("id")) }
                .decodeList<JsonObject>()
                .firstOrNull()
                ?.let { testo(it, "id") }
                ?: error("La scheda non ha restituito un id.")
        }

        db.from("mm_card_categories").delete { filter { eq("card_id", schedaId) } }
        if (categorie.isNotEmpty()) {
            db.from("mm_card_categories").insert(
                categorie.map { catId ->
                    buildJsonObject {
                        put("card_id", schedaId)
                        put("category_id", catId)
                    }
                }
            )
        }

        schedaId
    }

    /** Cancella la scheda: prima i file nel bucket, poi la riga. */
    suspend fun elimina(schedaId: String) = withContext(Dispatchers.IO) {
        val percorsi = immagini(schedaId).map { it.percorso }.filter { it.isNotBlank() }
        if (percorsi.isNotEmpty()) runCatching { bucket.delete(percorsi) }
        db.from("mm_cards").delete { filter { eq("id", schedaId) } }
        Unit
    }

    /**
     * Carica una foto nel bucket e ne registra la riga.
     *
     * Il percorso è quello del web — `utente/scheda/istante_casuale.est` —
     * perché le policy dello Storage guardano la prima cartella per capire di
     * chi è il file.
     */
    suspend fun caricaImmagine(
        schedaId: String,
        dati: ByteArray,
        nomeFile: String,
        mime: String,
    ) = withContext(Dispatchers.IO) {
        val utente = AuthRepo.userId() ?: error("Sessione scaduta: rientra e riprova.")
        val estensione = nomeFile.substringAfterLast('.', "")
            .lowercase()
            .takeIf { it.isNotBlank() && it.length <= 5 }
            ?: mime.substringAfter('/', "jpg")
        val nome = "${System.currentTimeMillis()}_${Random.nextInt(0, 1_000_000)}.$estensione"
        val percorso = "$utente/$schedaId/$nome"

        bucket.upload(percorso, dati) { upsert = false }

        db.from("mm_images").insert(
            buildJsonObject {
                put("card_id", schedaId)
                put("user_id", utente)
                put("storage_path", percorso)
                put("file_name", nome)
                put("mime_type", mime)
            }
        )
        Unit
    }

    /** Toglie una foto: prima il file, poi la riga, come per la scheda. */
    suspend fun eliminaImmagine(immagine: MmImmagine) = withContext(Dispatchers.IO) {
        if (immagine.percorso.isNotBlank()) runCatching { bucket.delete(listOf(immagine.percorso)) }
        db.from("mm_images").delete { filter { eq("id", immagine.id) } }
        Unit
    }
}
