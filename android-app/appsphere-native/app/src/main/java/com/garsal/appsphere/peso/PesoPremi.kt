package com.garsal.appsphere.peso

import android.content.Context
import androidx.annotation.DrawableRes
import com.garsal.appsphere.R
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Un premio cibo — gemello di `PRIZES` in `weight-quest.html`. Le foto sono
 * le stesse di `risorse/premi/*.jpg`, bundlate come drawable perché qui non
 * c'è una pagina statica da cui caricarle.
 */
data class PremioCibo(
    val id: String,
    val emoji: String,
    val nome: String,
    @DrawableRes val immagine: Int,
    val messaggio: String,
)

val PREMI_CIBO = listOf(
    PremioCibo(
        "torta_savoia", "🍰", "Torta Savoia", R.drawable.torta,
        "Sette strati di gloria, e te li sei sudati tutti.",
    ),
    PremioCibo(
        "cannolo", "🥐", "Cannolo", R.drawable.cannolo,
        "Ricotta e scorza croccante. Uno, però — non tre.",
    ),
    PremioCibo(
        "pizza", "🍕", "Pizza", R.drawable.pizza,
        "Stasera si esce: la pizza è già pagata dalla bilancia.",
    ),
    PremioCibo(
        "cioccolata", "🍫", "Tavoletta di cioccolata", R.drawable.cioccolata,
        "Una tavoletta intera, quadretto dopo quadretto.",
    ),
    PremioCibo(
        "biscotti", "🍪", "Tazza di biscotti", R.drawable.biscotti,
        "Tazza piena e biscotti a volontà: inzuppa senza fretta.",
    ),
)

fun premioDa(id: String): PremioCibo? = PREMI_CIBO.firstOrNull { it.id == id }

data class PremioVinto(val id: String, val data: String)

/**
 * Premio cibo alle soglie di peso — «gratta e vinci» dei traguardi
 * intermedi, gemello di quanto vive in `localStorage` in
 * `weight-quest.html` (`wq_mpts_<id>`, `wq_prizes_<id>`).
 *
 * ⚠️ **Per dispositivo, di proposito**: non c'è nessuna tabella su Supabase
 * per questo — stessa scelta già presa sul web (vedi `getPrizesKey` /
 * `getMilestonePtsKey`) — e aprirne una vorrebbe dire sincronizzare fra i
 * telefoni un dato che non lo merita. Chi apre l'app da un altro telefono si
 * ritrova la stellina di nuovo da estrarre, come sul web.
 */
object PesoPremi {
    private const val PREFS = "peso_premi"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun puntiTotali(context: Context, obiettivoId: String): Int =
        prefs(context).getInt("wq_mpts_$obiettivoId", 0)

    fun salvaPuntiTotali(context: Context, obiettivoId: String, punti: Int) {
        prefs(context).edit().putInt("wq_mpts_$obiettivoId", punti).apply()
    }

    /** Soglia (kg) → premio già grattato per quella soglia, se c'è. */
    fun premiVinti(context: Context, obiettivoId: String): Map<Int, PremioVinto> {
        val testo = prefs(context).getString("wq_prizes_$obiettivoId", null) ?: return emptyMap()
        val obj = runCatching { Json.parseToJsonElement(testo).jsonObject }.getOrNull() ?: return emptyMap()
        return obj.entries.mapNotNull { (chiave, valore) ->
            val soglia = chiave.toIntOrNull() ?: return@mapNotNull null
            val voce = valore as? JsonObject ?: return@mapNotNull null
            val id = voce["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val data = voce["date"]?.jsonPrimitive?.content ?: ""
            soglia to PremioVinto(id, data)
        }.toMap()
    }

    fun salvaPremio(context: Context, obiettivoId: String, soglia: Int, premioId: String, data: String) {
        val chiave = "wq_prizes_$obiettivoId"
        val p = prefs(context)
        val esistenti = runCatching {
            Json.parseToJsonElement(p.getString(chiave, null) ?: "{}").jsonObject
        }.getOrDefault(JsonObject(emptyMap()))
        val aggiornato = buildJsonObject {
            esistenti.entries.forEach { (k, v) -> put(k, v) }
            put(soglia.toString(), buildJsonObject {
                put("id", premioId)
                put("date", data)
            })
        }
        p.edit().putString(chiave, aggiornato.toString()).apply()
    }
}
