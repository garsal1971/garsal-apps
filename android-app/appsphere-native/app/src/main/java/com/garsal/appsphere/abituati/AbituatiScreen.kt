package com.garsal.appsphere.abituati

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.garsal.appsphere.core.GarsalTopBar
import com.garsal.appsphere.core.Palette
import com.garsal.appsphere.core.Pillola
import com.garsal.appsphere.core.RigaScorrevole
import com.garsal.appsphere.core.Tendina
import com.garsal.appsphere.core.coloreDaHex
import com.garsal.appsphere.core.larghezzaPulsanti
import java.time.LocalDate

/** Il nero della bolla di Abituati (`#1A1A1A`, uno dei cerchi olimpici). */
internal val NeroAbituati = Color(0xFF1A1A1A)

private enum class Vista(val etichetta: String) {
    OGGI("🎯 Oggi"),
    TUTTE("📋 Tutte"),
    ARCHIVIO("📦 Archivio"),
}

/**
 * Il filtro sullo stato di 📋 Tutte, le stesse tre voci della tendina del web.
 *
 * Parte da «Attive» come là: le interrotte sono memoria, non lavoro di oggi, e
 * un elenco che le mescola alle vive fa sembrare da spuntare qualcosa che è
 * fermo. Una scelta fra poche opzioni fisse è **sempre una tendina**, mai una
 * fila di pillole: coi caratteri di sistema grandi quelle andrebbero a capo o
 * si accorcerebbero (vedi `core/PulsantiTendine.kt`).
 */
private enum class FiltroStato(val etichetta: String, val vuoto: String) {
    ATTIVE("Attive", "Nessuna abitudine attiva. Creane una col +"),
    INTERROTTE("Interrotte", "Nessuna abitudine interrotta."),
    TUTTE("Tutte", "Nessuna abitudine. Creane una col +");

    fun tiene(a: HbAbitudine): Boolean = when (this) {
        ATTIVE -> a.stato == "active"
        INTERROTTE -> a.stato == "stopped"
        TUTTE -> true
    }
}

/**
 * Abituati in nativo: le abitudini di oggi da spuntare, l'elenco completo e
 * l'archivio degli stack finiti.
 *
 * ⚠️ **Nessuna regola vive qui.** Streak, jolly, chiusura degli stack e giorni
 * mancati stanno nelle RPC `hb_*` (`20260815120000_hb_regole_rpc.sql`), che
 * `habit-tracker.html` chiama esattamente come questa schermata: una regola
 * sola per tutt'e due, che è la ragione per cui quelle funzioni esistono. Qui
 * si disegna e si chiede.
 */
