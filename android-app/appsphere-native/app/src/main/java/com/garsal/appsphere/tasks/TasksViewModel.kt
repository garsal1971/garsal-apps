package com.garsal.appsphere.tasks

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Una riga del calendario: o un task vivo, o una cosa già fatta che vive solo
 * nello storico. Il calendario le tratta uguali, ma solo la prima si può
 * completare o saltare — sulla seconda non c'è più niente da fare.
 */
data class VoceGiorno(
    val chiave: String,
    val titolo: String,
    val completato: Boolean,
    val ora: Int,
    val task: TsTask?,
)

/** Un gruppo per categoria dentro una sezione, con la sua intestazione colorata. */
data class GruppoCategoria(
    val chiave: String,
    val etichetta: String,
    val colore: String?,
    val task: List<TsTask>,
)

data class TasksState(
    val task: List<TsTask> = emptyList(),
    val storico: List<TsStorico> = emptyList(),
    val categorie: List<CmCategoria> = emptyList(),
    val priorita: List<CmPriorita> = emptyList(),
    val impostazioni: Map<String, String> = emptyMap(),
    val caricamento: Boolean = true,
    val errore: String? = null,
    val messaggio: String? = null,
) {
    private val oggi: LocalDate get() = LocalDate.now()

    // ── Le sezioni della panoramica ──────────────────────────────────────
    //
    // Sono le stesse cinque di `renderDashboard()` in `tasks.html`, con gli
    // stessi filtri: quello che si vede qui e quello che si vede là devono
    // essere la stessa lista, o l'app diventa un secondo parere invece di una
    // seconda finestra sugli stessi task.
    //
    // Una differenza voluta: la data di un ricorrente qui ripiega su
    // `start_date` quando `next_occurrence_date` è vuota (`dataDiRiferimento`).
    // Nel web quel ripiego c'è solo in `isTaskDueToday`, quindi un ricorrente
    // senza prossima occorrenza e con partenza nel passato non compare in
    // nessuna sezione. Compare qui, fra gli scaduti: è un task che aspetta, e
    // il posto giusto per accorgersene è la panoramica.

    /** Quanti giorni in avanti guarda **PROSSIMI**: `dashboard_upcoming_days`. */
    val giorniProssimi: Int
        get() = impostazioni["dashboard_upcoming_days"]?.trim()?.toIntOrNull()?.coerceIn(1, 90) ?: 10

    /** I task che la panoramica mostra: gli stessi quattro stati, e in panoramica. */
    private val attivi: List<TsTask>
        get() = ordinati.filter { it.stato in TsTask.STATI_IN_CALENDARIO && it.inPanoramica }

    private val programmati: List<TsTask> get() = attivi.filter { it.tipo != "free_repeat" }

    val scaduti: List<TsTask>
        get() = programmati.filter { it.giornoDiRiferimento?.isBefore(oggi) == true }

    /**
     * «Oggi» è il giorno esatto, non «da oggi in giù»: è la stessa regola di
     * `isTaskDueToday()` nella pagina web, che confronta con `==` e non con
     * `<=` proprio per tenere separato quello che è scaduto.
     */
    val diOggi: List<TsTask>
        get() = programmati.filter { it.giornoDiRiferimento == oggi }

    val prossimi: List<TsTask>
        get() {
            val ultimo = oggi.plusDays(giorniProssimi.toLong())
            return programmati.filter { t ->
                val giorno = t.giornoDiRiferimento ?: return@filter false
                giorno.isAfter(oggi) && !giorno.isAfter(ultimo)
            }
        }

    val liberi: List<TsTask> get() = attivi.filter { it.tipo == "free_repeat" }

    /**
     * I `free_repeat`, raggruppati per categoria e **solo** per le categorie
     * con `show_in_dashboard`: sono task senza data, e senza un gruppo che li
     * chiami sarebbero un elenco alla rinfusa in fondo alla pagina.
     *
     * Può essere vuoto pur essendoci dei `free_repeat`: è il caso in cui
     * nessuna categoria è configurata per la dashboard, e la sezione lo dice
     * invece di sparire — sparendo, quei task non si vedrebbero da nessuna
     * parte e sembrerebbero cancellati.
     */
    val liberiPerCategoria: List<GruppoCategoria>
        get() = perCategoria(liberi, soloInDashboard = true)

    /**
     * `show_in_panoramica = false`: i task che l'utente ha tolto dalle sezioni
     * di sopra ma che restano leggibili in fondo, raggruppati per categoria.
     */
    val nascostiPerCategoria: List<GruppoCategoria>
        get() = perCategoria(
            ordinati.filter { it.stato in TsTask.STATI_IN_CALENDARIO && !it.inPanoramica },
            soloInDashboard = false,
        )

    val nascosti: List<TsTask>
        get() = task.filter { it.stato in TsTask.STATI_IN_CALENDARIO && !it.inPanoramica }

    val panoramicaVuota: Boolean
        get() = scaduti.isEmpty() && diOggi.isEmpty() && prossimi.isEmpty() &&
            liberi.isEmpty() && nascosti.isEmpty()

    /**
     * Raggruppa per categoria come fa il web: un task con due categorie
     * **compare in tutt'e due i gruppi**, e dentro il gruppo si ordina per
     * titolo. Chi non ha categorie finisce in «Senza categoria», tranne fra i
     * `free_repeat`, dove il web lo lascia proprio fuori.
     */
    private fun perCategoria(
        task: List<TsTask>,
        soloInDashboard: Boolean,
    ): List<GruppoCategoria> {
        val gruppi = linkedMapOf<String, MutableList<TsTask>>()
        task.forEach { t ->
            val sue = t.categorie.filter { id ->
                val categoria = categoriaDi(id)
                categoria != null && (!soloInDashboard || categoria.inDashboard)
            }
            if (sue.isEmpty()) {
                if (!soloInDashboard) gruppi.getOrPut(SENZA_CATEGORIA) { mutableListOf() }.add(t)
            } else {
                sue.forEach { gruppi.getOrPut(it) { mutableListOf() }.add(t) }
            }
        }
        return gruppi.map { (chiave, suoi) ->
            val categoria = categoriaDi(chiave)
            GruppoCategoria(
                chiave = chiave,
                etichetta = categoria?.etichetta ?: "Senza categoria",
                colore = categoria?.colore,
                task = suoi.sortedBy { it.titolo.lowercase() },
            )
        }
    }

    private val ordinati: List<TsTask>
        get() = task.sortedWith(
            compareBy({ it.giornoDiRiferimento ?: LocalDate.MAX }, { it.titolo.lowercase() })
        )

    /**
     * Cosa mostrare in un giorno del calendario: i task che ci cadono, più le
     * righe di storico completate quel giorno.
     *
     * Il web concatena le due liste senza guardarle (`[...activeTasks,
     * ...historicalTasks]`), quindi un task completato oggi la cui prossima
     * occorrenza è ancora oggi compare due volte. In una cella grande quanto
     * un dito quel doppione sembra un difetto, quindi qui lo storico salta i
     * task già presenti fra i vivi dello stesso giorno.
     */
    fun vociDel(giorno: LocalDate): List<VoceGiorno> {
        val vivi = task.filter { it.cadeIl(giorno) }
        val idVivi = vivi.mapTo(mutableSetOf()) { it.id }
        val fatti = storico
            .filter { giornoDa(it.completatoIl) == giorno && it.taskId !in idVivi }
            .map { riga ->
                VoceGiorno(
                    chiave = "storico-${riga.id}",
                    titolo = riga.titolo,
                    completato = true,
                    ora = oraDa(riga.completatoIl)?.take(2)?.toIntOrNull() ?: 0,
                    task = task.firstOrNull { it.id == riga.taskId },
                )
            }
        return vivi.map {
            VoceGiorno(
                chiave = "task-${it.id}",
                titolo = it.titolo,
                completato = it.completato,
                ora = it.oraDelGiorno,
                task = it,
            )
        } + fatti
    }

    fun categoriaDi(id: String): CmCategoria? = categorie.firstOrNull { it.id == id }

    fun prioritaDi(id: String?): CmPriorita? =
        id?.let { cercata -> priorita.firstOrNull { it.id == cercata } }

    private companion object {
        const val SENZA_CATEGORIA = "__senza_categoria__"
    }
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
                    // Lo storico serve al calendario per i giorni passati: se
                    // non arriva, il resto della schermata funziona lo stesso.
                    storico = runCatching { TasksRepository.storico() }.getOrDefault(emptyList()),
                    // Idem le impostazioni: senza, «Prossimi» guarda i dieci
                    // giorni di default invece di quelli configurati.
                    impostazioni = runCatching { TasksRepository.impostazioni() }
                        .getOrDefault(emptyMap()),
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
