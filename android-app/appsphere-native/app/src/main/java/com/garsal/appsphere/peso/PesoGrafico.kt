package com.garsal.appsphere.peso

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garsal.appsphere.core.Palette
import java.time.LocalDate

/**
 * L'andamento del peso, disegnato a mano su un `Canvas`.
 *
 * Nel web è Chart.js con zoom e scorrimento; qui è un disegno solo, e la
 * ragione è la stessa per cui il calendario dei task non è la griglia 7×6 del
 * web: su uno schermo di telefono, coi caratteri di sistema grandi, un grafico
 * fitto di etichette diventa illeggibile prima ancora di essere utile. Restano
 * le due cose che si guardano davvero — **dove sta la linea del peso rispetto
 * a quella del target** — e il resto si legge nella tabella.
 *
 * Le due serie non sono ricalcolate qui: arrivano dalle stesse righe della
 * tabella, quindi grafico e tabella non possono raccontare due storie diverse.
 */
@Composable
fun VistaGrafico(stato: PesoState) {
    val righe = stato.righe.asReversed() // dalla più vecchia alla più recente
    val misuratore = rememberTextMeasurer()

    val punti = righe.mapNotNull { riga ->
        val giorno = PesoRegole.giornoDa(riga.giorno) ?: return@mapNotNull null
        val peso = riga.minimo ?: return@mapNotNull null
        Triple(giorno, peso, riga.target)
    }

    if (punti.size < 2) {
        Text(
            "Servono almeno due pesate per disegnare la curva.",
            color = Palette.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
        )
        return
    }

    val primo = punti.first().first
    val ultimo = punti.last().first
    val giorniTotali = (ultimo.toEpochDay() - primo.toEpochDay()).coerceAtLeast(1L).toFloat()

    val valori = punti.map { it.second } + punti.mapNotNull { it.third }
    val minimo = valori.min()
    val massimo = valori.max()
    // Un filo di aria sopra e sotto: con la curva appiccicata al bordo non si
    // capisce se ha smesso di scendere o se è finito lo spazio.
    val margine = ((massimo - minimo) * 0.12).coerceAtLeast(0.3)
    val basso = minimo - margine
    val alto = massimo + margine
    val ampiezza = (alto - basso).coerceAtLeast(0.1)

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Legenda("Peso", Palette.primary)
            Legenda("Target", Verde)
        }

        Canvas(
            Modifier
                .fillMaxWidth()
                .height(300.dp)
                .padding(vertical = 4.dp)
        ) {
            val sinistra = 64f          // spazio per le etichette dei kg
            val fondo = size.height - 34f    // spazio per le date
            val larghezza = size.width - sinistra
            val altezza = fondo

            fun x(giorno: LocalDate): Float =
                sinistra + larghezza * ((giorno.toEpochDay() - primo.toEpochDay()) / giorniTotali)

            fun y(peso: Double): Float =
                (altezza * (1.0 - (peso - basso) / ampiezza)).toFloat()

            // Tre righe orizzontali di riferimento: basso, mezzo, alto.
            listOf(basso, (basso + alto) / 2, alto).forEach { valore ->
                val yy = y(valore)
                drawLine(
                    color = Palette.border,
                    start = Offset(sinistra, yy),
                    end = Offset(size.width, yy),
                    strokeWidth = 1f,
                )
                drawText(
                    textMeasurer = misuratore,
                    text = kg(valore),
                    topLeft = Offset(0f, yy - 16f),
                    style = TextStyle(color = Palette.muted, fontSize = 11.sp),
                )
            }

            // La curva del target, tratteggiata: è una promessa, non un fatto.
            val conTarget = punti.filter { it.third != null }
            if (conTarget.size >= 2) {
                val strada = Path().apply {
                    conTarget.forEachIndexed { indice, (giorno, _, target) ->
                        val px = x(giorno)
                        val py = y(target!!)
                        if (indice == 0) moveTo(px, py) else lineTo(px, py)
                    }
                }
                drawPath(
                    path = strada,
                    color = Verde,
                    style = Stroke(
                        width = 3f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)),
                    ),
                )
            }

            // La curva del peso.
            val strada = Path().apply {
                punti.forEachIndexed { indice, (giorno, peso, _) ->
                    val px = x(giorno)
                    val py = y(peso)
                    if (indice == 0) moveTo(px, py) else lineTo(px, py)
                }
            }
            drawPath(path = strada, color = Palette.primary, style = Stroke(width = 4f))

            // L'ultima pesata segnata da un pallino: è quella che si cerca.
            val (ultimoGiorno, ultimoPeso, _) = punti.last()
            drawCircle(
                color = Palette.primary,
                radius = 7f,
                center = Offset(x(ultimoGiorno), y(ultimoPeso)),
            )

            drawText(
                textMeasurer = misuratore,
                text = dataItaliana(primo.toString()),
                topLeft = Offset(sinistra, fondo + 8f),
                style = TextStyle(color = Palette.muted, fontSize = 11.sp),
            )
            val etichettaFine = dataItaliana(ultimo.toString())
            val larghezzaFine = misuratore.measure(
                etichettaFine,
                TextStyle(fontSize = 11.sp),
            ).size.width
            drawText(
                textMeasurer = misuratore,
                text = etichettaFine,
                topLeft = Offset(size.width - larghezzaFine, fondo + 8f),
                style = TextStyle(color = Palette.muted, fontSize = 11.sp),
            )
        }

        Text(
            text = "Dal ${dataItaliana(primo.toString())} al ${dataItaliana(ultimo.toString())} · " +
                "${punti.size} giorni · minimo ${kg(punti.minOf { it.second })} kg",
            color = Palette.muted,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = "La linea del peso è il minimo di ogni giornata, compresi i giorni " +
                "ricostruiti; quella tratteggiata è il target dei traguardi.",
            color = Palette.muted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 60.dp),
        )
    }
}

@Composable
private fun Legenda(testo: String, colore: Color) {
    Text(
        text = "▬ $testo",
        color = colore,
        fontWeight = FontWeight.SemiBold,
        style = MaterialTheme.typography.bodySmall,
    )
}
