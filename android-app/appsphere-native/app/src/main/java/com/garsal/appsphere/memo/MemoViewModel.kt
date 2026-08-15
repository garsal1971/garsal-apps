package com.garsal.appsphere.memo

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class Ordinamento(val etichetta: String) {
    AGGIORNATE("Ultime modificate"),
    CREATE("Ultime create"),
    TITOLO("Titolo"),
}

data class MemoState(
    val schede: List<MmScheda> = emptyList(),
    val categorie: List<CmCategoria> = emptyList(),
    val ricerca: String = "",
    val filtroCategoria: String? = null,
    val ordinamento: Ordinamento = Ordinamento.AGGIORNATE,
    val soloFissate: Boolean = false,
    /** Le foto già caricate, per scheda: si leggono solo quando serve. */
    val immagini: Map<String, List<MmImmagine>> = emptyMap(),
    val caricamento: Boolean = true,
    val errore: String? = null,
    val messaggio: String? = null,
) {
    /**
     * Le schede da mostrare — gli stessi tre passaggi di `getFilteredCards()`:
     * filtro per categoria, ricerca su titolo **e testo**, ordinamento.
     *
     * Le fissate restano in cima anche quando si ordina per titolo o per data
     * di creazione: sul web è l'ordine che arriva dal database, qui va
     * riscritto a mano perché l'ordinamento lo rifà il client.
     */
    val visibili: List<MmScheda>
        get() {
            var elenco = schede
            if (soloFissate) elenco = elenco.filter { it.fissata }
            filtroCategoria?.let { cat -> elenco = elenco.filter { cat in it.categorie } }

            val q = ricerca.trim().lowercase()
            if (q.isNotEmpty()) {
                elenco = elenco.filter {
                    it.titolo.lowercase().contains(q) || it.anteprima.lowercase().contains(q)
                }
            }

            val perOrdine: Comparator<MmScheda> = when (ordinamento) {
                Ordinamento.AGGIORNATE -> compareByDescending { it.aggiornata.orEmpty() }
                Ordinamento.CREATE -> compareByDescending { it.creata.orEmpty() }
                Ordinamento.TITOLO -> compareBy { it.titolo.lowercase() }
            }
            return elenco.sortedWith(compareByDescending<MmScheda> { it.fissata }.then(perOrdine))
        }

    fun categoria(id: String): CmCategoria? = categorie.firstOrNull { it.id == id }

    fun quante(categoriaId: String): Int = schede.count { categoriaId in it.categorie }
}

class MemoViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(MemoState())
    val state: StateFlow<MemoState> = _state.asStateFlow()

    init { carica() }

    fun carica() {
        viewModelScope.launch {
            _state.value = _state.value.copy(caricamento = true, errore = null)
            try {
                val schede = MemoRepository.schede()
                val categorie = MemoRepository.categorie()
                _state.value = _state.value.copy(
                    schede = schede,
                    categorie = categorie,
                    caricamento = false,
                )
            } catch (e: Exception) {
                Log.w(TAG, "caricamento schede fallito", e)
                _state.value = _state.value.copy(
                    caricamento = false,
                    errore = e.message ?: "Caricamento non riuscito",
                )
            }
        }
    }

    fun cerca(testo: String) { _state.value = _state.value.copy(ricerca = testo) }

    fun filtraCategoria(id: String?) { _state.value = _state.value.copy(filtroCategoria = id) }

    fun ordina(ordinamento: Ordinamento) {
        _state.value = _state.value.copy(ordinamento = ordinamento)
    }

    fun mostraSoloFissate(solo: Boolean) {
        _state.value = _state.value.copy(soloFissate = solo)
    }

    fun scartaMessaggi() { _state.value = _state.value.copy(errore = null, messaggio = null) }

    /**
     * Le foto di una scheda, chieste quando la si apre.
     *
     * Non si caricano insieme all'elenco: gli URL sono firmati e scadono in due
     * ore, e chiederli per tutte le schede a ogni apertura dell'app sarebbe una
     * richiesta per foto per niente. Nell'elenco basta il conteggio, che arriva
     * già con la scheda.
     */
    fun caricaImmagini(schedaId: String) {
        viewModelScope.launch {
            try {
                val foto = MemoRepository.immagini(schedaId)
                _state.value = _state.value.copy(
                    immagini = _state.value.immagini + (schedaId to foto),
                )
            } catch (e: Exception) {
                Log.w(TAG, "foto non caricate per $schedaId", e)
            }
        }
    }

    /**
     * Salva la scheda e **poi** le foto, come `saveCard()`: le foto nuove hanno
     * bisogno dell'id della scheda per il percorso nel bucket, quindi una
     * scheda nuova va scritta prima.
     *
     * Una foto che non si carica non fa fallire il salvataggio — il testo è già
     * al sicuro — ma lo dice, invece di sparire in silenzio.
     */
    fun salva(
        id: String?,
        bozza: BozzaScheda,
        fotoNuove: List<FotoInAttesa>,
        fotoDaTogliere: List<MmImmagine>,
        onFatto: () -> Unit,
    ) {
        viewModelScope.launch {
            try {
                val schedaId = MemoRepository.salva(
                    id = id,
                    titolo = bozza.titolo.trim(),
                    contenutoHtml = MemoHtml.aHtml(bozza.testo),
                    fissata = bozza.fissata,
                    colore = bozza.colore,
                    categorie = bozza.categorie,
                )

                fotoDaTogliere.forEach { foto ->
                    runCatching { MemoRepository.eliminaImmagine(foto) }
                        .onFailure { Log.w(TAG, "foto non eliminata: ${foto.id}", it) }
                }

                var fallite = 0
                fotoNuove.forEach { foto ->
                    runCatching {
                        val dati = MemoFoto.byte(getApplication(), foto.uri)
                        MemoRepository.caricaImmagine(schedaId, dati, foto.nome, foto.mime)
                    }.onFailure {
                        fallite++
                        Log.w(TAG, "foto non caricata: ${foto.nome}", it)
                    }
                }

                _state.value = _state.value.copy(
                    messaggio = if (fallite == 0) "✅ Scheda salvata"
                    else "Scheda salvata, ma $fallite foto non sono state caricate",
                    // Le foto in memoria sono quelle di prima: si buttano, e la
                    // scheda le richiede aprendosi. Tenerle mostrerebbe ancora
                    // quella appena tolta.
                    immagini = _state.value.immagini - schedaId,
                )
                onFatto()
                carica()
                caricaImmagini(schedaId)
            } catch (e: Exception) {
                Log.w(TAG, "salvataggio non riuscito", e)
                _state.value = _state.value.copy(errore = e.message ?: "Salvataggio non riuscito")
            }
        }
    }

    fun elimina(id: String, onFatto: () -> Unit) {
        viewModelScope.launch {
            try {
                MemoRepository.elimina(id)
                _state.value = _state.value.copy(
                    messaggio = "🗑 Scheda eliminata",
                    immagini = _state.value.immagini - id,
                )
                onFatto()
                carica()
            } catch (e: Exception) {
                Log.w(TAG, "eliminazione non riuscita", e)
                _state.value = _state.value.copy(errore = e.message ?: "Eliminazione non riuscita")
            }
        }
    }

    /** Fissa o libera una scheda dall'elenco, senza aprirla. */
    fun cambiaFissata(scheda: MmScheda) {
        viewModelScope.launch {
            try {
                MemoRepository.salva(
                    id = scheda.id,
                    titolo = scheda.titolo,
                    contenutoHtml = scheda.contenuto,
                    fissata = !scheda.fissata,
                    colore = scheda.colore,
                    categorie = scheda.categorie,
                )
                carica()
            } catch (e: Exception) {
                Log.w(TAG, "pin non cambiato", e)
                _state.value = _state.value.copy(errore = e.message ?: "Operazione non riuscita")
            }
        }
    }

    private companion object {
        const val TAG = "AppSphereMemo"
    }
}
