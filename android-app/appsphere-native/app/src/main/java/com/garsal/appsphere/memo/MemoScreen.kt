package com.garsal.appsphere.memo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.garsal.appsphere.core.GarsalTopBar
import com.garsal.appsphere.core.Palette
import com.garsal.appsphere.core.coloreDaHex

/** Blu di Memo, il `--primary` della pagina. */
internal val BluMemo = Color(0xFF2563EB)

/**
 * Memo in nativo: le schede di `memo.html`, con ricerca, filtro per categoria,
 * ordinamento, dettaglio e modifica.
 *
 * ⚠️ Gemella di `memo.html`, sulle stesse tabelle `mm_*` e sulle stesse
 * categorie condivise `cm_categories`. Il punto dove le due implementazioni
 * possono divergere in silenzio è il **contenuto**, che è HTML: la conversione
 * andata e ritorno vive in [MemoHtml] ed è documentata lì.
 */
@Composable
fun MemoScreen(
    onIndietro: () -> Unit,
    vm: MemoViewModel = viewModel(),
) {
    val stato by vm.state.collectAsStateWithLifecycle()

    // Il dettaglio si tiene per id e non per copia della scheda: dopo un
    // salvataggio l'elenco si ricarica, e con la copia si continuerebbe a
    // leggere la versione di prima.
    var apertaId by remember { mutableStateOf<String?>(null) }
    var inCompilazione by remember { mutableStateOf<Pair<BozzaScheda, String?>?>(null) }

    val aperta = apertaId?.let { id -> stato.schede.firstOrNull { it.id == id } }

    inCompilazione?.let { (bozza, id) ->
        MemoForm(
            bozzaIniziale = bozza,
            id = id,
            immaginiEsistenti = id?.let { stato.immagini[it] }.orEmpty(),
            categorie = stato.categorie,
            onAnnulla = { inCompilazione = null },
            onSalva = { compilata, nuove, daTogliere ->
                vm.salva(id, compilata, nuove, daTogliere) { inCompilazione = null }
            },
        )
        return
    }

    if (aperta != null) {
        MemoDettaglio(
            scheda = aperta,
            immagini = stato.immagini[aperta.id].orEmpty(),
            categorie = stato.categorie,
            onIndietro = { apertaId = null },
            onModifica = { inCompilazione = BozzaScheda.da(aperta) to aperta.id },
            onFissa = { vm.cambiaFissata(aperta) },
            onElimina = { vm.elimina(aperta.id) { apertaId = null } },
            onChiediImmagini = { vm.caricaImmagini(aperta.id) },
        )
        return
    }

    Scaffold(
        topBar = {
            GarsalTopBar(
                titolo = "Memo",
                onIndietro = onIndietro,
                azioni = {
                    Text(
                        text = "${stato.visibili.size}",
                        color = Palette.light,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { inCompilazione = BozzaScheda() to null },
                containerColor = BluMemo,
                contentColor = Palette.light,
            ) { Text("+", fontSize = 26.sp) }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            OutlinedTextField(
                value = stato.ricerca,
                onValueChange = vm::cerca,
                label = { Text("Cerca nelle schede") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            )

            Filtri(stato, vm)

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
                val elenco = stato.visibili
                when {
                    stato.caricamento && stato.schede.isEmpty() ->
                        CircularProgressIndicator(
                            color = BluMemo,
                            modifier = Modifier.align(Alignment.Center),
                        )

                    elenco.isEmpty() -> Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(text = if (stato.ricerca.isBlank()) "📋" else "🔍", fontSize = 40.sp)
                        Text(
                            text = if (stato.ricerca.isBlank())
                                "Nessuna scheda. Creane una col +"
                            else
                                "Nessun risultato per «${stato.ricerca}»",
                            color = Palette.muted,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }

                    else -> LazyColumn(
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(elenco, key = { it.id }) { scheda ->
                            SchedaCard(
                                scheda = scheda,
                                categorie = stato.categorie,
                                onApri = { apertaId = scheda.id },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Filtri(stato: MemoState, vm: MemoViewModel) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Ordinamento e «solo in evidenza» — la tendina e la pagina 📌 del web,
        // qui due file di pulsanti che vanno a capo da sé: coi caratteri grandi
        // una riga sola si taglierebbe.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Ordinamento.entries.forEach { o ->
                Etichetta(
                    testo = o.etichetta,
                    attiva = stato.ordinamento == o,
                    onTocca = { vm.ordina(o) },
                )
            }
            Etichetta(
                testo = "📌 In evidenza",
                attiva = stato.soloFissate,
                onTocca = { vm.mostraSoloFissate(!stato.soloFissate) },
            )
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Etichetta(
                testo = "Tutte (${stato.schede.size})",
                attiva = stato.filtroCategoria == null,
                onTocca = { vm.filtraCategoria(null) },
            )
            stato.categorie.forEach { cat ->
                val quante = stato.quante(cat.id)
                if (quante > 0) {
                    Etichetta(
                        testo = "${cat.etichetta} ($quante)",
                        attiva = stato.filtroCategoria == cat.id,
                        colore = coloreDaHex(cat.colore) ?: BluMemo,
                        onTocca = {
                            vm.filtraCategoria(if (stato.filtroCategoria == cat.id) null else cat.id)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun Etichetta(
    testo: String,
    attiva: Boolean,
    colore: Color = BluMemo,
    onTocca: () -> Unit,
) {
    Text(
        text = testo,
        color = if (attiva) Palette.light else Palette.dark,
        fontWeight = if (attiva) FontWeight.Bold else FontWeight.Normal,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (attiva) colore else Palette.inputBg)
            .clickable(onClick = onTocca)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

/**
 * La scheda nell'elenco: striscia del colore a sinistra, titolo, un pezzo del
 * testo, le categorie, la data e i segni 📌 e 📷 — le stesse cose di
 * `renderCardTile()`.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SchedaCard(
    scheda: MmScheda,
    categorie: List<CmCategoria>,
    onApri: () -> Unit,
) {
    // Il colore della scheda tinge tutta la card, dove il web ne fa una
    // striscia a sinistra: là le schede stanno su una griglia bianca e la
    // striscia basta a distinguerle, qui sono una sotto l'altra a tutta
    // larghezza, e il colore pieno si vede scorrendo col pollice.
    val coloreScheda = coloreDaHex(scheda.colore) ?: Palette.cardBg
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onApri),
        colors = CardDefaults.cardColors(containerColor = coloreScheda),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = scheda.titolo.ifBlank { "Senza titolo" },
                    color = if (scheda.titolo.isBlank()) Palette.muted else Palette.dark,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
                if (scheda.anteprima.isNotBlank()) {
                    Text(
                        text = scheda.anteprima,
                        color = Palette.muted,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (scheda.categorie.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        scheda.categorie.forEach { id ->
                            categorie.firstOrNull { it.id == id }?.let { cat ->
                                Text(
                                    text = cat.etichetta,
                                    color = Palette.light,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier
                                        .padding(vertical = 2.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(coloreDaHex(cat.colore) ?: Palette.muted)
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                }
                Text(
                    text = buildString {
                        append(scheda.dataItaliana)
                        if (scheda.fissata) append("  ·  📌")
                        if (scheda.immagini > 0) append("  ·  📷 ${scheda.immagini}")
                    },
                    color = Palette.muted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
