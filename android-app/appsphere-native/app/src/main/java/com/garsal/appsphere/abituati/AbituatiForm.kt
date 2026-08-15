package com.garsal.appsphere.abituati

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.garsal.appsphere.core.GarsalTopBar
import com.garsal.appsphere.core.Palette
import java.time.LocalDate

/** L'abitudine che si sta scrivendo. */
data class BozzaAbitudine(
    val nome: String = "",
    val descrizione: String = "",
    val categoriaId: String? = null,
    val frequenza: String = "daily",
    val giorniSettimana: List<Int> = emptyList(),
    val orari: List<String> = emptyList(),
    val inizio: LocalDate = LocalDate.now(),
    val obiettivo: Int = 30,
    val jolly: Int = 3,
    val puntiPremio: Int = 0,
    val puntiPenalita: Int = 0,
) {
    val valida: Boolean get() = nome.isNotBlank() && obiettivo >= 1 &&
        (frequenza != "weekly" || giorniSettimana.isNotEmpty()) &&
        (frequenza != "daily_multiple" || orari.isNotEmpty())

    companion object {
        fun da(a: HbAbitudine) = BozzaAbitudine(
            nome = a.nome,
            descrizione = a.descrizione.orEmpty(),
            categoriaId = a.categoriaId,
            frequenza = a.frequenza,
            giorniSettimana = a.giorniSettimana,
            orari = a.orari,
            inizio = a.giornoInizio ?: LocalDate.now(),
            obiettivo = a.obiettivo,
            jolly = a.jollyMassimi,
            puntiPremio = a.puntiPremio,
            puntiPenalita = a.puntiPenalita,
        )
    }
}

private val GIORNI = listOf(
    1 to "Lun", 2 to "Mar", 3 to "Mer", 4 to "Gio", 5 to "Ven", 6 to "Sab", 0 to "Dom",
)

