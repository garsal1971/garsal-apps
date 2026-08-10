package com.garsal.appsphere.spuntiamola

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garsal.appsphere.core.Palette
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.math.roundToInt

/**
 * Il periodo, il traguardo e le giornate chiave.
 *
 * Le giornate chiave si modificano su una **copia**: finché non si preme
 * Salva il database non sa niente, e Annulla butta via tutto davvero — la
 * stessa scelta di `tmpKeyDays` sul web.
 */
@Composable
fun ImpostazioniDialog(
    stato: SpuntiamolaState,
    onAnnulla: () -> Unit,
    onSalva: (String, String, String, String, Boolean, Map<String, String>) -> Unit,
) {
    val impostazioni = stato.impostazioni
    var traguardo by remember { mutableStateOf(impostazioni?.goal ?: "Il traguardo") }
    var emoji by remember { mutableStateOf(impostazioni?.emoji ?: "🎯") }
    var inizio by remember { mutableStateOf(impostazioni?.startDate ?: LocalDate.now().toString()) }
    var fine by remember {
        mutableStateOf(impostazioni?.endDate ?: LocalDate.now().plusDays(30).toString())
    }
    var saltaWeekend by remember { mutableStateOf(impostazioni?.skipWeekend ?: false) }

    var chiave by remember { mutableStateOf(stato.giornateChiave) }
    var nuovoGiorno by remember { mutableStateOf("") }
    var nuovaEtichetta by remember { mutableStateOf("") }

    val dateValide = runCatching {
        LocalDate.parse(fine) >= LocalDate.parse(inizio)
    }.getOrDefault(false)

    AlertDialog(
        onDismissRequest = onAnnulla,
        title = { Text("Impostazioni") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = traguardo,
                    onValueChange = { traguardo = it },
                    label = { Text("Traguardo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = emoji,
                    onValueChange = { emoji = it },
                    label = { Text("Emoji") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = inizio,
                    onValueChange = { inizio = it },
                    label = { Text("Dal (aaaa-mm-gg)") },
                    singleLine = true,
                    isError = runCatching { LocalDate.parse(inizio) }.isFailure,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = fine,
                    onValueChange = { fine = it },
                    label = { Text("Al (aaaa-mm-gg)") },
                    singleLine = true,
                    isError = !dateValide,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = saltaWeekend, onCheckedChange = { saltaWeekend = it })
                    Text("Salta sabato e domenica")
                }

                Text(
                    "⭐ Giornate chiave",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    "Le giornate che contano: nella griglia sono dorate e alla spunta partono i fuochi.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = Palette.muted,
                )

                chiave.toSortedMap().forEach { (giorno, etichetta) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Palette.inputBg)
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "★ $giorno" + if (etichetta.isNotBlank()) " — $etichetta" else "",
                            modifier = Modifier.weight(1f),
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            text = "✕",
                            color = Palette.danger,
                            modifier = Modifier.clickable { chiave = chiave - giorno },
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = nuovoGiorno,
                        onValueChange = { nuovoGiorno = it },
                        label = { Text("aaaa-mm-gg") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = nuovaEtichetta,
                        onValueChange = { nuovaEtichetta = it },
                        label = { Text("Etichetta") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                TextButton(
                    enabled = runCatching { LocalDate.parse(nuovoGiorno) }.isSuccess,
                    onClick = {
                        chiave = chiave + (nuovoGiorno to nuovaEtichetta.trim())
                        nuovoGiorno = ""
                        nuovaEtichetta = ""
                    },
                ) { Text("Aggiungi giornata chiave") }
            }
        },
        confirmButton = {
            TextButton(
                enabled = dateValide,
                onClick = { onSalva(traguardo, emoji, inizio, fine, saltaWeekend, chiave) },
            ) { Text("Salva") }
        },
        dismissButton = {
            TextButton(onClick = onAnnulla) { Text("Annulla") }
        },
    )
}

/**
 * La cerimonia di chiusura, in tre passi: l'ultima spunta, la soddisfazione,
 * la nota. Non è un modulo da riempire: l'ultima spunta è un gesto che si fa
 * una volta sola e lascia la sua emoji come sigillo.
 */
@Composable
fun ChiusuraDialog(
    stato: SpuntiamolaState,
    onRimanda: () -> Unit,
    onChiudi: (Int, String, String, String?) -> Unit,
) {
    var passo by remember { mutableIntStateOf(1) }
    var emojiFinale by remember { mutableStateOf<String?>(null) }
    var quando by remember { mutableStateOf<String?>(null) }
    var soddisfazione by remember { mutableIntStateOf(70) }
    var nota by remember { mutableStateOf("") }

    val impostazioni = stato.impostazioni

    AlertDialog(
        onDismissRequest = onRimanda,
        title = {
            Column {
                Text("${impostazioni?.emoji ?: "🏁"} Chiudi la stecca")
                if (impostazioni != null) {
                    Text(
                        text = impostazioni.goal + "\n" +
                            "${stato.spuntati} giorni spuntati su ${stato.totali}",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = Palette.muted,
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (passo) {
                    1 -> {
                        Text("L'ultima spunta. Una sola, e la stecca è chiusa.")
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (emojiFinale == null) Palette.inputBg
                                    else Palette.success.copy(alpha = 0.2f)
                                )
                                .clickable(enabled = emojiFinale == null) {
                                    emojiFinale = EMOJI_SPUNTA.random()
                                    quando = OffsetDateTime.now(ZoneOffset.UTC).toString()
                                    passo = 2
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(emojiFinale ?: "☑️", fontSize = 56.sp)
                        }
                    }

                    2 -> {
                        Text("Quanto sei soddisfatto di com'è andata?")
                        Text(
                            "$soddisfazione",
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold,
                            color = Palette.secondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Slider(
                            value = soddisfazione.toFloat(),
                            onValueChange = { soddisfazione = it.roundToInt().coerceIn(1, 100) },
                            valueRange = 1f..100f,
                        )
                        Text(
                            etichettaSoddisfazione(soddisfazione),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    else -> {
                        Text("Due parole su com'è andata (facoltative).")
                        OutlinedTextField(
                            value = nota,
                            onValueChange = { nota = it },
                            label = { Text("Nota") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..3).forEach { i ->
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (i == passo) Palette.secondary else Palette.border)
                        )
                    }
                }
            }
        },
        confirmButton = {
            when (passo) {
                1 -> {}
                2 -> TextButton(onClick = { passo = 3 }) { Text("Avanti") }
                else -> TextButton(
                    enabled = !stato.chiusuraInCorso,
                    onClick = {
                        onChiudi(soddisfazione, nota, emojiFinale ?: "🏁", quando)
                    },
                ) { Text(if (stato.chiusuraInCorso) "Archivio…" else "Chiudi la stecca 🏁") }
            }
        },
        dismissButton = {
            when (passo) {
                3 -> TextButton(onClick = { passo = 2 }) { Text("Indietro") }
                else -> TextButton(onClick = onRimanda) { Text("Più tardi") }
            }
        },
    )
}
