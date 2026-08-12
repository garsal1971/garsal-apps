package com.garsal.appsphere.tasks

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.garsal.appsphere.core.GarsalTopBar
import com.garsal.appsphere.core.Palette
import com.garsal.appsphere.core.coloreDaHex
import java.time.LocalDate

@Composable
fun TasksScreen(
    onIndietro: () -> Unit,
    vm: TasksViewModel = viewModel(),
) {
    val stato by vm.state.collectAsStateWithLifecycle()

    // `null` = form chiuso. `BozzaTask?` dentro l'apertura distingue il nuovo
    // (bozza vuota, id nullo) dalla modifica (bozza riempita, id del task).
    var inModifica by remember { mutableStateOf<Pair<BozzaTask, String?>?>(null) }
    var azioniSu by remember { mutableStateOf<TsTask?>(null) }
    var daEliminare by remember { mutableStateOf<TsTask?>(null) }
    var daSaltare by remember { mutableStateOf<TsTask?>(null) }

    inModifica?.let { (bozza, id) ->
        TaskForm(
            bozzaIniziale = bozza,
            id = id,
            categorie = stato.categorie,
            priorita = stato.priorita,
            onAnnulla = { inModifica = null },
            onSalva = { nuova -> vm.salva(nuova, id) { inModifica = null } },
        )
        return
    }

    Scaffold(
        topBar = {
            GarsalTopBar(
                titolo = "Tasks",
                onIndietro = onIndietro,
                azioni = {
                    Text(
                        text = "${stato.diOggi.size + stato.inRitardo.size}",
                        color = Palette.light,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { inModifica = BozzaTask() to null },
                containerColor = Palette.topBar,
                contentColor = Palette.light,
            ) { Text("+", fontSize = 26.sp) }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                stato.caricamento && stato.task.isEmpty() ->
                    CircularProgressIndicator(
                        color = Palette.topBar,
                        modifier = Modifier.align(Alignment.Center),
                    )

                stato.task.isEmpty() ->
                    Text(
                        "Nessun task.",
                        color = Palette.muted,
                        modifier = Modifier.align(Alignment.Center),
                    )

                else -> LazyColumn(
                    contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 88.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    sezione("In ritardo", stato.inRitardo, Palette.danger, stato) { azioniSu = it }
                    sezione("Oggi", stato.diOggi, Palette.success, stato) { azioniSu = it }
                    sezione("Prossimi", stato.prossimi, Palette.accent, stato) { azioniSu = it }
                    sezione("Quando capita", stato.liberi, Palette.muted, stato) { azioniSu = it }
                }
            }

            stato.errore?.let {
                Text(
                    it,
                    color = Palette.danger,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.align(Alignment.TopCenter).padding(8.dp),
                )
            }

            stato.messaggio?.let { messaggio ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Palette.dark),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Row(
                        Modifier.padding(14.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(messaggio, color = Palette.light)
                        Text(
                            "✕",
                            color = Palette.light,
                            modifier = Modifier.clickable { vm.messaggioMostrato() },
                        )
                    }
                }
            }
        }
    }

    azioniSu?.let { task ->
        DialogoAzioni(
            task = task,
            onChiudi = { azioniSu = null },
            onCompleta = { vm.completa(task.id); azioniSu = null },
            onSalta = { azioniSu = null; daSaltare = task },
            onFallisci = { vm.fallisci(task.id); azioniSu = null },
            onModifica = {
                azioniSu = null
                inModifica = BozzaTask.da(task) to task.id
            },
            onElimina = { azioniSu = null; daEliminare = task },
        )
    }

    daSaltare?.let { task ->
        DialogoSalta(
            task = task,
            onAnnulla = { daSaltare = null },
            onConferma = { giorni -> vm.salta(task.id, giorni); daSaltare = null },
        )
    }

    daEliminare?.let { task ->
        AlertDialog(
            onDismissRequest = { daEliminare = null },
            title = { Text("Eliminare il task?") },
            text = {
                Text(
                    "«${task.titolo}» sparisce per sempre, con tutta la sua storia. " +
                        "Per toglierlo di mezzo senza perderlo si archivia da tasks.html."
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.elimina(task.id); daEliminare = null }) {
                    Text("Elimina", color = Palette.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { daEliminare = null }) { Text("Annulla") }
            },
        )
    }
}

