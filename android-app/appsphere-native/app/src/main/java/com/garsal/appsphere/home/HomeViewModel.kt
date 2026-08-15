package com.garsal.appsphere.home

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.garsal.appsphere.premi.PremiRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.max

@Serializable
private data class BollaSalvata(
    val htmlFile: String,
    val nome: String,
    val descrizione: String,
    val punteggio: Int,
    val colore: String,
    val riservata: Boolean,
    val route: String,
)

data class HomeState(
    val bolle: List<Bolla> = emptyList(),
    val avvisi: List<Avviso> = emptyList(),
    val caricamento: Boolean = true,
    val errore: String? = null,
    val modalitaNascosta: Boolean = false,
    val avvisiChiusi: Boolean = false,
    /** Punti guadagnati: la somma dei punteggi di **tutte** le app attive. */
    val totaleLordo: Int = 0,
    /** Punti già spesi in premi (`cm_rewards_log`). */
    val puntiSpesi: Int = 0,
) {
    /**
     * Quello che il riquadro in basso mostra, e con cui si comprano i premi:
     * guadagnati meno spesi, mai sotto zero. È la stessa cifra di
     * `updateScorePanel()` nel web — se le due divergono, un premio comprabile
     * da una parte non lo è dall'altra.
     */
    val totaleNetto: Int get() = max(0, totaleLordo - puntiSpesi)
}

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    private val prefs = app.getSharedPreferences("appsphere", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    init {
        // Le bolle in cache si disegnano subito, prima ancora di parlare col
        // database: all'avvio si vede la home popolata invece di una rotella.
        // Poi i dati veri le sostituiscono.
        leggiCache()
        ricarica()
    }

    fun ricarica() {
        viewModelScope.launch {
            _state.value = _state.value.copy(caricamento = true, errore = null)
            try {
                val dati = HomeRepository.carica(_state.value.modalitaNascosta)
                val avvisi = HomeRepository.avvisi()
                val spesi = PremiRepository.puntiSpesi()
                _state.value = _state.value.copy(
                    bolle = dati.bolle,
                    avvisi = avvisi,
                    caricamento = false,
                    totaleLordo = dati.totaleLordo,
                    puntiSpesi = spesi,
                )
                salvaCache(dati.bolle, dati.totaleLordo, spesi)
            } catch (e: Exception) {
                Log.w("AppSphereHome", "caricamento home fallito", e)
                _state.value = _state.value.copy(
                    caricamento = false,
                    // Se in cache c'era qualcosa le bolle restano a schermo:
                    // meglio dei punteggi di ieri che una pagina vuota.
                    errore = e.message ?: "Caricamento non riuscito",
                )
            }
        }
    }

    /** Come sul web: vale per la sessione, non si ricorda al prossimo avvio. */
    fun cambiaModalitaNascosta() {
        _state.value = _state.value.copy(modalitaNascosta = !_state.value.modalitaNascosta)
        ricarica()
    }

    fun chiudiAvvisi() {
        _state.value = _state.value.copy(avvisiChiusi = true)
    }

    /**
     * Rilegge i soli punti spesi, al ritorno dal catalogo premi.
     *
     * I punteggi delle app non possono essere cambiati nel frattempo — di là
     * non si tocca nessuna app — e rifarli vorrebbe dire una `score_query` per
     * riga per vedere gli stessi numeri di un momento fa.
     */
    fun ricaricaPuntiSpesi() {
        viewModelScope.launch {
            val spesi = PremiRepository.puntiSpesi()
            _state.value = _state.value.copy(puntiSpesi = spesi)
            salvaCache(_state.value.bolle, _state.value.totaleLordo, spesi)
        }
    }

    private fun leggiCache() {
        val grezzo = prefs.getString(CHIAVE_CACHE, null) ?: return
        try {
            val bolle = json.decodeFromString<List<BollaSalvata>>(grezzo).map {
                Bolla(it.htmlFile, it.nome, it.descrizione, it.punteggio, it.colore, it.riservata, it.route)
            }
            // Una bolla in cache che nel frattempo non è più portata va buttata,
            // o si aprirebbe una rotta che non esiste più.
            val valide = bolle.filter { PortedApps.perHtmlFile.containsKey(it.htmlFile) }
            _state.value = _state.value.copy(
                bolle = valide,
                // Il totale si tiene in cache insieme alle bolle: mostrare il
                // solo lordo mentre i punti spesi arrivano darebbe un numero
                // più alto del vero, che poi cala sotto gli occhi.
                totaleLordo = prefs.getInt(CHIAVE_LORDO, 0),
                puntiSpesi = prefs.getInt(CHIAVE_SPESI, 0),
            )
        } catch (e: Exception) {
            Log.w("AppSphereHome", "cache bolle illeggibile, la butto: ${e.message}")
            prefs.edit().remove(CHIAVE_CACHE).apply()
        }
    }

    private fun salvaCache(bolle: List<Bolla>, lordo: Int, spesi: Int) {
        // Le riservate non finiscono in cache: al prossimo avvio la modalità
        // nascosta è spenta, e comparirebbero prima che qualcuno la riaccenda.
        val daSalvare = bolle.filterNot { it.riservata }.map {
            BollaSalvata(it.htmlFile, it.nome, it.descrizione, it.punteggio, it.colore, it.riservata, it.route)
        }
        try {
            prefs.edit()
                .putString(CHIAVE_CACHE, json.encodeToString(daSalvare))
                .putInt(CHIAVE_LORDO, lordo)
                .putInt(CHIAVE_SPESI, spesi)
                .apply()
        } catch (e: Exception) {
            Log.w("AppSphereHome", "cache bolle non salvata: ${e.message}")
        }
    }

    private companion object {
        const val CHIAVE_CACHE = "bolle_cache"
        const val CHIAVE_LORDO = "totale_lordo"
        const val CHIAVE_SPESI = "punti_spesi"
    }
}
