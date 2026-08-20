package com.garsal.appsphere.peso

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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
 * L'andamento del peso, disegnato a mano su un `Canvas` scorrevole.
 *
 * Nel web è Chart.js con zoom e scorrimento; qui è un disegno, ma con lo
 * stesso scorrimento — comincia dall'**inizio dell'obiettivo** e arriva alla
 * sua **fine**, futuro compreso, perché la spezzata del target racconta tutto
 * il piano e non solo il pezzo già passato. All'apertura è centrato su oggi
 * con circa due settimane prima e dopo in vista; da lì si scorre col dito.
 *
 * Le due serie non sono ricalcolate qui: il peso arriva dalle stesse righe
 * della tabella (quindi grafico e tabella non possono raccontare due storie
 * diverse), il target dai traguardi diretti dell'obiettivo — non dal valore
 * interpolato giorno per giorno, che resta dov'è utile: la tabella e il
 * punteggio.
 */
@Composable
fun VistaGrafico(stato: PesoState) {
    val obiettivo = stato.obiettivo
    val inizio = obiettivo?.let { PesoRegole.giornoDa(it.inizio) }
    val fine = obiettivo?.let { PesoRegole.giornoDa(it.fine) }

    if (obiettivo == null || inizio == null || fine == null || !fine.isAfter(inizio)) {
        Text(
            "Serve un obiettivo con una curva di traguardi per disegnare il grafico.",
            color = Palette.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
        )
        return
    }

    val righe = stato.righe.asReversed() // dalla più vecchia alla più recente
    val misuratore = rememberTextMeasurer()

    // Il peso non esce mai dal periodo dell'obiettivo: le pesate di prima —
    // il mese di respiro che il caricamento tiene per la prima interpolazione
    // — affollerebbero la lettura senza dire niente su questo obiettivo.
    val punti = righe.mapNotNull { riga ->
        val giorno = PesoRegole.giornoDa(riga.giorno) ?: return@mapNotNull null
        val peso = riga.minimo ?: return@mapNotNull null
        if (giorno.isBefore(inizio)) return@mapNotNull null
        giorno to peso
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

    // La spezzata del target è quella dei traguardi, non i valori
    // interpolati giorno per giorno — quelli restano nella tabella. Copre
    // **tutto** il periodo dell'obiettivo, futuro compreso: agli estremi,
    // se non cadono già su un traguardo, un solo valore interpolato completa
    // la linea fino all'inizio e fino alla fine dichiarati.
    val traguardi = obiettivo.traguardi
    val target = buildList {
        traguardi.forEach { t ->
            val giorno = PesoRegole.giornoDa(t.giorno) ?: return@forEach
            if (!giorno.isBefore(inizio) && !giorno.isAfter(fine)) add(giorno to t.peso)
        }
        if (none { it.first == inizio }) {
            PesoRegole.targetInterpolato(traguardi, inizio.toString())?.let { add(inizio to it) }
        }
        if (none { it.first == fine }) {
            PesoRegole.targetInterpolato(traguardi, fine.toString())?.let { add(fine to it) }
        }
        sortBy { it.first }
    }

    val valori = punti.map { it.second } + target.map { it.second }
    val minimo = valori.min()
    val massimo = valori.max()
    // Un filo di aria sopra e sotto: con la curva appiccicata al bordo non si
    // capisce se ha smesso di scendere o se è finito lo spazio.
    val margine = ((massimo - minimo) * 0.12).coerceAtLeast(0.3)
    val basso = minimo - margine
    val alto = massimo + margine
    val ampiezza = (alto - basso).coerceAtLeast(0.1)

    val oggi = LocalDate.now()
    val giorniTotali = (fine.toEpochDay() - inizio.toEpochDay()).coerceAtLeast(1L)
    // Circa due settimane per lato in vista all'apertura: la larghezza per
    // giorno è fissa, non ricavata dallo schermo, per restare prevedibile su
    // telefoni diversi (28 giorni ~ una schermata su un telefono medio).
    val larghezzaGiorno = 12.dp
    val larghezzaGrafico = larghezzaGiorno * (giorniTotali.toInt() + 1)

    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    var viewportPx by remember { mutableIntStateOf(0) }
    LaunchedEffect(viewportPx) {
        if (viewportPx <= 0) return@LaunchedEffect
        val giorniDaInizio = (oggi.toEpochDay() - inizio.toEpochDay()).coerceIn(0, giorniTotali)
        val pxPerGiorno = with(density) { larghezzaGiorno.toPx() }
        val centro = pxPerGiorno * giorniDaInizio
        val offset = (centro - viewportPx / 2f).toInt().coerceIn(0, scrollState.maxValue)
        scrollState.scrollTo(offset)
    }

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Legenda("Peso", Palette.primary)
            Legenda("Target", Verde)
        }

        Row(Modifier.fillMaxWidth().height(300.dp).padding(vertical = 4.dp)) {
            // Colonna fissa a sinistra per le etichette in kg: non scorre
            // insieme al disegno, altrimenti scorrendo si perderebbe subito
            // di vista a cosa si riferisce l'altezza della curva.
            Canvas(Modifier.width(48.dp).fillMaxHeight()) {
                val fondo = size.height - 34f
                listOf(basso, (basso + alto) / 2, alto).forEach { valore ->
                    val yy = (fondo * (1.0 - (valore - basso) / ampiezza)).toFloat()
                    drawText(
                        textMeasurer = misuratore,
                        text = kg(valore),
                        topLeft = Offset(0f, yy - 16f),
                        style = TextStyle(color = Palette.muted, fontSize = 11.sp),
                    )
                }
            }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .onSizeChanged { viewportPx = it.width }
                    .horizontalScroll(scrollState)
            ) {
                Canvas(Modifier.width(larghezzaGrafico).fillMaxHeight()) {
                    val fondo = size.height - 34f
                    val bordo = 10f

                    fun x(giorno: LocalDate): Float =
                        bordo + (giorno.toEpochDay() - inizio.toEpochDay()) * (size.width - 2 * bordo) / giorniTotali
                    fun y(peso: Double): Float =
                        (fondo * (1.0 - (peso - basso) / ampiezza)).toFloat()

                    // Le stesse tre righe di riferimento della colonna delle
                    // etichette, qui per intera larghezza.
                    listOf(basso, (basso + alto) / 2, alto).forEach { valore ->
                        drawLine(
                            color = Palette.border,
                            start = Offset(0f, y(valore)),
                            end = Offset(size.width, y(valore)),
                            strokeWidth = 1f,
                        )
                    }

                    // Una tacca e la data ogni settimana: senza, in un disegno
                    // che può essere lungo mesi non si capirebbe mai a che
                    // punto del periodo si sta scorrendo.
                    var cursore = inizio
                    while (!cursore.isAfter(fine)) {
                        val px = x(cursore)
                        drawLine(
                            color = Palette.border,
                            start = Offset(px, 0f),
                            end = Offset(px, fondo),
                            strokeWidth = 1f,
                        )
                        drawText(
                            textMeasurer = misuratore,
                            text = dataItaliana(cursore.toString()).take(5),
                            topLeft = Offset(px + 2f, fondo + 8f),
                            style = TextStyle(color = Palette.muted, fontSize = 10.sp),
                        )
                        cursore = cursore.plusDays(7)
                    }

                    // Oggi, marcata: è il punto da cui si parte per leggere
                    // il grafico, prima ancora del bordo sinistro.
                    if (!oggi.isBefore(inizio) && !oggi.isAfter(fine)) {
                        val px = x(oggi)
                        drawLine(
                            color = Palette.primary,
                            start = Offset(px, 0f),
                            end = Offset(px, fondo),
                            strokeWidth = 2f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
                        )
                        drawText(
                            textMeasurer = misuratore,
                            text = "Oggi",
                            topLeft = Offset(px + 4f, 4f),
                            style = TextStyle(color = Palette.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                        )
                    }

                    // La curva del target, tratteggiata e dritta fra un
                    // traguardo e l'altro: è una promessa, non un fatto.
                    if (target.size >= 2) {
                        val strada = Path().apply {
                            target.forEachIndexed { indice, (giorno, peso) ->
                                val px = x(giorno)
                                val py = y(peso)
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

                    // La curva del peso — non va oltre l'ultima pesata: il
                    // futuro non si può disegnare, solo il piano promette.
                    val strada = Path().apply {
                        punti.forEachIndexed { indice, (giorno, peso) ->
                            val px = x(giorno)
                            val py = y(peso)
                            if (indice == 0) moveTo(px, py) else lineTo(px, py)
                        }
                    }
                    drawPath(path = strada, color = Palette.primary, style = Stroke(width = 4f))

                    // L'ultima pesata segnata da un pallino: è quella che si cerca.
                    val (ultimoGiorno, ultimoPeso) = punti.last()
                    drawCircle(
                        color = Palette.primary,
                        radius = 7f,
                        center = Offset(x(ultimoGiorno), y(ultimoPeso)),
                    )
                }
            }
        }

        Text(
            text = "Dal ${dataItaliana(inizio.toString())} al ${dataItaliana(fine.toString())} · " +
                "minimo ${kg(punti.minOf { it.second })} kg",
            color = Palette.muted,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = "Scorri per vedere tutto il periodo. La linea del peso è il minimo di ogni " +
                "giornata, compresi i giorni ricostruiti; quella tratteggiata è il target dei " +
                "traguardi, anche nel futuro.",
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