@Composable
fun AbituatiScreen(
    onIndietro: () -> Unit,
    vm: AbituatiViewModel = viewModel(),
) {
    val stato by vm.state.collectAsStateWithLifecycle()
    val oggi = LocalDate.now()

    var vista by remember { mutableStateOf(Vista.OGGI) }
    var filtro by remember { mutableStateOf(FiltroStato.ATTIVE) }
    var inCompilazione by remember { mutableStateOf<Pair<BozzaAbitudine, String?>?>(null) }
    var daEliminare by remember { mutableStateOf<HbAbitudine?>(null) }
    var daInterrompere by remember { mutableStateOf<HbAbitudine?>(null) }
    var daRiprendere by remember { mutableStateOf<HbAbitudine?>(null) }

    inCompilazione?.let { (bozza, id) ->
        AbituatiForm(
            bozzaIniziale = bozza,
            id = id,
            categorie = stato.categorie,
            onAnnulla = { inCompilazione = null },
            onSalva = { compilata -> vm.salva(id, compilata) { inCompilazione = null } },
        )
        return
    }

    Scaffold(
        topBar = {
            GarsalTopBar(
                titolo = "Abituati",
                onIndietro = onIndietro,
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { inCompilazione = BozzaAbitudine() to null },
                containerColor = NeroAbituati,
                contentColor = Palette.light,
            ) { Text("+", fontSize = 26.sp) }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            SelettoreVista(vista) { vista = it }

            stato.errore?.let { messaggio ->
                Text(
                    text = messaggio,
                    color = Palette.danger,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .clickable { vm.scartaMessaggi() },
                )
            }

            Box(Modifier.fillMaxSize()) {
                when {
                    stato.caricamento && stato.abitudini.isEmpty() ->
                        CircularProgressIndicator(
                            color = NeroAbituati,
                            modifier = Modifier.align(Alignment.Center),
                        )

                    vista == Vista.ARCHIVIO -> Archivio(stato.archivio)

                    else -> {
                        val elenco = if (vista == Vista.OGGI) stato.diOggi(oggi)
                        else stato.abitudini.filter { filtro.tiene(it) }

                        Column(Modifier.fillMaxSize()) {
                            if (vista == Vista.TUTTE) {
                                Box(Modifier.padding(horizontal = 12.dp)) {
                                    Tendina(
                                        etichetta = "Stato",
                                        scelto = filtro.etichetta,
                                        voci = FiltroStato.entries.map { it.name to it.etichetta },
                                    ) { scelta -> filtro = FiltroStato.valueOf(scelta) }
                                }
                            }

                            if (elenco.isEmpty()) {
                                Vuoto(
                                    icona = if (vista == Vista.OGGI) "🎯" else "📋",
                                    testo = if (vista == Vista.OGGI)
                                        "Oggi non c'è niente da spuntare."
                                    else
                                        filtro.vuoto,
                                )
                            } else {
                                LazyColumn(
                                    contentPadding = PaddingValues(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    items(elenco, key = { it.id }) { abitudine ->
                                        SchedaAbitudine(
                                            abitudine = abitudine,
                                            stato = stato,
                                            oggi = oggi,
                                            conSpunte = vista == Vista.OGGI,
                                            onSegna = { giorno, nuovo, orario ->
                                                vm.segna(abitudine, giorno, nuovo, orario, oggi)
                                            },
                                            onModifica = {
                                                inCompilazione = BozzaAbitudine.da(abitudine) to abitudine.id
                                            },
                                            onInterrompi = { daInterrompere = abitudine },
                                            onRiprendi = { daRiprendere = abitudine },
                                            onElimina = { daEliminare = abitudine },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Le cerimonie ────────────────────────────────────────────────────
    stato.daFesteggiare.firstOrNull()?.let { esito ->
        Festa(
            esito = esito,
            onRicomincia = { inizio -> vm.riparti(esito, inizio, oggi) },
            onChiudi = { vm.scartaFesta(esito) },
        )
    }

    stato.gameOver.firstOrNull()?.let { esito ->
        GameOver(
            esito = esito,
            onRicomincia = { inizio -> vm.chiudiStack(esito, inizio, oggi) },
            onInterrompi = { vm.chiudiStack(esito, null, oggi) },
        )
    }

    daInterrompere?.let { abitudine ->
        AlertDialog(
            onDismissRequest = { daInterrompere = null },
            title = { Text("Interrompere l'abitudine?") },
            text = {
                Text(
                    "«${abitudine.nome}» non viene eliminata e il punteggio non cambia: resta " +
                        "con tutte le sue spunte, ma esce da 🎯 Oggi.\n\n" +
                        "Da qui in poi non conta più giorni mancati, non consuma jolly e non può " +
                        "né vincere né fallire, finché non la riprendi.\n\n" +
                        "🔔 Il promemoria si cancella, e riprendendola andrà riscritto."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.interrompi(abitudine, oggi)
                    daInterrompere = null
                }) { Text("Interrompi", color = Palette.warning, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { daInterrompere = null }) {
                    Text("Annulla", color = Palette.muted)
                }
            },
        )
    }

    daRiprendere?.let { abitudine ->
        Riprendi(
            abitudine = abitudine,
            contaDaRecuperare = { da -> vm.giorniDaRecuperare(abitudine, da, oggi) },
            onRiprendi = { inizio ->
                vm.riprendi(abitudine, inizio, oggi)
                daRiprendere = null
            },
            onChiudi = { daRiprendere = null },
        )
    }

    daEliminare?.let { abitudine ->
        AlertDialog(
            onDismissRequest = { daEliminare = null },
            title = { Text("Eliminare l'abitudine?") },
            text = {
                Text(
                    "«${abitudine.nome}» e le sue spunte spariscono. " +
                        "Gli stack già archiviati restano."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.elimina(abitudine.id) { daEliminare = null }
                }) { Text("Elimina", color = Palette.danger, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { daEliminare = null }) {
                    Text("Annulla", color = Palette.muted)
                }
            },
        )
    }
}

@Composable
private fun SelettoreVista(scelta: Vista, onScegli: (Vista) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Vista.entries.forEach { v ->
            val attiva = v == scelta
            Text(
                text = v.etichetta,
                color = if (attiva) Palette.light else Palette.dark,
                fontWeight = if (attiva) FontWeight.Bold else FontWeight.Normal,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (attiva) NeroAbituati else Palette.inputBg)
                    .clickable { onScegli(v) }
                    .padding(horizontal = 10.dp, vertical = 10.dp),
            )
        }
    }
}

/**
 * La scheda di un'abitudine: nome, categoria, avanzamento verso l'obiettivo,
 * jolly rimasti e — nella vista di oggi — i pulsanti per segnare.
 *
 * Le abitudini a più orari hanno **una riga per orario**, come la timeline del
 * web: il jolly però lo conta il giorno, non la sessione, e quel conto lo fa la
 * RPC.
 */
@Composable
private fun SchedaAbitudine(
    abitudine: HbAbitudine,
    stato: AbituatiState,
    oggi: LocalDate,
    conSpunte: Boolean,
    onSegna: (LocalDate, String, String?) -> Unit,
    onModifica: () -> Unit,
    onInterrompi: () -> Unit,
    onRiprendi: () -> Unit,
    onElimina: () -> Unit,
) {
    val categoria = stato.categoria(abitudine.categoriaId)
    val streak = stato.streakDi(abitudine)
    val obiettivo = abitudine.obiettivo.coerceAtLeast(1)
    val jollyRimasti = (abitudine.jollyMassimi - abitudine.jollyUsati).coerceAtLeast(0)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Palette.cardBg),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = abitudine.nome,
                color = Palette.dark,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
            )

            categoria?.let { cat ->
                Text(
                    text = cat.etichetta,
                    color = Palette.light,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(coloreDaHex(cat.colore) ?: Palette.muted)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }

            if (abitudine.stato == "stopped") {
                Text(
                    text = "⏹ Interrotta",
                    color = Palette.dark,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Palette.warning.copy(alpha = 0.25f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }

            LinearProgressIndicator(
                progress = { (streak.toFloat() / obiettivo).coerceIn(0f, 1f) },
                color = NeroAbituati,
                trackColor = Palette.border,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "🔥 $streak di ${abitudine.obiettivo} · 🃏 $jollyRimasti jolly",
                color = Palette.muted,
                style = MaterialTheme.typography.bodyMedium,
            )

            if (conSpunte) {
                if (abitudine.aPiuOrari) {
                    abitudine.orari.sorted().forEach { orario ->
                        Text(
                            text = "🕑 $orario",
                            color = Palette.dark,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spunte(
                            statoOggi = stato.statoDi(abitudine.id, oggi, orario),
                            onSegna = { nuovo -> onSegna(oggi, nuovo, orario) },
                        )
                    }
                } else {
                    Spunte(
                        statoOggi = stato.statoDi(abitudine.id, oggi),
                        onSegna = { nuovo -> onSegna(oggi, nuovo, null) },
                    )
                }
            } else {
                // Una riga sola che scorre col dito, con le larghezze misurate
                // su **tutte** le etichette possibili — anche quelle che questa
                // scheda non mostra — o un pulsante condizionale farebbe
                // traballare la larghezza degli altri (`core/PulsantiTendine.kt`).
                val etichette = listOf("✏️ Modifica", "⏹ Interrompi", "▶️ Riprendi", "🗑 Elimina")
                val larghezza = larghezzaPulsanti(etichette)
                RigaScorrevole(Arrangement.spacedBy(8.dp)) {
                    if (abitudine.stato == "stopped") {
                        // Su un'abitudine ferma la modifica non si offre, come
                        // nel web: quello che serve è rimetterla in moto.
                        Pillola("▶️ Riprendi", Palette.success, larghezza, onRiprendi)
                    } else {
                        Pillola("✏️ Modifica", NeroAbituati, larghezza, onModifica)
                        Pillola("⏹ Interrompi", Palette.warning, larghezza, onInterrompi)
                    }
                    Pillola("🗑 Elimina", Palette.danger, larghezza, onElimina)
                }
            }
        }
    }
}

/**
 * I tre stati di un periodo. Toccare quello già segnato lo toglie — è il
 * `newState === 'none'` del web, e serve per correggere un tocco sbagliato
 * (togliendo un `failed` la RPC restituisce anche il jolly).
 */
@Composable
private fun Spunte(statoOggi: String?, onSegna: (String) -> Unit) {
    val opzioni = listOf(
        "completed" to "✅ Fatto",
        "failed" to "❌ Fallito",
        "skipped" to "⏭ Saltato",
    )
    val larghezza = larghezzaPulsanti(opzioni.map { it.second })
    RigaScorrevole(Arrangement.spacedBy(6.dp)) {
        opzioni.forEach { (valore, etichetta) ->
            val attivo = statoOggi == valore
            Text(
                text = etichetta,
                color = if (attivo) Palette.light else Palette.dark,
                fontWeight = if (attivo) FontWeight.Bold else FontWeight.Normal,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier
                    .width(larghezza)
                    .padding(vertical = 2.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        when {
                            !attivo -> Palette.inputBg
                            valore == "completed" -> Palette.success
                            valore == "failed" -> Palette.danger
                            else -> Palette.warning
                        }
                    )
                    .clickable { onSegna(if (attivo) "none" else valore) }
                    .padding(vertical = 10.dp),
            )
        }
    }
    if (statoOggi == "missed") {
        Text(
            text = "Segnato come mancato dal controllo dei giorni passati.",
            color = Palette.muted,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun Archivio(righe: List<HbArchiviato>) {
    if (righe.isEmpty()) {
        Vuoto("📦", "Nessuno stack in archivio.")
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(righe, key = { it.id }) { riga ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Palette.cardBg),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(text = riga.nome, color = Palette.dark, fontWeight = FontWeight.Bold)
                    Text(
                        text = riga.esito,
                        color = if (riga.punti >= 0) Palette.success else Palette.danger,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "${dataItaliana(riga.inizio)} → ${dataItaliana(riga.fine)} · " +
                            "🔥 ${riga.streak} · ✅ ${riga.completamenti} · ❌ ${riga.fallimenti}",
                        color = Palette.muted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = if (riga.punti >= 0) "+${riga.punti} punti" else "${riga.punti} punti",
                        color = if (riga.punti >= 0) Palette.success else Palette.danger,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

/** Lo stack vinto: il modale del web, con la stessa scelta — riparti o basta. */
@Composable
private fun Festa(esito: HbEsito, onRicomincia: (LocalDate) -> Unit, onChiudi: () -> Unit) {
    val context = LocalContext.current
    var inizio by remember(esito.abitudineId) { mutableStateOf(LocalDate.now()) }

    AlertDialog(
        onDismissRequest = onChiudi,
        title = { Text("🏆 Stack completato!") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("«${esito.nome}» — 🔥 ${esito.streak} di fila, +${esito.punti} punti.")
                Text(
                    text = "Nuovo ciclo dal ${dataItaliana(inizio)}",
                    color = Palette.muted,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Palette.inputBg)
                        .clickable { scegliData(context, inizio) { inizio = it } }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onRicomincia(inizio) }) {
                Text("Ricomincia", color = Palette.success, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onChiudi) { Text("Per ora basta", color = Palette.muted) }
        },
    )
}

/** Lo stack perso: jolly finiti o scadenza. */
@Composable
private fun GameOver(
    esito: HbEsito,
    onRicomincia: (LocalDate) -> Unit,
    onInterrompi: () -> Unit,
) {
    val context = LocalContext.current
    var inizio by remember(esito.abitudineId) { mutableStateOf(LocalDate.now()) }

    AlertDialog(
        onDismissRequest = onInterrompi,
        title = {
            Text(
                if (esito.motivo == "scadenza_calendario") "⏰ Stack scaduto"
                else "💀 Jolly esauriti"
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("«${esito.nome}» — 🔥 ${esito.streak}, ${esito.mancati} mancati.")
                Text(
                    text = "Nuovo ciclo dal ${dataItaliana(inizio)}",
                    color = Palette.muted,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Palette.inputBg)
                        .clickable { scegliData(context, inizio) { inizio = it } }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onRicomincia(inizio) }) {
                Text("Ricomincia", color = Palette.dark, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onInterrompi) { Text("Interrompi", color = Palette.danger) }
        },
    )
}

/**
 * RIPRENDI: rimette in moto un'abitudine interrotta, **da una data scelta**.
 *
 * ⚠️ La data è il punto della finestra. Riprendendo con `started_at` fermo a
 * quando l'abitudine era partita, il primo giro di `hb_reconcile` marcherebbe
 * `missed` ogni giorno passato dall'interruzione: i jolly finirebbero sul posto
 * e il game over scatterebbe prima ancora di rivedere la scheda. Quanto costi
 * la data scelta lo dice `hb_giorni_da_recuperare` — la stessa RPC che usa
 * `habit-tracker.html`, così l'avviso è lo stesso da tutt'e due le parti — e si
 * rilegge a ogni cambio di data.
 *
 * Quando i jolly non bastano il pulsante **non si blocca**: cambia scritta. È
 * una scelta legittima — si può voler ricominciare da lontano sapendo di
 * perdere — ma non deve succedere per distrazione.
 */
@Composable
private fun Riprendi(
    abitudine: HbAbitudine,
    contaDaRecuperare: suspend (LocalDate) -> Int?,
    onRiprendi: (LocalDate) -> Unit,
    onChiudi: () -> Unit,
) {
    val context = LocalContext.current
    val jolly = abitudine.jollyMassimi.coerceAtLeast(1)
    var inizio by remember(abitudine.id) { mutableStateOf(LocalDate.now()) }
    var mancati by remember(abitudine.id) { mutableStateOf<Int?>(null) }
    var inCorso by remember(abitudine.id) { mutableStateOf(true) }

    // Il conto si rifà a ogni data scelta; `LaunchedEffect` annulla da sé
    // quello di prima, quindi vince l'ultima scelta e non l'ultima risposta.
    LaunchedEffect(abitudine.id, inizio) {
        inCorso = true
        mancati = contaDaRecuperare(inizio)
        inCorso = false
    }

    val quanti = mancati
    val avviso = when {
        inCorso -> "⏳ Conto i giorni da recuperare…"
        quanti == null -> "⚠️ Non riesco a contare i giorni da recuperare: controlla la connessione."
        quanti == 0 -> "✅ Nessun giorno da recuperare: riparte pulita."
        quanti >= jolly ->
            "💀 Da questa data a oggi ci sono $quanti giorni senza spunta e i jolly sono $jolly: " +
                "riaperta così finisce subito in game over."
        else -> "⚠️ Da questa data a oggi ci sono $quanti giorni senza spunta: " +
            "consumerebbero $quanti jolly su $jolly."
    }
    val perso = quanti != null && quanti >= jolly

    AlertDialog(
        onDismissRequest = onChiudi,
        title = { Text("▶️ Riprendere l'abitudine?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("«${abitudine.nome}»")
                Text(
                    text = "Riparti dal ${dataItaliana(inizio)}",
                    color = Palette.dark,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Palette.inputBg)
                        .clickable { scegliData(context, inizio) { inizio = it } }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                )
                abitudine.giornoInizio?.let {
                    Text(
                        text = "Era partita il ${dataItaliana(it)}",
                        color = Palette.muted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(
                    text = avviso,
                    color = if (perso) Palette.danger else Palette.dark,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "I giorni prima di questa data non contano più: le spunte già fatte " +
                        "restano in archivio, ma streak, giorni fatti e jolly ripartono da qui.",
                    color = Palette.muted,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = "🔔 Il promemoria era stato cancellato all'interruzione: se lo rivuoi, " +
                        "riaprilo dal web.",
                    color = Palette.muted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onRiprendi(inizio) }) {
                Text(
                    text = if (perso) "Riprendi lo stesso" else "Riprendi",
                    color = if (perso) Palette.danger else Palette.success,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onChiudi) { Text("Annulla", color = Palette.muted) }
        },
    )
}

@Composable
private fun Vuoto(icona: String, testo: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = icona, fontSize = 40.sp)
        Text(text = testo, color = Palette.muted, modifier = Modifier.padding(top = 12.dp))
    }
}
