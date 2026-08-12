package com.garsal.appsphere.tasks

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

data class TasksState(
    val task: List<TsTask> = emptyList(),
    val categorie: List<CmCategoria> = emptyList(),
    val priorita: List<CmPriorita> = emptyList(),
    val caricamento: Boolean = true,
    val errore: String? = null,
    val messaggio: String? = null,
) {
    private val oggi: LocalDate get() = LocalDate.now()

    /**
     * Le quattro sezioni dell'elenco.
     *
     * «Oggi» è il giorno esatto, non «da oggi in giù»: è la stessa regola di
     * `isTaskDueToday()` nella pagina web, che confronta con `==` e non con
     * `<=` proprio per tenere separato quello che è in ritardo. I
     * `free_repeat` non hanno una data e stanno per conto loro, come lì.
     */
    val inRitardo: List<TsTask>
        get() = ordinati.filter { it.giornoDiRiferimento?.isBefore(oggi) == true }

    val diOggi: List<TsTask>
        get() = ordinati.filter { it.giornoDiRiferimento == oggi }

    val prossimi: List<TsTask>
        get() = ordinati.filter { it.giornoDiRiferimento?.isAfter(oggi) == true }

    val liberi: List<TsTask>
        get() = ordinati.filter { it.tipo == "free_repeat" }

    private val ordinati: List<TsTask>
        get() = task.sortedWith(
            compareBy({ it.giornoDiRiferimento ?: LocalDate.MAX }, { it.titolo.lowercase() })
        )

    fun categoriaDi(id: String): CmCategoria? = categorie.firstOrNull { it.id == id }

    fun prioritaDi(id: String?): CmPriorita? =
        id?.let { cercata -> priorita.firstOrNull { it.id == cercata } }
}

class TasksViewModel : ViewModel() {

    private val _state = MutableStateFlow(TasksState())
    val state: StateFlow<TasksState> = _state.asStateFlow()

    init { carica() }

    fun carica() {
        viewModelScope.launch {
            _state.value = _state.value.copy(caricamento = true, errore = null)
            try {
                _state.value = _state.value.copy(
                    task = TasksRepository.task(),
                    categorie = TasksRepository.categorie(),
                    priorita = TasksRepository.priorita(),
                    caricamento = false,
                )
            } catch (e: Exception) {
                Log.w(TAG, "caricamento fallito", e)
                _state.value = _state.value.copy(
                    caricamento = false,
                    errore = e.message ?: "Caricamento non riuscito",
                )
            }
        }
    }

    // ── Ciclo di vita ────────────────────────────────────────────────────
    //
    // Tre funzioni identiche a meno della RPC, e volutamente non accorpate in
    // un `quando(azione)` con un `when` dentro: sono i tre punti in cui questa
    // app tocca lo stato di un task, e si vogliono poter leggere uno per uno.
    // Nessuna calcola niente: chiama, guarda l'esito, ricarica.

    fun completa(id: String) = agisci("Completato") { TasksRepository.completa(id) }

    fun salta(id: String, giorni: Int) = agisci("Saltato") { TasksRepository.salta(id, giorni) }

    fun fallisci(id: String) = agisci("Segnato come fallito") { TasksRepository.fallisci(id) }

    private fun agisci(fatto: String, azione: suspend () -> TasksRepository.Esito) {
        viewModelScope.launch {
            try {
                val esito = azione()
                if (!esito.ok) {
                    _state.value = _state.value.copy(
                        messaggio = "Non riuscito: ${esito.errore ?: "il server ha detto no"}"
                    )
                    return@launch
                }
                val punti = esito.punti?.let { if (it >= 0) " (+$it)" else " ($it)" }.orEmpty()
                _state.value = _state.value.copy(messaggio = "$fatto$punti")
                // La prossima occorrenza la decide il server: l'unico modo di
                // sapere com'è rimasto il task è rileggerlo.
                ricaricaTask()
            } catch (e: Exception) {
                Log.w(TAG, "azione non riuscita", e)
                _state.value = _state.value.copy(
                    messaggio = "Non riuscito: ${e.message ?: "connessione assente"}"
                )
            }
        }
    }

    fun salva(bozza: BozzaTask, id: String?, poi: () -> Unit) {
        viewModelScope.launch {
            try {
                TasksRepository.salva(bozza, id)
                _state.value = _state.value.copy(
                    messaggio = if (id == null) "Task creato" else "Task aggiornato"
                )
                ricaricaTask()
                poi()
            } catch (e: Exception) {
                Log.w(TAG, "salvataggio non riuscito", e)
                _state.value = _state.value.copy(
                    messaggio = "Non salvato: ${e.message ?: "connessione assente"}"
                )
            }
        }
    }

    /**
     * Cancellazione ottimistica: il task sparisce subito e torna se il
     * database dice di no. Come le spunte di Spuntiamola, e per la stessa
     * ragione — una riga che resta lì mezzo secondo dopo che l'hai eliminata
     * fa premere due volte.
     */
    fun elimina(id: String) {
        viewModelScope.launch {
            val prima = _state.value.task
            _state.value = _state.value.copy(task = prima.filterNot { it.id == id })
            try {
                TasksRepository.elimina(id)
                _state.value = _state.value.copy(messaggio = "Task eliminato")
            } catch (e: Exception) {
                Log.w(TAG, "eliminazione non riuscita", e)
                _state.value = _state.value.copy(
                    task = prima,
                    messaggio = "Non eliminato: ${e.message ?: "connessione assente"}",
                )
            }
        }
    }

    private suspend fun ricaricaTask() {
        runCatching { TasksRepository.task() }
            .onSuccess { _state.value = _state.value.copy(task = it) }
            .onFailure { Log.w(TAG, "ricarica non riuscita", it) }
    }

    fun messaggioMostrato() {
        _state.value = _state.value.copy(messaggio = null)
    }

    private companion object {
        const val TAG = "Tasks"
    }
}
