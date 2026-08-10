package com.garsal.appsphere.eventslog

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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

@Composable
fun EventsLogScreen(
    onIndietro: () -> Unit,
    vm: EventsLogViewModel = viewModel(),
) {
    val stato by vm.state.collectAsStateWithLifecycle()
    var eventoDaAnnotare by remember { mutableStateOf<ElEvento?>(null) }

    Scaffold(
        topBar = {
            GarsalTopBar(
                titolo = "Events Log",
                onIndietro = onIndietro,
                azioni = {
                    Text(
                        "${stato.puntiTotali} pt",
                        color = Palette.light,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                stato.caricamento && stato.gruppi.isEmpty() ->
                    CircularProgressIndicator(
                        color = Palette.success,
                        modifier = Modifier.align(Alignment.Center),
                    )

                stato.gruppi.isEmpty() ->
                    Text(
                        "Nessun gruppo di eventi.",
                        color = Palette.muted,
                        modifier = Modifier.align(Alignment.Center),
                    )

                else -> Column(Modifier.fillMaxSize()) {
                    SchedeGruppi(
                        gruppi = stato.gruppi,
                        attivo = stato.gruppoAttivo,
                        onScegli = { vm.scegliGruppo(it) },
                    )

                    GrigliaEventi(
                        eventi = stato.eventiDelGruppo,
                        valoriDaSelect = stato.valoriDaSelect,
                        onRegistra = { vm.registra(it, null) },
                        onAnnota = { eventoDaAnnotare = it },
                    )

                    Text(
                        "Registro",
                        fontWeight = FontWeight.Bold,
                        color = Palette.dark,
                        modifier = Modifier.padding(start = 14.dp, top = 8.dp),
                    )

                    LazyColumn(
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(stato.log) { riga ->
                            RigaLog(
                                riga = riga,
                                evento = stato.evento(riga.eventId),
                                onCancella = { vm.cancella(riga) },
                            )
                        }
                    }
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
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
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

    eventoDaAnnotare?.let { evento ->
        var nota by remember(evento.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { eventoDaAnnotare = null },
            title = { Text("${evento.icon.orEmpty()} ${evento.name}".trim()) },
            text = {
                OutlinedTextField(
                    value = nota,
                    onValueChange = { nota = it },
                    label = { Text("Nota") },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.registra(evento, nota)
                    eventoDaAnnotare = null
                }) { Text("Registra") }
            },
            dismissButton = {
                TextButton(onClick = { eventoDaAnnotare = null }) { Text("Annulla") }
            },
        )
    }
}

@Composable
private fun SchedeGruppi(
    gruppi: List<ElGruppo>,
    attivo: String?,
    onScegli: (String) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        gruppi.forEach { gruppo ->
            val scelto = gruppo.id == attivo
            val colore = coloreDaHex(gruppo.color) ?: Palette.accent
            Box(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (scelto) colore else Palette.inputBg)
                    .clickable { onScegli(gruppo.id) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "${gruppo.icon.orEmpty()} ${gruppo.name}".trim(),
                    color = if (scelto) Palette.light else Palette.dark,
                    fontWeight = if (scelto) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

/**
 * Gli eventi del gruppo scelto: un tocco registra, una pressione lunga apre la
 * nota. Gli eventi `DA_SELECT` non si toccano — si contano da soli — e mostrano
 * il valore corrente.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun GrigliaEventi(
    eventi: List<ElEvento>,
    valoriDaSelect: Map<String, Int>,
    onRegistra: (ElEvento) -> Unit,
    onAnnota: (ElEvento) -> Unit,
) {
    if (eventi.isEmpty()) {
        Text(
            "Nessun evento in questo gruppo.",
            color = Palette.muted,
            modifier = Modifier.padding(14.dp),
        )
        return
    }

    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        eventi.forEach { evento ->
            val automatico = evento.eventType == "DA_SELECT"
            val colore = coloreDaHex(evento.color) ?: Palette.success

            Column(
                Modifier
                    .padding(bottom = 8.dp)
                    .width(104.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (automatico) colore.copy(alpha = 0.35f) else colore)
                    .then(
                        if (automatico) Modifier
                        else Modifier.combinedClickable(
                            onClick = { onRegistra(evento) },
                            onLongClick = { onAnnota(evento) },
                        )
                    )
                    .padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(evento.icon ?: "•", fontSize = 24.sp)
                Text(
                    text = evento.name,
                    color = Palette.light,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = if (automatico) "conta: ${valoriDaSelect[evento.id] ?: "—"}"
                           else "+${evento.points}",
                    color = Palette.light.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun RigaLog(riga: ElLog, evento: ElEvento?, onCancella: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Palette.inputBg)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(evento?.icon ?: "•", fontSize = 18.sp)
        Column(Modifier.padding(start = 10.dp).weight(1f)) {
            Text(
                text = evento?.name ?: "Evento rimosso",
                fontWeight = FontWeight.SemiBold,
                color = Palette.dark,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = riga.loggedAt.replace('T', ' ') +
                    (riga.note?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = Palette.muted,
            )
        }
        Text(
            "+${riga.pointsAtLog}",
            fontWeight = FontWeight.Bold,
            color = Palette.success,
            modifier = Modifier.padding(end = 10.dp),
        )
        Text("🗑", modifier = Modifier.clickable(onClick = onCancella))
    }
}