/**
 * Creazione e modifica di un'abitudine.
 *
 * ⚠️ **La frequenza non si cambia** su un'abitudine che esiste già, come il
 * tipo di un task: decide quali colonne quella riga usa — `daily_times` su una
 * settimanale non la pulisce nessuno — e soprattutto i giorni già spuntati sono
 * stati contati con la regola di prima. Stessa ragione per la **data di
 * inizio**: da lì partono lo streak, i giorni mancati e la scadenza dello
 * stack, e spostarla riscriverebbe il giudizio su giorni già passati.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AbituatiForm(
    bozzaIniziale: BozzaAbitudine,
    id: String?,
    categorie: List<HbCategoria>,
    onAnnulla: () -> Unit,
    onSalva: (BozzaAbitudine) -> Unit,
) {
    var b by remember { mutableStateOf(bozzaIniziale) }
    val context = LocalContext.current
    val nuova = id == null

    Scaffold(
        topBar = {
            GarsalTopBar(
                titolo = if (nuova) "🎯 Nuova abitudine" else "✏️ Modifica abitudine",
                onIndietro = onAnnulla,
                azioni = {
                    Text(
                        text = "Salva",
                        color = if (b.valida) Palette.light else Palette.light.copy(alpha = 0.4f),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = b.valida) { onSalva(b) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(
                value = b.nome,
                onValueChange = { b = b.copy(nome = it) },
                label = { Text("Nome") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = b.descrizione,
                onValueChange = { b = b.copy(descrizione = it) },
                label = { Text("Descrizione") },
                modifier = Modifier.fillMaxWidth(),
            )

            // ── Categoria ───────────────────────────────────────────────
            if (categorie.isNotEmpty()) {
                Etichetta("Categoria")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Scelta("— nessuna —", b.categoriaId == null) { b = b.copy(categoriaId = null) }
                    categorie.forEach { cat ->
                        Scelta(cat.etichetta, b.categoriaId == cat.id) {
                            b = b.copy(categoriaId = cat.id)
                        }
                    }
                }
            }

            // ── Frequenza ───────────────────────────────────────────────
            Etichetta("Frequenza")
            if (nuova) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Scelta("Ogni giorno", b.frequenza == "daily") {
                        b = b.copy(frequenza = "daily")
                    }
                    Scelta("Più volte al giorno", b.frequenza == "daily_multiple") {
                        b = b.copy(frequenza = "daily_multiple")
                    }
                    Scelta("Giorni della settimana", b.frequenza == "weekly") {
                        b = b.copy(frequenza = "weekly")
                    }
                }
            } else {
                Text(
                    text = when (b.frequenza) {
                        "daily_multiple" -> "Più volte al giorno — non si cambia"
                        "weekly" -> "Giorni della settimana — non si cambia"
                        else -> "Ogni giorno — non si cambia"
                    },
                    color = Palette.muted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (b.frequenza == "weekly") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GIORNI.forEach { (numero, nome) ->
                        val scelto = numero in b.giorniSettimana
                        Scelta(nome, scelto) {
                            b = b.copy(
                                giorniSettimana = if (scelto) b.giorniSettimana - numero
                                else b.giorniSettimana + numero
                            )
                        }
                    }
                }
            }

            if (b.frequenza == "daily_multiple") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    b.orari.sorted().forEach { orario ->
                        Scelta("$orario ✕", true) { b = b.copy(orari = b.orari - orario) }
                    }
                    Scelta("＋ orario", false) {
                        val adesso = java.time.LocalTime.now()
                        TimePickerDialog(
                            context,
                            { _, ora, minuti ->
                                val o = "%02d:%02d".format(ora, minuti)
                                if (o !in b.orari) b = b.copy(orari = b.orari + o)
                            },
                            adesso.hour, 0, true,
                        ).show()
                    }
                }
            }

            // ── Numeri ──────────────────────────────────────────────────
            Numero("Obiettivo (giorni)", b.obiettivo) { b = b.copy(obiettivo = it) }
            Numero("Jolly a disposizione", b.jolly) { b = b.copy(jolly = it) }
            Numero("Punti se lo completi", b.puntiPremio) { b = b.copy(puntiPremio = it) }
            Numero("Punti se lo fallisci", b.puntiPenalita) { b = b.copy(puntiPenalita = it) }

            // ── Inizio ──────────────────────────────────────────────────
            Etichetta("Inizio")
            if (nuova) {
                Scelta(dataItaliana(b.inizio), false) {
                    scegliData(context, b.inizio) { b = b.copy(inizio = it) }
                }
            } else {
                Text(
                    text = "${dataItaliana(b.inizio)} — non si cambia: da lì partono streak, " +
                        "giorni mancati e scadenza dello stack.",
                    color = Palette.muted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun Etichetta(testo: String) {
    Text(text = testo, color = Palette.muted, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun Numero(etichetta: String, valore: Int, onCambia: (Int) -> Unit) {
    var testo by remember(etichetta) { mutableStateOf(valore.toString()) }
    OutlinedTextField(
        value = testo,
        onValueChange = {
            testo = it.filter(Char::isDigit)
            onCambia(testo.toIntOrNull() ?: 0)
        },
        label = { Text(etichetta) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Scelta(testo: String, scelto: Boolean, onTocca: () -> Unit) {
    Text(
        text = testo,
        color = if (scelto) Palette.light else Palette.dark,
        fontWeight = if (scelto) FontWeight.Bold else FontWeight.Normal,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (scelto) NeroAbituati else Palette.inputBg)
            .clickable(onClick = onTocca)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

internal fun scegliData(context: Context, iniziale: LocalDate, onScelta: (LocalDate) -> Unit) {
    DatePickerDialog(
        context,
        { _, anno, mese, giorno -> onScelta(LocalDate.of(anno, mese + 1, giorno)) },
        iniziale.year, iniziale.monthValue - 1, iniziale.dayOfMonth,
    ).show()
}

/** `2026-08-15` → `15/08/2026`, come ovunque nelle app di casa. */
internal fun dataItaliana(giorno: LocalDate): String =
    "%02d/%02d/%d".format(giorno.dayOfMonth, giorno.monthValue, giorno.year)

internal fun dataItaliana(iso: String?): String {
    val pezzi = iso?.take(10)?.split("-") ?: return ""
    return if (pezzi.size == 3) "${pezzi[2]}/${pezzi[1]}/${pezzi[0]}" else iso
}
