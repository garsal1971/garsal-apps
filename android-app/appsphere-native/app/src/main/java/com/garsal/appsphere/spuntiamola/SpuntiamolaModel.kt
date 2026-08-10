package com.garsal.appsphere.spuntiamola

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import java.time.DayOfWeek
import java.time.LocalDate

// ── Righe del database ────────────────────────────────────────────────────
// Gli stessi campi che scrive spuntiamola.html: le due implementazioni si
// alternano sulle stesse righe, quindi i nomi non si toccano.

@Serializable
data class SpSettings(
    @SerialName("user_id") val userId: String? = null,
    val goal: String = "Il traguardo",
    val emoji: String = "🎯",
    @SerialName("start_date") val startDate: String,
    @SerialName("end_date") val endDate: String,
    @SerialName("skip_weekend") val skipWeekend: Boolean = false,
)

@Serializable
data class SpCheck(
    @SerialName("user_id") val userId: String? = null,
    val day: String,
    val emoji: String = "✅",
)

@Serializable
data class SpKeyDay(
    @SerialName("user_id") val userId: String? = null,
    val day: String,
    val label: String = "",
)

@Serializable
data class SpStecca(
    val goal: String = "",
    val emoji: String = "🎯",
    @SerialName("start_date") val startDate: String = "",
    @SerialName("end_date") val endDate: String = "",
    @SerialName("skip_weekend") val skipWeekend: Boolean = false,
    @SerialName("total_days") val totalDays: Int = 0,
    @SerialName("done_days") val doneDays: Int = 0,
    @SerialName("final_emoji") val finalEmoji: String = "🏁",
    @SerialName("final_check_at") val finalCheckAt: String? = null,
    val satisfaction: Int = 50,
    val note: String = "",
    val checks: JsonObject? = null,
    @SerialName("key_days") val keyDays: JsonObject? = null,
    @SerialName("closed_at") val closedAt: String? = null,
)

/** La riga da inserire alla chiusura: `user_id` esplicito come fa il web. */
@Serializable
data class SpSteccaNuova(
    @SerialName("user_id") val userId: String,
    val goal: String,
    val emoji: String,
    @SerialName("start_date") val startDate: String,
    @SerialName("end_date") val endDate: String,
    @SerialName("skip_weekend") val skipWeekend: Boolean,
    @SerialName("total_days") val totalDays: Int,
    @SerialName("done_days") val doneDays: Int,
    @SerialName("final_emoji") val finalEmoji: String,
    @SerialName("final_check_at") val finalCheckAt: String?,
    val satisfaction: Int,
    val note: String,
    val checks: JsonObject,
    @SerialName("key_days") val keyDays: JsonObject,
)

// ── Frasi, emoji e messaggi ───────────────────────────────────────────────
// Copiati parola per parola da spuntiamola.html: sono il carattere dell'app,
// non un dettaglio di presentazione da reinventare.

val FRASI = listOf(
    "Grande! ...anche se non dipende da te. 😄",
    "E uno! Il traguardo trema. 😏",
    "Giorno archiviato. Prossimo!",
    "Un altro se n'è andato. Ciao ciao! 👋",
    "Bravo, hai aspettato benissimo. 🏅",
    "Complimenti: è passato un giorno. Merito tuo? Mah. 🤷",
    "Spuntato. La matematica non mente. 🧮",
    "Questo giorno non tornerà. Meglio così!",
    "Piano piano si arriva ovunque. 🐢",
    "Hai battuto il calendario. Di nuovo.",
    "Un giorno in meno, zero rimpianti.",
    "Il tempo passa, tu spunti. Squadra perfetta.",
    "Chi spunta, arriva. Regola numero uno. 📏",
    "Fatto! Ora puoi anche riposarti.",
    "Sei una macchina da spunte. 🤖",
    "Il calendario ti teme, ormai.",
    "Ancora uno? Ancora uno. 💪",
    "Segnato! La strada si accorcia.",
    "Oggi c'eri. È già tantissimo.",
    "Un mattoncino in più nel muro. 🧱",
    "Spunta fatta, coscienza a posto. 😌",
    "Il conto alla rovescia ringrazia.",
    "Costanza batte entusiasmo. Sempre. 🔁",
    "Nessuno ti toglie questo giorno.",
    "Eccellente. Continua così, campione! 🏆",
    "Un altro giorno domato. 🦁",
    "Questa spunta profuma di libertà. 🌬️",
    "Che soddisfazione, eh? 😎",
    "Piccolo passo, grande direzione. 🚶",
    "Spuntato con stile. ✨",
    "Fatto. Si torna a vivere. 🎈",
)

val EMOJI_SPUNTA = listOf(
    "✅", "🎉", "⭐", "🔥", "💪", "🚀", "🌟", "🏆", "😎", "🍀",
    "🎯", "⚡", "🥳", "👏", "💎", "🌈", "🦾", "🧨", "🎊", "☀️",
)

data class Traguardo(val perc: Int, val emoji: String, val testo: String)

