package com.garsal.appsphere.peso

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

data class PesoState(
    val pesate: List<Pesata> = emptyList(),
    val obiettivi: List<Obiettivo> = emptyList(),
    /** Quello che si sta guardando: di partenza il primo attivo. */
    val obiettivoId: String? = null,
    val caricamento: Boolean = true,
    val errore: String? = null,
    val messaggio: String? = null,
) {
    val obiettivo: Obiettivo?
        get() = obiettivi.firstOrNull { it.id == obiettivoId }

    /** L'obiettivo che fa testo per i badge: solo se è ancora aperto. */
    private val inCorso: Obiettivo?
        get() = obiettivo?.takeIf { it.attivo && it.traguardi.size >= 2 }

    /**
     * La tabella giorno per giorno. `by lazy` e non un getter: la leggono la
     * scheda della tabella, il grafico e il badge dei punti, e ricalcolarla a
     * ogni lettura vorrebbe dire rifare l'interpolazione di un anno di giorni
     * a ogni ridisegno.
     */
    val righe: List<PesoRegole.RigaGiorno> by lazy { PesoRegole.tabella(pesate, obiettivo) }

    // ── I sei riquadri, nell'ordine in cui stanno nella pagina ───────────

    /** Minimo oggi — la pesata più bassa di oggi. */
    val minimoOggi: Double? get() = PesoRegole.minimoDiOggi(pesate)

    /** Target oggi — dove dovrebbe stare l'ago secondo la curva. */
    val targetOggi: Double?
        get() = inCorso?.let { PesoRegole.targetInterpolato(it.traguardi, LocalDate.now().toString()) }

    /** Mancano al target — quanto si è sopra (o sotto, col segno meno) oggi. */
    val mancanoAlTarget: Double?
        get() {
            val peso = minimoOggi ?: return null
            val target = targetOggi ?: return null
            return peso - target
        }

    /** Kg alla fine — la distanza dal peso previsto all'ultimo giorno. */
    val kgAllaFine: Double?
        get() {
            val obiettivo = inCorso ?: return null
            val peso = minimoOggi ?: return null
            val finale = PesoRegole.targetInterpolato(obiettivo.traguardi, obiettivo.fine) ?: return null
            return peso - finale
        }

    /**
     * Punteggio — la somma dei punteggi finali degli obiettivi **chiusi**,
     * come `updateDashboardStats()`: quelli ancora aperti non hanno un
     * punteggio finale e non ci entrano.
     */
    val punteggio: Int
        get() = obiettivi.filterNot { it.attivo }.sumOf { it.punteggioFinale ?: 0 }

    /**
     * Punti oggi — il cumulativo dell'obiettivo in corso fino a oggi.
     *
     * Il nome è quello del web e vuol dire «a che punto sei», non «quanto hai
     * guadagnato oggi»: è lo stesso numero dell'ultima riga della tabella.
     */
    val puntiOggi: Int?
        get() {
            if (inCorso == null) return null
            return righe.firstOrNull { it.cumulativo != null }?.cumulativo
        }

    /** Ci si è già pesati oggi? È la domanda che dà il nome all'app. */
    val pesatoOggi: Boolean
        get() = pesate.any { it.giorno == LocalDate.now().toString() }
}

class PesoViewModel : ViewModel() {

    private val _state = MutableStateFlow(PesoState())
    val state: StateFlow<PesoState> = _state.asStateFlow()

    init { carica() }

