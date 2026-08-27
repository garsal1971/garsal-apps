package com.garsal.appsphere.abituati

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

data class AbituatiState(
    val abitudini: List<HbAbitudine> = emptyList(),
    val spunte: List<HbSpunta> = emptyList(),
    val archivio: List<HbArchiviato> = emptyList(),
    val categorie: List<HbCategoria> = emptyList(),
    /** Lo streak per abitudine, calcolato da `hb_streak`. */
    val streak: Map<String, Int> = emptyMap(),
    /** Le cerimonie che aspettano: stack vinti e game over. */
    val daFesteggiare: List<HbEsito> = emptyList(),
    val gameOver: List<HbEsito> = emptyList(),
    val caricamento: Boolean = true,
    val errore: String? = null,
    val messaggio: String? = null,
) {
    fun categoria(id: String?): HbCategoria? = id?.let { c -> categorie.firstOrNull { it.id == c } }

    fun streakDi(abitudine: HbAbitudine): Int = streak[abitudine.id] ?: 0

    /** Lo stato di un periodo: `completed`, `failed`, `skipped`, `missed` o null. */
    fun statoDi(abitudineId: String, giorno: LocalDate, orario: String? = null): String? {
        val g = giorno.toString()
        return spunte.firstOrNull {
            it.abitudineId == abitudineId && it.giorno == g &&
                (orario == null || it.orario == orario)
        }?.stato
    }

    /**
     * L'ultima spunta di un'abitudine.
     *
     * ⚠️ Le righe `missed` non contano: non le ha messe nessuno, le scrive la
     * riconciliazione sui giorni dovuti e mai spuntati. Prendendole per spunte,
     * «il giorno dopo l'ultima spunta» diventerebbe il giorno dopo
     * l'interruzione — cioè il punto in cui l'abitudine si era già fermata, non
     * quello in cui la si stava ancora seguendo. Stessa regola di
     * `lastCheckDateStr()` in `habit-tracker.html`.
     */
    fun ultimaSpunta(abitudineId: String): LocalDate? {
        // Il massimo si cerca a mano: `LocalDate` è `Comparable<ChronoLocalDate>`
        // e non `Comparable<LocalDate>`, quindi `maxOrNull()` qui non tornerebbe
        // un `LocalDate`.
        var ultima: LocalDate? = null
        for (spunta in spunte) {
            if (spunta.abitudineId != abitudineId || spunta.stato == "missed") continue
            val giorno = runCatching { LocalDate.parse(spunta.giorno) }.getOrNull() ?: continue
            if (ultima == null || giorno.isAfter(ultima)) ultima = giorno
        }
        return ultima
    }

    /**
     * La data da cui proporre la ripartenza di un'abitudine interrotta: **il
     * giorno dopo l'ultima spunta**, che è dove si era fermata davvero.
     *
     * Due limiti, entrambi necessari: senza nessuna spunta si ripiega su oggi
     * (non c'è un «dopo» di niente), e in nessun caso si va oltre oggi — un
     * `started_at` nel futuro è un'abitudine che non cade mai, quindi ripresa
     * oggi e muta fino a domani. La stessa `defaultResumeDateStr()` del web.
     */
    fun ripartenzaSuggerita(abitudineId: String, oggi: LocalDate = LocalDate.now()): LocalDate {
        val dopo = ultimaSpunta(abitudineId)?.plusDays(1) ?: return oggi
        return if (dopo.isAfter(oggi)) oggi else dopo
    }

    /** Le abitudini che oggi chiedono qualcosa, come la timeline del web. */
    fun diOggi(oggi: LocalDate = LocalDate.now()): List<HbAbitudine> =
        abitudini.filter { it.stato == "active" && it.cadeIl(oggi) }
}

class AbituatiViewModel : ViewModel() {

    private val _state = MutableStateFlow(AbituatiState())
    val state: StateFlow<AbituatiState> = _state.asStateFlow()

    init { carica() }

