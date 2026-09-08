package com.garsal.tandem

import org.json.JSONArray
import org.json.JSONObject

/** Le categorie di spesa di un giro in bici. Vivono qui e non nel database:
 *  aggiungerne una è una riga di Kotlin, non una migration — è la stessa
 *  scelta di `COVERAGE_ITEMS` e `INCOME_SECTIONS` in Finanza. `categoria` è
 *  testo libero lato database proprio per questo. */
data class Categoria(val id: String, val emoji: String, val nome: String)

val CATEGORIE = listOf(
    Categoria("cibo", "🍝", "Mangiare"),
    Categoria("bar", "☕", "Bar e caffè"),
    Categoria("alloggio", "🛏️", "Dormire"),
    Categoria("trasporto", "🚆", "Treni e trasporti"),
    Categoria("bici", "🔧", "Bici e officina"),
    Categoria("visite", "🎟️", "Visite e ingressi"),
    Categoria("varie", "🛒", "Varie"),
)

/** Una categoria che non è più in elenco non fa sparire la voce che la cita:
 *  si mostra com'è scritta, come la *misura tolta* dei diari di Memo. */
fun categoriaDi(id: String): Categoria =
    CATEGORIE.firstOrNull { it.id == id } ?: Categoria(id, "🏷️", id)

object Stati {
    const val IN_ATTESA = "in_attesa"
    const val CONFERMATA = "confermata"
    const val CANC_RICHIESTA = "cancellazione_richiesta"
    const val CANCELLATA = "cancellata"
}

data class Persona(val id: String, val nome: String)

data class ViaggioInfo(
    val id: String,
    val nome: String,
    val codice: String,
    val dataInizio: String?,
    val dataFine: String?,
    val valuta: String,
)

data class Voce(
    val id: String,
    val tipo: String,               // 'spesa' | 'restituzione'
    val importo: Double,
    val data: String,               // yyyy-MM-dd
    val descrizione: String,
    val categoria: String,
    val daId: String,
    val daNome: String,
    val perChi: String?,            // 'entrambi' | 'uno' | null (restituzione)
    val beneficiarioId: String?,
    val beneficiarioNome: String?,
    val haScontrino: Boolean,
    val scontrinoLetto: Double?,
    val stato: String,
    val creataDa: String,
    val creataDaNome: String,
    val cancChiestaDa: String?,
    val cancMotivo: String?,
) {
    val spesa get() = tipo == "spesa"
    val viva get() = stato != Stati.CANCELLATA
}

data class RigaLog(
    val id: Long,
    val azione: String,
    val chi: String,
    val voceTesto: String,
    val voceImporto: Double?,
    val dettaglio: String,
    val quando: String,
)

data class StatoViaggio(
    val viaggio: ViaggioInfo,
    val io: Persona,
    val altro: Persona?,
    val voci: List<Voce>,
    val log: List<RigaLog>,
    val saldo: Double,
    val saldoAtteso: Double,
    val totaleViaggio: Double,
    val daConfermare: Int,
)

/* ───────────────────────────────────────────────────────────────────────────
   La lettura del JSON.

   ⚠️ Si legge campo per campo con `opt*` e non con una data class serializzata:
   una colonna di forma inattesa non deve dare una voce storta ma **la schermata
   vuota**. È la stessa scelta di `ts_tasks` e `ps_weight_tracking` nel nativo.
   ─────────────────────────────────────────────────────────────────────────── */

private fun JSONObject.stringaONull(k: String): String? =
    if (isNull(k)) null else optString(k).ifBlank { null }

private fun JSONObject.numeroONull(k: String): Double? =
    if (isNull(k)) null else optString(k).toDoubleOrNull() ?: optDouble(k).takeIf { !it.isNaN() }

fun personaDa(o: JSONObject?): Persona? =
    if (o == null) null else Persona(o.optString("id"), o.optString("nome"))

fun voceDa(o: JSONObject) = Voce(
    id = o.optString("id"),
    tipo = o.optString("tipo", "spesa"),
    importo = o.numeroONull("importo") ?: 0.0,
    data = o.optString("data"),
    descrizione = o.optString("descrizione", ""),
    categoria = o.optString("categoria", "varie"),
    daId = o.optString("da_id"),
    daNome = o.optString("da_nome", ""),
    perChi = o.stringaONull("per_chi"),
    beneficiarioId = o.stringaONull("beneficiario_id"),
    beneficiarioNome = o.stringaONull("beneficiario_nome"),
    haScontrino = o.stringaONull("scontrino_path") != null,
    scontrinoLetto = o.numeroONull("scontrino_letto"),
    stato = o.optString("stato", Stati.IN_ATTESA),
    creataDa = o.optString("creata_da"),
    creataDaNome = o.optString("creata_da_nome", ""),
    cancChiestaDa = o.stringaONull("canc_chiesta_da"),
    cancMotivo = o.stringaONull("canc_motivo"),
)

fun logDa(o: JSONObject) = RigaLog(
    id = o.optLong("id"),
    azione = o.optString("azione"),
    chi = o.optString("chi_nome", ""),
    voceTesto = o.optString("voce_testo", ""),
    voceImporto = o.numeroONull("voce_importo"),
    dettaglio = o.optString("dettaglio", ""),
    quando = o.optString("at"),
)

fun statoDa(o: JSONObject): StatoViaggio? {
    val v = o.optJSONObject("viaggio") ?: return null
    val io = personaDa(o.optJSONObject("io")) ?: return null
    fun <T> lista(nome: String, f: (JSONObject) -> T): List<T> {
        val arr: JSONArray = o.optJSONArray(nome) ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(f) }
    }
    return StatoViaggio(
        viaggio = ViaggioInfo(
            id = v.optString("id"), nome = v.optString("nome"), codice = v.optString("codice"),
            dataInizio = v.stringaONull("data_inizio"), dataFine = v.stringaONull("data_fine"),
            valuta = v.optString("valuta", "EUR"),
        ),
        io = io,
        altro = personaDa(o.optJSONObject("altro")),
        voci = lista("voci", ::voceDa),
        log = lista("log", ::logDa),
        saldo = o.numeroONull("saldo") ?: 0.0,
        saldoAtteso = o.numeroONull("saldo_atteso") ?: 0.0,
        totaleViaggio = o.numeroONull("totale_viaggio") ?: 0.0,
        daConfermare = o.optInt("da_confermare", 0),
    )
}
