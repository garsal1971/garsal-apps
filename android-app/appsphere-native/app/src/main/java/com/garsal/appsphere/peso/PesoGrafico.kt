package com.garsal.appsphere.peso

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
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
 * con un paio di settimane prima e dopo in vista; da lì si scorre col dito.
 *
 * Le due serie non sono ricalcolate qui: il peso arriva dalle stesse righe
 * della tabella (quindi grafico e tabella non possono raccontare due storie
 * diverse), il target dai traguardi diretti dell'obiettivo — non dal valore
 * interpolato giorno per giorno, che resta dov'è utile: la tabella e il
 * punteggio.
 *
 * ⚠️ **Il disegno prende tutta l'altezza che avanza** (`weight(1f)`) invece di
 * un'altezza fissa, e sotto non c'è più niente scritto: le due righe di
 * spiegazione che stavano lì raccontavano quello che si vede — che si scorre,
 * che la linea piena è il peso e la tratteggiata il target — e per dirlo si
 * prendevano un terzo dello schermo del telefono. Quel che serve davvero
 * (legenda e minimo del periodo) sta ora in **una riga sola sopra** il
 * grafico.
 *
 * ⚠️ **Le misure del disegno sono in `dp`, mai in pixel grezzi.** Prima
 * spessori, margini e la fascia delle date erano numeri in px (`4f`, `34f`):
 * su uno schermo denso valgono un terzo di quello che sembrano, e infatti le
 * date sotto l'asse venivano tagliate a metà — la fascia era alta 34 px, cioè
 * meno dell'altezza del testo che ci andava scritto dentro.
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
    //
    // Il terzo valore dice se quel giorno è **ricostruito**: la linea li
    // attraversa tutti (togliendoli cambierebbe la forma della curva, e i
    // punti valgono per il punteggio), ma il pallino si disegna solo sulle
    // pesate vere — un pallino su un giorno interpolato sembrerebbe una
    // misura che non c'è mai stata.
    val punti = righe.mapNotNull { riga ->
        val giorno = PesoRegole.giornoDa(riga.giorno) ?: return@mapNotNull null
        val peso = riga.minimo ?: return@mapNotNull null
        if (giorno.isBefore(inizio)) return@mapNotNull null
        Triple(giorno, peso, riga.interpolata)
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
    // Cinque righe di riferimento invece di tre: con tre, fra una riga e
    // l'altra ci sono anche cinque chili e l'altezza della curva si legge a
    // occhio invece che sul righello.
    val livelli = List(5) { basso + ampiezza * it / 4.0 }

    val oggi = LocalDate.now()
    val giorniTotali = (fine.toEpochDay() - inizio.toEpochDay()).coerceAtLeast(1L)

    // Quanto è largo un giorno: è la variabile che il **pizzico** cambia.
    // Non si ricava dallo schermo — così il grafico parte uguale su telefoni
    // diversi — e da lì in poi la scala la decide il dito.
    var dpGiorno by remember { mutableFloatStateOf(DP_GIORNO_INIZIALE) }
    val larghezzaGiorno = dpGiorno.dp

    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    var viewportPx by remember { mutableIntStateOf(0) }

    // Allargato al massimo il periodo intero sta in una schermata, e da lì in
    // poi il disegno non si stringe più: un `Canvas` più stretto del riquadro
    // che lo contiene lascerebbe una fascia vuota a destra, che sembra un
    // difetto e non una scala.
    val larghezzaRiquadro = with(density) { viewportPx.toDp() }
    val larghezzaGrafico =
        (larghezzaGiorno * (giorniTotali.toInt() + 1)).coerceAtLeast(larghezzaRiquadro)

    // Il giorno che stava al centro quando il pizzico è cominciato: dopo aver
    // cambiato scala si torna lì. Senza, stringendo le dita il grafico
    // scivolerebbe via da sé e si perderebbe il punto che si stava guardando.
    var centroDaTenere by remember { mutableStateOf<Double?>(null) }

    LaunchedEffect(viewportPx) {
        if (viewportPx <= 0) return@LaunchedEffect
        val giorniDaInizio = (oggi.toEpochDay() - inizio.toEpochDay()).coerceIn(0, giorniTotali)
        val pxPerGiorno = with(density) { larghezzaGiorno.toPx() }
        val centro = pxPerGiorno * giorniDaInizio
        val offset = (centro - viewportPx / 2f).toInt().coerceIn(0, scrollState.maxValue)
        scrollState.scrollTo(offset)
    }

    LaunchedEffect(dpGiorno) {
        val centro = centroDaTenere ?: return@LaunchedEffect
        // Un frame di attesa: la larghezza nuova del disegno — e con lei il
        // massimo dello scorrimento — si conosce solo dopo che è stata
        // misurata, e scrollTo su un massimo vecchio finirebbe corto.
        withFrameNanos { }
        val pxPerGiorno = with(density) { dpGiorno.dp.toPx() }
        val offset = (centro * pxPerGiorno - viewportPx / 2f).toInt()
        scrollState.scrollTo(offset.coerceIn(0, scrollState.maxValue))
        centroDaTenere = null
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Tutto quello che c'è da dire, in una riga: chi è chi e dov'è il
        // minimo del periodo. Il resto lo dice il disegno.
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Legenda("Peso", Palette.primary)
                Legenda("Target", Verde)
            }
            Text(
                text = "min ${kg(punti.minOf { it.second })} kg",
                color = Palette.muted,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                // In basso resta il posto del FAB «Pesati ancora», che
                // altrimenti si siederebbe proprio sopra le date dell'asse.
                .padding(bottom = 76.dp),
        ) {
            // Colonna fissa a sinistra per le etichette in kg: non scorre
            // insieme al disegno, altrimenti scorrendo si perderebbe subito
            // di vista a cosa si riferisce l'altezza della curva.
            Canvas(Modifier.width(44.dp).fillMaxHeight()) {
                val fondo = size.height - ASSE.toPx()
                livelli.forEach { valore ->
                    val yy = (fondo * (1.0 - (valore - basso) / ampiezza)).toFloat()
                    val etichetta = misuratore.measure(
                        text = kg(valore),
                        style = TextStyle(color = Palette.muted, fontSize = 12.sp),
                    )
                    drawText(
                        textLayoutResult = etichetta,
                        topLeft = Offset(
                            x = size.width - etichetta.size.width - 6.dp.toPx(),
                            y = yy - etichetta.size.height / 2f,
                        ),
                    )
                }
            }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Palette.cardBg)
                    .border(1.dp, Palette.border, RoundedCornerShape(12.dp))
                    .onSizeChanged { viewportPx = it.width }
                    // ⚠️ Il pizzico si scrive a mano invece di usare
                    // `detectTransformGestures` o `transformable`: quelli
                    // prendono anche il trascinamento a un dito e lo
                    // consumano, cioè si mangerebbero lo scorrimento del
                    // grafico. Qui si guarda l'evento solo quando le dita
                    // sono **almeno due**, e solo allora lo si consuma —
                    // con un dito solo l'evento passa oltre e arriva a
                    // `horizontalScroll`, che continua a funzionare come
                    // prima.
                    //
                    // ⚠️ E si guarda nel passaggio **`Initial`**, non in
                    // quello normale. `Main` arriva prima al modifier più
                    // interno, cioè allo scorrimento, che tratterebbe il
                    // pizzico come un trascinamento e farebbe scivolare il
                    // grafico mentre lo si stringe; `Initial` scende invece
                    // dall'esterno, quindi qui si può togliere l'evento di
                    // mano allo scorrimento prima che lo veda.
                    .pointerInput(giorniTotali) {
                        awaitEachGesture {
                            awaitFirstDown(
                                requireUnconsumed = false,
                                pass = PointerEventPass.Initial,
                            )
                            do {
                                val evento = awaitPointerEvent(PointerEventPass.Initial)
                                if (evento.changes.size >= 2) {
                                    val fattore = evento.calculateZoom()
                                    if (fattore != 1f && fattore > 0f) {
                                        // Il giorno al centro si legge
                                        // **prima** di cambiare scala, e solo
                                        // la prima volta del gesto: rileggerlo
                                        // a ogni evento userebbe la posizione
                                        // già spostata dall'evento precedente.
                                        val pxPerGiorno = dpGiorno.dp.toPx()
                                        if (centroDaTenere == null) {
                                            centroDaTenere =
                                                (scrollState.value + viewportPx / 2f) / pxPerGiorno.toDouble()
                                        }
                                        // Il minimo non è un numero scritto
                                        // qui: è la scala a cui **tutto il
                                        // periodo sta in una schermata**, e
                                        // dipende quindi da quanto è largo il
                                        // telefono e da quanto dura
                                        // l'obiettivo.
                                        val minimo =
                                            if (viewportPx > 0)
                                                (viewportPx.toDp().value / (giorniTotali + 1))
                                                    .toFloat()
                                                    .coerceAtLeast(DP_GIORNO_MINIMO)
                                            else DP_GIORNO_MINIMO
                                        dpGiorno = (dpGiorno * fattore)
                                            .coerceIn(minimo, DP_GIORNO_MASSIMO)
                                    }
                                    evento.changes.forEach { it.consume() }
                                }
                            } while (evento.changes.any { it.pressed })
                        }
                    }
                    .horizontalScroll(scrollState)
            ) {
                Canvas(Modifier.width(larghezzaGrafico).fillMaxHeight()) {
                    val fondo = size.height - ASSE.toPx()
                    val bordo = 10.dp.toPx()

                    fun x(giorno: LocalDate): Float =
                        bordo + (giorno.toEpochDay() - inizio.toEpochDay()) * (size.width - 2 * bordo) / giorniTotali
                    fun y(peso: Double): Float =
                        (fondo * (1.0 - (peso - basso) / ampiezza)).toFloat()

                    // Le stesse righe di riferimento della colonna delle
                    // etichette, qui per intera larghezza.
                    livelli.forEach { valore ->
                        drawLine(
                            color = Palette.border,
                            start = Offset(0f, y(valore)),
                            end = Offset(size.width, y(valore)),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }

                    // Una tacca e la data ogni settimana: senza, in un disegno
                    // che può essere lungo mesi non si capirebbe mai a che
                    // punto del periodo si sta scorrendo.
                    //
                    // ⚠️ Il passo segue lo zoom. A grafico stretto le date di
                    // ogni settimana finirebbero una sopra l'altra: illeggibili
                    // e pure più fitte delle tacche, che è il modo migliore per
                    // far sembrare rotto un disegno che sta funzionando.
                    val passoGiorni = when {
                        dpGiorno >= 12f -> 7L
                        dpGiorno >= 6f -> 14L
                        else -> 28L
                    }
                    var cursore: LocalDate = inizio
                    while (!cursore.isAfter(fine)) {
                        val px = x(cursore)
                        drawLine(
                            color = Palette.border,
                            start = Offset(px, 0f),
                            end = Offset(px, fondo),
                            strokeWidth = 1.dp.toPx(),
                        )
                        val data = misuratore.measure(
                            text = dataItaliana(cursore.toString()).take(5),
                            style = TextStyle(color = Palette.muted, fontSize = 11.sp),
                        )
                        // La data si scrive **centrata sulla tacca**, e la
                        // fascia sotto l'asse è alta abbastanza da contenerla:
                        // è il taglio che si vedeva prima.
                        drawText(
                            textLayoutResult = data,
                            topLeft = Offset(px - data.size.width / 2f, fondo + 6.dp.toPx()),
                        )
                        cursore = cursore.plusDays(passoGiorni)
                    }

                    // Oggi, marcata: è il punto da cui si parte per leggere
                    // il grafico, prima ancora del bordo sinistro.
                    if (!oggi.isBefore(inizio) && !oggi.isAfter(fine)) {
                        val px = x(oggi)
                        drawLine(
                            color = Palette.primary.copy(alpha = 0.7f),
                            start = Offset(px, 0f),
                            end = Offset(px, fondo),
                            strokeWidth = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(
                                floatArrayOf(4.dp.toPx(), 4.dp.toPx())
                            ),
                        )
                        val oggiTesto = misuratore.measure(
                            text = "Oggi",
                            style = TextStyle(
                                color = Palette.light,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                        // Su una targhetta piena invece che in aria: sopra le
                        // righe della griglia la scritta si leggeva a fatica.
                        drawRoundRect(
                            color = Palette.primary,
                            topLeft = Offset(px + 3.dp.toPx(), 2.dp.toPx()),
                            size = Size(
                                width = oggiTesto.size.width + 10.dp.toPx(),
                                height = oggiTesto.size.height + 4.dp.toPx(),
                            ),
                            cornerRadius = CornerRadius(6.dp.toPx()),
                        )
                        drawText(
                            textLayoutResult = oggiTesto,
                            topLeft = Offset(px + 8.dp.toPx(), 4.dp.toPx()),
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
                                width = 2.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round,
                                pathEffect = PathEffect.dashPathEffect(
                                    floatArrayOf(5.dp.toPx(), 4.dp.toPx())
                                ),
                            ),
                        )
                    }

                    // La curva del peso — non va oltre l'ultima pesata: il
                    // futuro non si può disegnare, solo il piano lo promette.
                    val strada = Path().apply {
                        punti.forEachIndexed { indice, (giorno, peso, _) ->
                            val px = x(giorno)
                            val py = y(peso)
                            if (indice == 0) moveTo(px, py) else lineTo(px, py)
                        }
                    }

                    // Sotto la curva una sfumatura che si spegne: dà al
                    // disegno un peso visivo che una linea sola non ha, e
                    // separa a colpo d'occhio il fatto (pieno) dalla promessa
                    // (tratteggiata).
                    val area = Path().apply {
                        addPath(strada)
                        lineTo(x(punti.last().first), fondo)
                        lineTo(x(punti.first().first), fondo)
                        close()
                    }
                    drawPath(
                        path = area,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Palette.primary.copy(alpha = 0.26f),
                                Palette.primary.copy(alpha = 0.02f),
                            ),
                            startY = 0f,
                            endY = fondo,
                        ),
                    )

                    drawPath(
                        path = strada,
                        color = Palette.primary,
                        style = Stroke(
                            width = 2.5.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
                    )

                    // Un pallino su ogni pesata **vera**, col contorno del
                    // colore dello sfondo perché non si impastino fra loro
                    // quando cadono vicine.
                    punti.forEach { (giorno, peso, ricostruito) ->
                        // Sotto una certa scala due giorni distano meno del
                        // pallino stesso: una collana di pallini appiccicati
                        // nasconde la curva invece di raccontarla.
                        if (ricostruito || dpGiorno < 8f) return@forEach
                        val centro = Offset(x(giorno), y(peso))
                        drawCircle(Palette.cardBg, radius = 3.5.dp.toPx(), center = centro)
                        drawCircle(Palette.primary, radius = 2.dp.toPx(), center = centro)
                    }

                    // L'ultima pesata è quella che si cerca: pallino più
                    // grande e il numero scritto accanto, perché il valore di
                    // oggi non si debba leggere sul righello delle etichette.
                    val (ultimoGiorno, ultimoPeso, _) = punti.last()
                    val fine2 = Offset(x(ultimoGiorno), y(ultimoPeso))
                    drawCircle(Palette.cardBg, radius = 6.dp.toPx(), center = fine2)
                    drawCircle(Palette.primary, radius = 4.dp.toPx(), center = fine2)

                    val valore = misuratore.measure(
                        text = kg(ultimoPeso),
                        style = TextStyle(
                            color = Palette.primary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    drawText(
                        textLayoutResult = valore,
                        topLeft = Offset(
                            // Sopra il pallino, e a sinistra se scriverlo a
                            // destra lo porterebbe fuori dal disegno.
                            x = (fine2.x + 8.dp.toPx())
                                .coerceAtMost(size.width - valore.size.width - 2.dp.toPx()),
                            y = (fine2.y - valore.size.height - 8.dp.toPx()).coerceAtLeast(0f),
                        ),
                    )
                }
            }
        }
    }
}

/** L'altezza della fascia sotto l'asse, dove vanno le date. */
private val ASSE = 26.dp

/**
 * La scala di partenza e i suoi estremi, in dp per giorno.
 *
 * Il minimo vero lo decide lo schermo — è la scala a cui tutto il periodo sta
 * in una schermata — e questa costante è solo il pavimento sotto cui non si
 * scende comunque, per un obiettivo lunghissimo su un telefono stretto. Il
 * massimo è il dettaglio di un giorno alla volta: oltre, un grafico lungo un
 * chilometro non è uno zoom, è un modo di perdersi.
 */
private const val DP_GIORNO_INIZIALE = 16f
private const val DP_GIORNO_MINIMO = 1.2f
private const val DP_GIORNO_MASSIMO = 48f

@Composable
private fun Legenda(testo: String, colore: Color) {
    Text(
        text = "▬ $testo",
        color = colore,
        fontWeight = FontWeight.SemiBold,
        style = MaterialTheme.typography.bodyMedium,
    )
}