    /**
     * Il giro di apertura, nello stesso ordine del web: **prima** la
     * riconciliazione — giorni mancati, jolly, stack completati e scaduti —
     * e poi si legge, perché quello che si legge sia già a posto.
     *
     * La riconciliazione è una RPC sola (`hb_reconcile`), la stessa che chiama
     * `habit-tracker.html`: qui non si decide niente, si mostra quello che ha
     * deciso lei.
     */
    fun carica(oggi: LocalDate = LocalDate.now()) {
        viewModelScope.launch {
            _state.value = _state.value.copy(caricamento = true, errore = null)
            try {
                val (vinti, persi) = runCatching { AbituatiRepository.riconcilia(oggi) }
                    .onFailure { Log.w(TAG, "riconciliazione non riuscita", it) }
                    .getOrDefault(emptyList<HbEsito>() to emptyList())

                val abitudini = AbituatiRepository.abitudini()
                val spunte = AbituatiRepository.spunte()
                val archivio = AbituatiRepository.archivio()
                val categorie = AbituatiRepository.categorie()
                val streak = streakDi(abitudini, oggi)

                _state.value = _state.value.copy(
                    abitudini = abitudini,
                    spunte = spunte,
                    archivio = archivio,
                    categorie = categorie,
                    streak = streak,
                    daFesteggiare = _state.value.daFesteggiare + vinti,
                    gameOver = _state.value.gameOver + persi,
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

    private suspend fun streakDi(abitudini: List<HbAbitudine>, oggi: LocalDate): Map<String, Int> =
        coroutineScope {
            abitudini.map { a ->
                async { a.id to AbituatiRepository.streak(a.id, oggi) }
            }.awaitAll().toMap()
        }

    /**
     * Segna un periodo. La scrittura, i jolly e il conto dello streak li fa la
     * RPC; qui si guarda solo se ha alzato una delle due bandiere — obiettivo
     * raggiunto o jolly finiti — e in quel caso si richiama il giro di
     * riconciliazione, che è l'unico posto dove uno stack si chiude.
     */
    fun segna(
        abitudine: HbAbitudine,
        giorno: LocalDate,
        stato: String,
        orario: String? = null,
        oggi: LocalDate = LocalDate.now(),
    ) {
        viewModelScope.launch {
            try {
                val esito = AbituatiRepository.segna(abitudine.id, giorno, stato, orario, oggi)
                val ok = testo(esito, "ok")?.toBooleanStrictOrNull() ?: false
                if (!ok) {
                    _state.value = _state.value.copy(
                        errore = testo(esito, "error") ?: "Spunta non riuscita",
                    )
                    return@launch
                }
                carica(oggi)
            } catch (e: Exception) {
                Log.w(TAG, "spunta non riuscita", e)
                _state.value = _state.value.copy(errore = e.message ?: "Spunta non riuscita")
            }
        }
    }

    /** «Ricomincia» e «Interrompi» del game over. */
    fun chiudiStack(esito: HbEsito, nuovoInizio: LocalDate?, oggi: LocalDate = LocalDate.now()) {
        viewModelScope.launch {
            try {
                // Uno stack scaduto per calendario `hb_reconcile` l'ha già
                // archiviato ed eliminato: qui resta solo l'eventuale nuovo
                // ciclo, e chiamare la chiusura darebbe «abitudine non trovata».
                if (!esito.giaArchiviato) {
                    AbituatiRepository.chiudiStack(esito.abitudineId, nuovoInizio, oggi)
                }
                scartaGameOver(esito)
                carica(oggi)
            } catch (e: Exception) {
                Log.w(TAG, "chiusura stack non riuscita", e)
                _state.value = _state.value.copy(errore = e.message ?: "Chiusura non riuscita")
            }
        }
    }

    /** «Ricomincia» dopo uno stack vinto: l'abitudine c'è ancora, si clona. */
    fun riparti(esito: HbEsito, inizio: LocalDate, oggi: LocalDate = LocalDate.now()) {
        viewModelScope.launch {
            try {
                AbituatiRepository.nuovoCiclo(esito.abitudineId, inizio)
                scartaFesta(esito)
                carica(oggi)
            } catch (e: Exception) {
                Log.w(TAG, "nuovo ciclo non riuscito", e)
                _state.value = _state.value.copy(errore = e.message ?: "Nuovo ciclo non riuscito")
            }
        }
    }

    fun scartaFesta(esito: HbEsito) {
        _state.value = _state.value.copy(
            daFesteggiare = _state.value.daFesteggiare.filterNot { it.abitudineId == esito.abitudineId },
        )
    }

    fun scartaGameOver(esito: HbEsito) {
        _state.value = _state.value.copy(
            gameOver = _state.value.gameOver.filterNot { it.abitudineId == esito.abitudineId },
        )
    }

    /**
     * INTERROMPI e RIPRENDI, le due direzioni di `status` fra `active` e
     * `stopped`. Non passano da una RPC — come nel web sono un `update`
     * diretto: le RPC governano dove va la prossima occorrenza, non se
     * l'abitudine è in corso.
     */
    fun interrompi(abitudine: HbAbitudine, oggi: LocalDate = LocalDate.now()) {
        viewModelScope.launch {
            try {
                AbituatiRepository.interrompi(abitudine.id)
                carica(oggi)
            } catch (e: Exception) {
                Log.w(TAG, "interruzione non riuscita", e)
                _state.value = _state.value.copy(errore = e.message ?: "Interruzione non riuscita")
            }
        }
    }

    fun riprendi(abitudine: HbAbitudine, inizio: LocalDate, oggi: LocalDate = LocalDate.now()) {
        viewModelScope.launch {
            try {
                AbituatiRepository.riprendi(abitudine.id, inizio)
                carica(oggi)
            } catch (e: Exception) {
                Log.w(TAG, "ripresa non riuscita", e)
                _state.value = _state.value.copy(errore = e.message ?: "Ripresa non riuscita")
            }
        }
    }

    /**
     * Quanto costerebbe riprendere da una certa data. Il conto lo fa il
     * database, qui si passa solo la domanda: `null` vuol dire che non si è
     * potuto chiedere, e non «zero giorni».
     */
    suspend fun giorniDaRecuperare(
        abitudine: HbAbitudine,
        da: LocalDate,
        oggi: LocalDate = LocalDate.now(),
    ): Int? = AbituatiRepository.giorniDaRecuperare(abitudine.id, da, oggi)

    fun salva(id: String?, bozza: BozzaAbitudine, onFatto: () -> Unit) {
        viewModelScope.launch {
            try {
                AbituatiRepository.salva(id, bozza)
                onFatto()
                carica()
            } catch (e: Exception) {
                Log.w(TAG, "salvataggio non riuscito", e)
                _state.value = _state.value.copy(errore = e.message ?: "Salvataggio non riuscito")
            }
        }
    }

    fun elimina(id: String, onFatto: () -> Unit) {
        viewModelScope.launch {
            try {
                AbituatiRepository.elimina(id)
                onFatto()
                carica()
            } catch (e: Exception) {
                Log.w(TAG, "eliminazione non riuscita", e)
                _state.value = _state.value.copy(errore = e.message ?: "Eliminazione non riuscita")
            }
        }
    }

    fun scartaMessaggi() {
        _state.value = _state.value.copy(errore = null, messaggio = null)
    }

    private companion object {
        const val TAG = "AppSphereAbituati"
    }
}
