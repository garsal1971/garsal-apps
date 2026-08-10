package com.garsal.appsphere.home

import android.util.Log
import com.garsal.appsphere.core.Supabase
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.DayOfWeek
import java.time.LocalDate

@Serializable
data class CmApp(
    val title: String = "",
    val description: String? = null,
    @SerialName("score_query") val scoreQuery: String? = null,
    @SerialName("html_file") val htmlFile: String? = null,
    val color: String? = null,
    val riservato: Boolean? = null,
)

/** Una bolla pronta da disegnare: il dato del DB più la rotta del registro. */
data class Bolla(
    val htmlFile: String,
    val nome: String,
    val descrizione: String,
    val punteggio: Int,
    val colore: String,
    val riservata: Boolean,
    val route: String,
)

data class Avviso(val testo: String, val route: String)

@Serializable
private data class SpSettingsAvviso(
    val goal: String = "",
    val emoji: String = "🎯",
    @SerialName("start_date") val startDate: String = "",
    @SerialName("end_date") val endDate: String = "",
    @SerialName("skip_weekend") val skipWeekend: Boolean = false,
)

@Serializable
private data class SpGiorno(val day: String = "", val label: String? = null)

object HomeRepository {

    private const val TAG = "AppSphereHome"

    /**
     * Le app da mostrare, con il punteggio.
     *
     * Stessa lettura di `loadApps()` in index.html — `cm_apps` attive, ordinate
     * per id, punteggio dalla RPC `run_score_query` con l'SQL scritto nella
     * riga — con in più l'incrocio col registro delle app portate.
     *
     * Il filtro su `riservato` è fatto qui e non nella query: le righe in gioco
     * sono una manciata, e la pagina web ha già la stessa strada come ripiego
     * per quando la colonna non c'è.
     */
    suspend fun bolle(modalitaNascosta: Boolean): List<Bolla> = withContext(Dispatchers.IO) {
        val righe = Supabase.client().postgrest
            .from("cm_apps")
            .select(
                Columns.raw("title,description,score_query,html_file,color,riservato")
            ) {
                filter { eq("active", true) }
                order("id", Order.ASCENDING)
            }
            .decodeList<CmApp>()

        val perFile = righe.associateBy { it.htmlFile }

        coroutineScope {
            PortedApps.perHtmlFile.map { (file, portata) ->
                async {
                    val riga = perFile[file]
                    val riservata = riga?.riservato == true
                    if (riservata && !modalitaNascosta) return@async null

                    Bolla(
                        htmlFile = file,
                        nome = riga?.title?.takeIf { it.isNotBlank() } ?: portata.titoloDiRipiego,
                        descrizione = riga?.description?.takeIf { !it.isNullOrBlank() }
                            ?: portata.descrizioneDiRipiego,
                        punteggio = punteggio(riga?.scoreQuery),
                        colore = riga?.color?.takeIf { !it.isNullOrBlank() }
                            ?: portata.coloreDiRipiego,
                        riservata = riservata,
                        route = portata.route,
                    )
                }
            }.mapNotNull { it.await() }
        }
    }

    /**
     * `run_score_query` è blindata su un solo account
     * (`20260611160000_rosa_readonly_access.sql`): per chiunque altro la
     * chiamata fallisce. Non è un errore da mostrare — il web fa lo stesso e
     * lascia la bolla al minimo — quindi si torna 0 e si annota nel log.
     */
    private suspend fun punteggio(sql: String?): Int {
        if (sql.isNullOrBlank()) return 0
        return try {
            // decodeAs pretende un tipo non nullable: se la funzione tornasse
            // null il decode lancia e si finisce nel catch qui sotto, che è
            // esattamente l'esito voluto (punteggio 0).
            Supabase.client().postgrest
                .rpc("run_score_query", buildJsonObject { put("query", sql) })
                .decodeAs<Int>()
        } catch (e: Exception) {
            Log.w(TAG, "punteggio non calcolato: ${e.message}")
            0
        }
    }

    /**
     * Gli avvisi della home.
     *
     * Sul web ne convergono sei (decisioni, task urgenti, totale portafogli,
     * Ta Firi, Spuntiamola, abitudini). Qui c'è solo Spuntiamola perché è
     * l'unica delle sei che porta a una schermata che esiste: un avviso che
     * non apre niente è peggio di nessun avviso. Le altre si aggiungono quando
     * le rispettive app diventano native.
     */
    suspend fun avvisi(): List<Avviso> = withContext(Dispatchers.IO) {
        avvisoSpuntiamola()?.let { listOf(it) } ?: emptyList()
    }

    private suspend fun avvisoSpuntiamola(): Avviso? = try {
        val db = Supabase.client().postgrest
        // Giorno locale, non UTC: alle undici di sera `toISOString()` darebbe
        // già il giorno dopo e l'avviso sparirebbe con la spunta ancora da fare.
        val oggi = LocalDate.now()
        val oggiStr = oggi.toString()

        val impostazioni = db.from("sp_settings")
            .select(Columns.raw("goal,emoji,start_date,end_date,skip_weekend")) {
                limit(1)
            }
            .decodeList<SpSettingsAvviso>()
            .firstOrNull()

        val fuoriPeriodo = impostazioni == null ||
            oggiStr < impostazioni.startDate ||
            oggiStr > impostazioni.endDate
        val weekendSaltato = impostazioni?.skipWeekend == true &&
            (oggi.dayOfWeek == DayOfWeek.SATURDAY || oggi.dayOfWeek == DayOfWeek.SUNDAY)

        when {
            impostazioni == null || fuoriPeriodo || weekendSaltato -> null
            else -> {
                val giaSpuntato = db.from("sp_checks")
                    .select(Columns.raw("day")) {
                        filter { eq("day", oggiStr) }
                        limit(1)
                    }
                    .decodeList<SpGiorno>()
                    .isNotEmpty()

                if (giaSpuntato) null else {
                    val chiave = db.from("sp_key_days")
                        .select(Columns.raw("day,label")) {
                            filter { eq("day", oggiStr) }
                            limit(1)
                        }
                        .decodeList<SpGiorno>()
                        .firstOrNull()

                    val testo = if (chiave != null) {
                        val etichetta = chiave.label?.takeIf { it.isNotBlank() }?.let { " — $it" } ?: ""
                        "⭐ Oggi è una giornata chiave$etichetta e non l'hai spuntata!"
                    } else {
                        "Oggi non l'hai ancora spuntato — ${impostazioni.emoji} ${impostazioni.goal}"
                    }
                    Avviso(testo, Route.SPUNTIAMOLA)
                }
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "avviso Spuntiamola non disponibile: ${e.message}")
        null
    }
}
