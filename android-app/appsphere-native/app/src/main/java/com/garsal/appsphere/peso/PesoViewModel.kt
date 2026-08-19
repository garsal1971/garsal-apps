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
    /** Quello che si sta guardando: di partenza il più recente. */
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

/**
 * Cosa risponde [PesoViewModel.preparaChiusura] — `closeObjective()` nel web,
 * dove è un `alert()` che blocca o un `confirm()` col conto dei punti.
 */
sealed class EsitoChiusura {
    data class Bloccata(val motivo: String) : EsitoChiusura()
    data class DaConfermare(val puntiGiornalieri: Int, val puntiChiusura: Int, val totale: Int) : EsitoChiusura()
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
                // primo giro si parte dal più recente — `obiettivi()` è già
                // ordinata per `created_at` decrescente, quindi è il primo
                // della lista, chiuso o no.
                val scelto = _state.value.obiettivoId?.takeIf { id -> obiettivi.any { it.id == id } }
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

    // ── Gestione obiettivi ────────────────────────────────────────────────

    /**
     * Crea o aggiorna un obiettivo — `saveObjective()`. Dopo il salvataggio lo
     * riseleziona: è quello che si vuole vedere subito, nuovo o appena
     * modificato che sia.
     */
    fun salvaObiettivo(id: String?, bozza: BozzaObiettivo) {
        viewModelScope.launch {
            try {
                val riga = PesoRepository.rigaObiettivo(bozza)
                val nuovoId = PesoRepository.salvaObiettivo(id, riga)
                _state.value = _state.value.copy(
                    obiettivoId = nuovoId,
                    messaggio = "💾 Obiettivo «${bozza.nome.trim()}» ${if (id == null) "creato" else "aggiornato"}",
                )
                carica()
            } catch (e: Exception) {
                Log.w(TAG, "obiettivo non salvato", e)
                _state.value = _state.value.copy(messaggio = "Non salvato: ${e.message ?: "connessione assente"}")
            }
        }
    }

    /**
     * I controlli di `closeObjective()` prima del `confirm()`: bloccata se
     * l'obiettivo è già chiuso o, per un successo, se il peso di oggi (o del
     * periodo, per «mantenere») non è ancora sceso sotto il target. Pura e
     * non sospesa: la chiama la UI a ogni tocco di Successo/Fallito, prima di
     * mostrare la conferma.
     */
    fun preparaChiusura(obiettivo: Obiettivo, nuovoStato: String): EsitoChiusura {
        if (!obiettivo.attivo) return EsitoChiusura.Bloccata("Questo obiettivo è già chiuso!")

        if (nuovoStato == "success") {
            val endW = obiettivo.pesoFinale
                ?: return EsitoChiusura.Bloccata(
                    "Obiettivo senza peso target valido: impossibile chiudere con SUCCESSO."
                )
            if (obiettivo.tipo == "mantenere") {
                val massimo = PesoRegole.massimoNelPeriodo(_state.value.pesate, obiettivo.inizio, obiettivo.fine)
                    ?: return EsitoChiusura.Bloccata(
                        "Non puoi chiudere con SUCCESSO: nessun peso registrato nel periodo " +
                            "(${dataItaliana(obiettivo.inizio)} → ${dataItaliana(obiettivo.fine)})."
                    )
                if (massimo > endW) {
                    return EsitoChiusura.Bloccata(
                        "Non puoi chiudere con SUCCESSO: il massimo del periodo (${kg(massimo)} kg) " +
                            "supera il peso stabilito (${kg(endW)} kg)."
                    )
                }
            } else {
                val minimoOggi = PesoRegole.minimoStrettoOggi(_state.value.pesate)
                    ?: return EsitoChiusura.Bloccata("Non puoi chiudere con SUCCESSO: nessun peso registrato oggi.")
                if (minimoOggi > endW) {
                    return EsitoChiusura.Bloccata(
                        "Non puoi chiudere con SUCCESSO: il minimo di oggi (${kg(minimoOggi)} kg) " +
                            "supera l'obiettivo (${kg(endW)} kg)."
                    )
                }
            }
        }

        val puntiGiornalieri = _state.value.puntiOggi ?: 0
        val puntiChiusura = if (nuovoStato == "success") obiettivo.bonusFinale else -obiettivo.malusFinale
        return EsitoChiusura.DaConfermare(puntiGiornalieri, puntiChiusura, puntiGiornalieri + puntiChiusura)
    }

    /** Chiude l'obiettivo col punteggio già confermato dall'utente. */
    fun chiudiObiettivo(obiettivo: Obiettivo, nuovoStato: String, punteggioFinale: Int) {
        viewModelScope.launch {
            try {
                PesoRepository.chiudiObiettivo(obiettivo.id, nuovoStato, punteggioFinale)
                _state.value = _state.value.copy(messaggio = "Obiettivo chiuso! Punteggio finale: $punteggioFinale")
                carica()
            } catch (e: Exception) {
                Log.w(TAG, "chiusura non riuscita", e)
                _state.value = _state.value.copy(messaggio = "Non chiuso: ${e.message ?: "connessione assente"}")
            }
        }
    }

    /**
     * Cancella un obiettivo — sempre permesso, anche chiuso: `deleteObjective()`
     * non lo blocca mai, a differenza di Salva/Successo/Fallito.
     */
    fun eliminaObiettivo(obiettivo: Obiettivo) {
        viewModelScope.launch {
            try {
                PesoRepository.eliminaObiettivo(obiettivo.id)
                _state.value = _state.value.copy(
                    obiettivoId = _state.value.obiettivoId.takeUnless { it == obiettivo.id },
                    messaggio = "🗑 Obiettivo «${obiettivo.nome}» eliminato",
                )
                carica()
            } catch (e: Exception) {
                Log.w(TAG, "eliminazione obiettivo non riuscita", e)
                _state.value = _state.value.copy(messaggio = "Non eliminato: ${e.message ?: "connessione assente"}")
            }
        }
    }

    private companion object {
        const val TAG = "Peso"
    }
}