    fun carica() {
        viewModelScope.launch {
            _state.value = _state.value.copy(caricamento = true, errore = null)
            try {
                val obiettivi = PesoRepository.obiettivi()
                // L'obiettivo scelto si tiene fra un caricamento e l'altro; al
                // primo giro si parte dal primo aperto, e se non ce n'è
                // nessuno dal più recente — così la tabella non è mai vuota
                // per il solo fatto che l'ultimo obiettivo è stato chiuso.
                val scelto = _state.value.obiettivoId?.takeIf { id -> obiettivi.any { it.id == id } }
                    ?: obiettivi.firstOrNull { it.attivo }?.id
                    ?: obiettivi.firstOrNull()?.id

                _state.value = _state.value.copy(
                    pesate = PesoRepository.pesate(da = daQuando(obiettivi, scelto)),
                    obiettivi = obiettivi,
                    obiettivoId = scelto,
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

    /**
     * Da dove far partire la lettura delle pesate: l'inizio dell'obiettivo che
     * si sta guardando, meno un mese di respiro per la prima interpolazione.
     * Senza obiettivo si guarda indietro un anno e mezzo.
     *
     * Non è un dettaglio di prestazioni: la tabella e il cumulativo devono
     * poter vedere **tutti** i giorni dell'obiettivo, o il conto dei punti
     * comincerebbe a metà strada e non tornerebbe con quello del web.
     */
    private fun daQuando(obiettivi: List<Obiettivo>, scelto: String?): LocalDate {
        val obiettivo = obiettivi.firstOrNull { it.id == scelto }
        val inizio = PesoRegole.giornoDa(obiettivo?.inizio)
        return inizio?.minusMonths(1) ?: LocalDate.now().minusMonths(18)
    }

    fun scegliObiettivo(id: String) {
        _state.value = _state.value.copy(obiettivoId = id)
        // Un altro obiettivo è un altro periodo: le pesate vanno rilette, o la
        // sua tabella comincerebbe dal giorno in cui comincia quella di prima.
        carica()
    }

    /**
     * Segna la pesata.
     *
     * Il target che finisce nella riga è quello interpolato **oggi**, come fa
     * il web: si congela nella pesata e non si ricalcola più, così spostare i
     * traguardi domani non riscrive il giudizio sui giorni già passati.
     */
    fun pesati(giorno: LocalDate, ora: LocalTime, peso: Double) {
        viewModelScope.launch {
            val obiettivo = _state.value.obiettivo
            val target = obiettivo?.let { PesoRegole.targetInterpolato(it.traguardi, giorno.toString()) }
            try {
                PesoRepository.salvaPesata(giorno, ora, peso, target)
                _state.value = _state.value.copy(
                    messaggio = "⚖️ ${kg(peso)} kg segnati per il ${dataItaliana(giorno.toString())}"
                )
                ricaricaPesate()
            } catch (e: Exception) {
                Log.w(TAG, "pesata non salvata", e)
                _state.value = _state.value.copy(
                    messaggio = "Non salvata: ${e.message ?: "connessione assente"}"
                )
            }
        }
    }

    /**
     * Cancella una pesata scritta a mano. Ottimistica con rollback, come le
     * spunte di Spuntiamola: la riga sparisce subito e torna se il database
     * dice di no.
     */
    fun elimina(pesata: Pesata) {
        viewModelScope.launch {
            val prima = _state.value.pesate
            _state.value = _state.value.copy(
                pesate = prima.filterNot { it.timestamp == pesata.timestamp }
            )
            try {
                PesoRepository.eliminaPesata(pesata.timestamp)
                _state.value = _state.value.copy(messaggio = "🗑 Pesata eliminata")
            } catch (e: Exception) {
                Log.w(TAG, "eliminazione non riuscita", e)
                _state.value = _state.value.copy(
                    pesate = prima,
                    messaggio = "Non eliminata: ${e.message ?: "connessione assente"}",
                )
            }
        }
    }

    /** Le pesate di un giorno, per il dettaglio: la più leggera per prima. */
    fun pesateDel(giorno: String): List<Pesata> =
        _state.value.pesate.filter { it.giorno == giorno }.sortedBy { it.peso }

    private suspend fun ricaricaPesate() {
        val stato = _state.value
        runCatching { PesoRepository.pesate(da = daQuando(stato.obiettivi, stato.obiettivoId)) }
            .onSuccess { _state.value = _state.value.copy(pesate = it) }
            .onFailure { Log.w(TAG, "ricarica non riuscita", it) }
    }

    fun messaggioMostrato() {
        _state.value = _state.value.copy(messaggio = null)
    }

    private companion object {
        const val TAG = "Peso"
    }
}