/** Una sezione dell'elenco: intestazione più le sue schede, se ce ne sono. */
private fun androidx.compose.foundation.lazy.LazyListScope.sezione(
    titolo: String,
    task: List<TsTask>,
    colore: androidx.compose.ui.graphics.Color,
    stato: TasksState,
    onTocca: (TsTask) -> Unit,
) {
    if (task.isEmpty()) return
    item(key = "intestazione-$titolo") {
        Text(
            text = "$titolo · ${task.size}",
            fontWeight = FontWeight.Bold,
            color = colore,
            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
        )
    }
    items(task, key = { it.id }) { t -> SchedaTask(t, colore, stato, onTocca) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SchedaTask(
    task: TsTask,
    colore: androidx.compose.ui.graphics.Color,
    stato: TasksState,
    onTocca: (TsTask) -> Unit,
) {
    val priorita = stato.prioritaDi(task.prioritaId)

    // Niente striscia di colore verticale come in tasks.html: lì è un `<div>`
    // che si stira da solo, qui vorrebbe un'altezza — e un'altezza fissa
    // attorno a del testo che cresce coi caratteri di sistema è il difetto
    // della top bar rifatto uguale. Il colore della sezione sta
    // nell'intestazione e nel pallino qui davanti al titolo.
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Palette.inputBg)
            .clickable { onTocca(task) }
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("●", color = colore, modifier = Modifier.padding(end = 8.dp))
            Text(
                text = task.titolo,
                fontWeight = FontWeight.SemiBold,
                color = Palette.dark,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        Text(
            text = buildString {
                append(TsTask.etichettaTipo(task.tipo))
                task.dataDiRiferimento?.let {
                    append(" · ")
                    append(dataItaliana(it))
                    oraDa(it)?.let { ora -> append(" $ora") }
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = Palette.muted,
            modifier = Modifier.padding(top = 2.dp),
        )

        if (priorita != null || task.categorie.isNotEmpty()) {
            FlowRow(
                Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                priorita?.let {
                    Etichetta("🎯 ${it.nome}", coloreDaHex(it.colore) ?: Palette.secondary)
                }
                task.categorie.mapNotNull { stato.categoriaDi(it) }.forEach { c ->
                    Etichetta(
                        "${c.icona.orEmpty()} ${c.nome}".trim(),
                        coloreDaHex(c.colore) ?: Palette.accent,
                    )
                }
            }
        }
    }
}

@Composable
private fun Etichetta(testo: String, colore: androidx.compose.ui.graphics.Color) {
    Text(
        text = testo,
        color = Palette.light,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier
            .padding(bottom = 4.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(colore)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/**
 * Le azioni su un task, una per riga.
 *
 * Una riga per azione e non tre pulsanti in fila sulla scheda: coi caratteri
 * di sistema grandi tre pulsanti affiancati diventano illeggibili o si
 * accavallano, e qui ognuno ha la larghezza intera e la sua spiegazione.
 */
@Composable
private fun DialogoAzioni(
    task: TsTask,
    onChiudi: () -> Unit,
    onCompleta: () -> Unit,
    onSalta: () -> Unit,
    onFallisci: () -> Unit,
    onModifica: () -> Unit,
    onElimina: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onChiudi,
        title = { Text(task.titolo) },
        text = {
            Column {
                task.descrizione?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.muted)
                }
                Voce("✅ Completa", "+${task.puntiSuccesso} punti", Palette.success, onCompleta)
                // `free_repeat` non ha una prossima occorrenza a cui saltare:
                // la RPC `task_skip` risponde con un errore, quindi la voce
                // non si mostra invece di offrire un pulsante che non va.
                if (task.tipo != "free_repeat") {
                    Voce("⏭ Salta", "${task.puntiSalto} punti", Palette.warning, onSalta)
                }
                Voce("✗ Fallito", "${task.puntiFallimento} punti", Palette.danger, onFallisci)
                if (task.modificabile) {
                    Voce("✏️ Modifica", null, Palette.accent, onModifica)
                } else {
                    Text(
                        text = "I workflow si modificano da tasks.html: i loro step hanno " +
                            "dipendenze fra loro, e un form che le semplifica li romperebbe.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.muted,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
                Voce("🗑 Elimina", null, Palette.danger, onElimina)
            }
        },
        confirmButton = { TextButton(onClick = onChiudi) { Text("Chiudi") } },
    )
}

@Composable
private fun Voce(
    titolo: String,
    dettaglio: String?,
    colore: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(titolo, color = colore, fontWeight = FontWeight.SemiBold)
        dettaglio?.let {
            Text(it, color = Palette.muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * Di quanti giorni spostare. Lo chiede **solo** per i `single`: per tutti gli
 * altri tipi `task_skip` ignora `p_days` e va alla prossima occorrenza sua,
 * quindi chiedere un numero che il server butta via sarebbe una bugia.
 */
@Composable
private fun DialogoSalta(task: TsTask, onAnnulla: () -> Unit, onConferma: (Int) -> Unit) {
    if (task.tipo != "single") {
        AlertDialog(
            onDismissRequest = onAnnulla,
            title = { Text("Saltare?") },
            text = { Text("Il task va alla sua prossima occorrenza.") },
            confirmButton = { TextButton(onClick = { onConferma(1) }) { Text("Salta") } },
            dismissButton = { TextButton(onClick = onAnnulla) { Text("Annulla") } },
        )
        return
    }

    var giorni by remember { mutableStateOf("1") }
    val numero = giorni.toIntOrNull()

    AlertDialog(
        onDismissRequest = onAnnulla,
        title = { Text("Di quanti giorni?") },
        text = {
            Column {
                OutlinedTextField(
                    value = giorni,
                    onValueChange = { giorni = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("Giorni") },
                    singleLine = true,
                )
                Text(
                    text = numero?.let {
                        "Nuova data: " + dataItaliana(
                            (giornoDa(task.dataDiRiferimento) ?: LocalDate.now())
                                .plusDays(it.toLong()).toString()
                        )
                    } ?: "Serve un numero di giorni.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.muted,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = numero != null && numero >= 1,
                onClick = { numero?.let(onConferma) },
            ) { Text("Sposta") }
        },
        dismissButton = { TextButton(onClick = onAnnulla) { Text("Annulla") } },
    )
}
