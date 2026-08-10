package com.garsal.appsphere.home

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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
)

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
                val bolle = HomeRepository.bolle(_state.value.modalitaNascosta)
                val avvisi = HomeRepository.avvisi()
                _state.value = _state.value.copy(
                    bolle = bolle,
                    avvisi = avvisi,
                    caricamento = false,
                )
                salvaCache(bolle)
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

    private fun leggiCache() {
        val grezzo = prefs.getString(CHIAVE_CACHE, null) ?: return
        try {
            val bolle = json.decodeFromString<List<BollaSalvata>>(grezzo).map {
                Bolla(it.htmlFile, it.nome, it.descrizione, it.punteggio, it.colore, it.riservata, it.route)
            }
            // Una bolla in cache che nel frattempo non è più portata va buttata,
            // o si aprirebbe una rotta che non esiste più.
            val valide = bolle.filter { PortedApps.perHtmlFile.containsKey(it.htmlFile) }
            _state.value = _state.value.copy(bolle = valide)
        } catch (e: Exception) {
            Log.w("AppSphereHome", "cache bolle illeggibile, la butto: ${e.message}")
            prefs.edit().remove(CHIAVE_CACHE).apply()
        }
    }

    private fun salvaCache(bolle: List<Bolla>) {
        // Le riservate non finiscono in cache: al prossimo avvio la modalità
        // nascosta è spenta, e comparirebbero prima che qualcuno la riaccenda.
        val daSalvare = bolle.filterNot { it.riservata }.map {
            BollaSalvata(it.htmlFile, it.nome, it.descrizione, it.punteggio, it.colore, it.riservata, it.route)
        }
        try {
            prefs.edit().putString(CHIAVE_CACHE, json.encodeToString(daSalvare)).apply()
        } catch (e: Exception) {
            Log.w("AppSphereHome", "cache bolle non salvata: ${e.message}")
        }
    }

    private companion object {
        const val CHIAVE_CACHE = "bolle_cache"
    }
}