val TRAGUARDI = listOf(
    Traguardo(25, "🌱", "Un quarto fatto! Il difficile è cominciare."),
    Traguardo(50, "⛰️", "Metà strada! Da qui si vede il traguardo."),
    Traguardo(75, "🔥", "Tre quarti! Ormai è in discesa."),
    Traguardo(100, "🏁", "FINITO! Li hai spuntati tutti. Campione!"),
)

data class Fascia(val max: Int, val testo: String)

val SODD_ETICHETTE = listOf(
    Fascia(15, "Per niente. 😔"),
    Fascia(30, "Poco."),
    Fascia(45, "Insomma…"),
    Fascia(60, "Nella media."),
    Fascia(75, "Bene! 🙂"),
    Fascia(90, "Molto bene! 😄"),
    Fascia(100, "Alla grande! 🤩"),
)

enum class TonoToast { NORMALE, GRANDE, CHIAVE, DOLCE }

data class MessaggioChiusura(
    val max: Int,
    val emoji: String,
    val tono: TonoToast,
    val testo: String,
)

val MSG_CHIUSURA = listOf(
    MessaggioChiusura(
        20, "🫂", TonoToast.DOLCE,
        "Non è andata come volevi. Però ci sei arrivato in fondo, e quello non te lo toglie nessuno."
    ),
    MessaggioChiusura(
        40, "💪", TonoToast.DOLCE,
        "Poteva andare meglio, è vero. Ma l'hai chiusa lo stesso: chiuderla era la parte difficile."
    ),
    MessaggioChiusura(
        60, "👍", TonoToast.GRANDE,
        "Nel complesso ci sta! Una stecca chiusa è una stecca chiusa."
    ),
    MessaggioChiusura(
        80, "🎉", TonoToast.GRANDE,
        "Bella soddisfazione! Te la sei guadagnata un giorno alla volta."
    ),
    MessaggioChiusura(
        95, "🏆", TonoToast.CHIAVE,
        "Grandissimo! Questa è di quelle da ricordare."
    ),
    MessaggioChiusura(
        100, "🎆", TonoToast.CHIAVE,
        "PERFETTA! Meglio di così non si poteva chiudere. Che spettacolo!"
    ),
)

fun etichettaSoddisfazione(valore: Int): String =
    (SODD_ETICHETTE.firstOrNull { valore <= it.max } ?: SODD_ETICHETTE.last()).testo

fun messaggioChiusura(valore: Int): MessaggioChiusura =
    MSG_CHIUSURA.firstOrNull { valore <= it.max } ?: MSG_CHIUSURA.last()

// ── Calcoli sul periodo ───────────────────────────────────────────────────

/**
 * I giorni che compongono il periodo, sabato e domenica esclusi se richiesto.
 * Come `giorniDelPeriodo()` sul web, guardia sul numero di giri compresa: un
 * periodo assurdo non deve poter bloccare l'app.
 */
fun giorniDelPeriodo(impostazioni: SpSettings?): List<LocalDate> {
    if (impostazioni == null) return emptyList()
    val inizio = runCatching { LocalDate.parse(impostazioni.startDate) }.getOrNull() ?: return emptyList()
    val fine = runCatching { LocalDate.parse(impostazioni.endDate) }.getOrNull() ?: return emptyList()
    if (fine < inizio) return emptyList()

    val giorni = mutableListOf<LocalDate>()
    var corrente = inizio
    var guardia = 0
    while (corrente <= fine && guardia < 4000) {
        val weekend = corrente.dayOfWeek == DayOfWeek.SATURDAY || corrente.dayOfWeek == DayOfWeek.SUNDAY
        if (!(impostazioni.skipWeekend && weekend)) giorni += corrente
        corrente = corrente.plusDays(1)
        guardia++
    }
    return giorni
}

/** Quanti giorni passati di fila sono spuntati, contando a ritroso da oggi. */
fun calcolaSerie(giorni: List<LocalDate>, spunte: Map<String, String>): Int {
    val oggi = LocalDate.now()
    val passati = giorni.filter { it <= oggi }
    var serie = 0
    for (giorno in passati.asReversed()) {
        if (spunte.containsKey(giorno.toString())) serie++ else break
    }
    return serie
}

/**
 * C'è ancora qualcosa da spuntare?
 * Il periodo è da chiudere quando sono tutti fatti *oppure* quando l'ultimo
 * giorno è passato: nel secondo caso restano delle caselle vuote, ed è giusto
 * così — la stecca si archivia com'è andata.
 */
fun steccaDaChiudere(giorni: List<LocalDate>, spunte: Map<String, String>, fine: String?): Boolean {
    if (giorni.isEmpty()) return false
    val tuttiFatti = giorni.all { spunte.containsKey(it.toString()) }
    val scaduta = fine?.let { runCatching { LocalDate.now() > LocalDate.parse(it) }.getOrDefault(false) } ?: false
    return tuttiFatti || scaduta
}
