package com.garsal.appsphere.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.garsal.appsphere.core.DialogoAggiornamento
import com.garsal.appsphere.core.GarsalTopBar
import com.garsal.appsphere.core.Palette
import com.garsal.appsphere.core.coloreDaHex
import com.garsal.appsphere.premi.PremiScreen
import com.garsal.appsphere.premi.RossoPremi
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/** Quanto il riquadro del totale sta staccato dagli angoli (web: 20 px). */
private val MARGINE_PANNELLO = 16.dp

/**
 * Il bordo nero di bolle e riquadro. Nel CSS sono 4 px, che su questo schermo
 * varrebbero un dp e mezzo: qui è in dp perché resti spesso come si vede sul
 * monitor, dove quel bordo è il segno grafico dell'app.
 */
private val BORDO_BOLLA = 3.dp

/** Il `#111` del web, non il nero pieno. */
private val NeroBordo = Color(0xFF111111)

@Composable
fun HomeScreen(
    onApriApp: (String) -> Unit,
    vm: HomeViewModel = viewModel(),
) {
    val stato by vm.state.collectAsStateWithLifecycle()
    var mostraVersione by remember { mutableStateOf(false) }
    var mostraPremi by remember { mutableStateOf(false) }

    fun chiudiPremi() {
        mostraPremi = false
        // Un premio ritirato ha speso dei punti: il riquadro in basso deve
        // dirlo appena si torna, non al prossimo avvio.
        vm.ricaricaPuntiSpesi()
    }

    // Il catalogo premi non è una destinazione di navigazione ma una schermata
    // che prende il posto della home, quindi il tasto indietro va intercettato
    // qui: senza, chiuderebbe l'app invece del catalogo.
    BackHandler(enabled = mostraPremi) { chiudiPremi() }

    if (mostraPremi) {
        PremiScreen(lordo = stato.totaleLordo, onIndietro = { chiudiPremi() })
        return
    }

    // Le icone in `dp` non seguono l'ingrandimento dei caratteri: accanto a un
    // punteggio scritto grande resterebbero minuscole, e soprattutto piccole da
    // centrare col dito. Stesso tetto della top bar.
    val scala = LocalDensity.current.fontScale.coerceIn(1f, 1.6f)

    Scaffold(
        topBar = {
            GarsalTopBar(
                titolo = if (stato.modalitaNascosta) "AppSphere ·" else "AppSphere",
                azioni = {
                    // Il totale non sta più qui ma nel riquadro in basso, come
                    // sul web: due volte la stessa cifra, con i caratteri di
                    // sistema grandi, sono solo una riga in meno per il titolo.
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Ricarica",
                        tint = Palette.light,
                        modifier = Modifier
                            .size(26.dp * scala)
                            .clickable { vm.ricarica() },
                    )
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Versione e aggiornamento",
                        tint = Palette.light,
                        modifier = Modifier
                            .padding(start = 10.dp)
                            .size(26.dp * scala)
                            .clickable { mostraVersione = true },
                    )
                },
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            if (!stato.avvisiChiusi && stato.avvisi.isNotEmpty()) {
                FumettoAvvisi(
                    avvisi = stato.avvisi,
                    onApri = onApriApp,
                    onChiudi = { vm.chiudiAvvisi() },
                )
            }

            stato.errore?.let { messaggio ->
                Text(
                    text = messaggio,
                    color = Palette.danger,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            Box(Modifier.fillMaxSize()) {
                // Il riquadro del totale galleggia sopra il campo delle bolle,
                // che lo scansano: qui se ne misura l'ingombro, perché con i
                // caratteri di sistema grandi è alto il doppio e un rettangolo
                // scritto a mano nel codice sarebbe sbagliato proprio lì.
                var ingombroPannello by remember { mutableStateOf(IntSize.Zero) }

                if (stato.bolle.isEmpty() && stato.caricamento) {
                    CircularProgressIndicator(
                        color = Palette.topBar,
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else if (stato.bolle.isEmpty()) {
                    Text(
                        text = "Nessuna app da mostrare.",
                        color = Palette.muted,
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else {
                    CampoBolle(
                        bolle = stato.bolle,
                        ingombroPannello = ingombroPannello,
                        inCattura = stato.inCattura,
                        onApri = onApriApp,
                        onCatturaColore = vm::catturaColore,
                        onPressioneLunga = vm::apriCattura,
                    )
                }

                PannelloTotale(
                    totale = stato.totaleNetto,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(MARGINE_PANNELLO)
                        .onSizeChanged { ingombroPannello = it },
                    onClick = { mostraPremi = true },
                )

                // Il widget del codice: si vede mentre si digita e, a modalità
                // accesa, resta lì con l'occhio — che è l'unico segno che la
                // modalità è su, oltre al punto nel titolo.
                if (stato.inCattura || stato.modalitaNascosta) {
                    WidgetCodice(
                        caselle = stato.caselle,
                        colori = stato.coloriCatturati,
                        inCattura = stato.inCattura,
                        modalitaNascosta = stato.modalitaNascosta,
                        onVerifica = { vm.verificaCodice() },
                        onSpegni = { vm.spegniModalitaNascosta() },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(MARGINE_PANNELLO),
                    )
                }
            }
        }
    }

    if (mostraVersione) {
        DialogoAggiornamento(onChiudi = { mostraVersione = false })
    }
}

/**
 * L'area delle bolle: dimensioni e collocazione le decide [BubbleLayout],
 * qui si disegnano e si trascinano.
 *
 * `ingombroPannello` è quanto misura il riquadro del totale, che sta in basso
 * a sinistra sopra quest'area: serve a ricavarne il rettangolo da scansare.
 *
 * Con `inCattura` acceso le bolle **non si aprono più**: il tocco registra il
 * loro colore nel codice. È la stessa scelta del web, dove `onDown` esce prima
 * di armare `onUp` e `launchApp` non scatta mai — senza, ogni cifra del codice
 * aprirebbe un'app.
 */
@Composable
private fun CampoBolle(
    bolle: List<Bolla>,
    ingombroPannello: IntSize,
    inCattura: Boolean,
    onApri: (String) -> Unit,
    onCatturaColore: (String) -> Unit,
    onPressioneLunga: () -> Unit,
) {
    val densita = LocalDensity.current.density
    val marginePx = with(LocalDensity.current) { MARGINE_PANNELLO.toPx() }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            // La pressione lunga sullo sfondo apre il widget, come sul `#field`
            // del web. Sta sul campo e non su un pulsante: un pulsante
            // etichettato «modalità nascosta» sarebbe una modalità nascosta
            // annunciata.
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { onPressioneLunga() })
            }
    ) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()

        val pannello = remember(ingombroPannello, w, h, marginePx) {
            if (ingombroPannello == IntSize.Zero) null
            else BubbleLayout.Pannello(
                x = marginePx,
                y = h - marginePx - ingombroPannello.height,
                w = ingombroPannello.width.toFloat(),
                h = ingombroPannello.height.toFloat(),
            )
        }

        val raggi = remember(bolle, w, h, densita) {
            val massimo = bolle.maxOfOrNull { it.punteggio } ?: 0
            bolle.map { BubbleLayout.diametro(it.punteggio, massimo, densita) / 2f }
        }

        // Le posizioni si ricalcolano solo quando cambiano le bolle, la
        // dimensione dell'area o quella del pannello: un trascinamento non deve
        // rimescolare tutto.
        var posizioni by remember(bolle, w, h, pannello) {
            mutableStateOf(
                buildList {
                    val nodi = mutableListOf<BubbleLayout.Nodo>()
                    raggi.forEachIndexed { i, r ->
                        val (x, y) = BubbleLayout.collocazioneIniziale(r, nodi, w, h)
                        nodi += BubbleLayout.Nodo(i, x, y, r)
                    }
                    BubbleLayout.assesta(nodi, w, h, pannello)
                    addAll(nodi.map { Offset(it.x, it.y) })
                }
            )
        }

        bolle.forEachIndexed { i, bolla ->
            if (i >= posizioni.size || i >= raggi.size) return@forEachIndexed
            val r = raggi[i]
            val centro = posizioni[i]

            BollaCerchio(
                bolla = bolla,
                raggio = r,
                centro = centro,
                inCattura = inCattura,
                onApri = {
                    if (inCattura) onCatturaColore(bolla.colore) else onApri(bolla.route)
                },
                onTrascina = { spostamento ->
                    val nodi = posizioni.mapIndexed { j, p ->
                        BubbleLayout.Nodo(j, p.x, p.y, raggi[j])
                    }.toMutableList()
                    nodi[i].x += spostamento.x
                    nodi[i].y += spostamento.y
                    BubbleLayout.risolviTrascinamento(nodi, i, w, h, pannello)
                    posizioni = nodi.map { Offset(it.x, it.y) }
                },
            )
        }
    }
}

// Il nome è BollaCerchio e non Bolla per non accavallarsi al data class Bolla
// di questo stesso package: la chiamata finirebbe sul costruttore.
@Composable
private fun BollaCerchio(
    bolla: Bolla,
    raggio: Float,
    centro: Offset,
    inCattura: Boolean,
    onApri: () -> Unit,
    onTrascina: (Offset) -> Unit,
) {
    val densita = LocalDensity.current.density
    val coloreCerchio = coloreDaHex(bolla.colore) ?: Palette.olimpici.first()
    // Testo bianco o scuro secondo quanto è chiaro lo sfondo: il giallo
    // olimpico con scritta bianca sopra non si legge.
    val coloreTesto = if (coloreCerchio.luminance() > 0.6f) Palette.dark else Palette.light
    val diametroDp = (raggio * 2f / densita).dp
    // Il numero si scrive solo se è un punteggio: quello di Spuntiamola sono i
    // giorni che mancano, quello di Obiettivi gli obiettivi attivi, e sotto il
    // nome di una bolla un conteggio si legge come punti (vedi
    // [AppSenzaPunti]). Si decide dall'`html_file` e non da un campo dentro la
    // bolla: la cache delle preferenze porta quelle dell'avvio precedente, e un
    // campo aggiunto oggi lì dentro non c'è.
    val conPunteggio = bolla.punteggio > 0 && AppSenzaPunti.contaComePunti(bolla.htmlFile)
    val dimensioneTesto = misuraTesto(bolla.nome, raggio * 2f, densita, conPunteggio)
    // `size * 0.13` come il web, con lo stesso minimo di 9.
    val dimensionePunteggio = ((raggio * 2f * 0.13f) / densita).coerceAtLeast(9f).sp

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    x = (centro.x - raggio).roundToInt(),
                    y = (centro.y - raggio).roundToInt(),
                )
            }
            .size(diametroDp)
            .clip(CircleShape)
            .background(coloreCerchio)
            // Il bordo nero del web (`border: 4px solid #111`), che stacca le
            // bolle scure dallo sfondo bianco e quelle chiare fra loro.
            .border(BORDO_BOLLA, NeroBordo, CircleShape)
            // A codice aperto le bolle non si trascinano: il gesto serve tutto
            // a toccarle una per una, e una bolla che scivola sotto il dito
            // mentre si digita il codice fa perdere la cifra.
            .pointerInput(bolla.htmlFile, inCattura) {
                if (inCattura) return@pointerInput
                detectDragGestures { cambiamento, spostamento ->
                    cambiamento.consume()
                    onTrascina(spostamento)
                }
            }
            .clickable(onClick = onApri),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = (diametroDp.value * 0.12f).dp),
        ) {
            Text(
                text = bolla.nome,
                color = coloreTesto,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                fontSize = dimensioneTesto,
                lineHeight = dimensioneTesto * 1.2f,
                maxLines = 3,
            )
            // Il punteggio a zero non si scrive, come sul web: una bolla al
            // minimo con uno «0» sotto sembra rotta, non vuota.
            if (conPunteggio) {
                Text(
                    text = "${bolla.punteggio}",
                    color = coloreTesto.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                    fontSize = dimensionePunteggio,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/**
 * Il widget del codice a colori — il `#code-entry` del web: una casella per
 * colore da indovinare, la freccia che verifica e l'occhio che spegne.
 *
 * Le caselle si riempiono toccando le bolle: **è la bolla a dare il colore**,
 * quindi il codice non si può leggere da nessuna parte nell'app, e cambia da
 * sé se un giorno cambiano i colori delle app. Toccare una casella già piena
 * verifica, come sul web, dove serve a chi ha finito di digitare senza dover
 * centrare la freccia.
 */
@Composable
private fun WidgetCodice(
    caselle: Int,
    colori: List<String>,
    inCattura: Boolean,
    modalitaNascosta: Boolean,
    onVerifica: () -> Unit,
    onSpegni: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xF2FFFFFF))
            .border(2.dp, NeroBordo, RoundedCornerShape(24.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (inCattura) {
            repeat(caselle) { i ->
                val colore = colori.getOrNull(i)
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(colore?.let { coloreDaHex(it) } ?: Palette.inputBg)
                        .border(2.dp, NeroBordo, RoundedCornerShape(6.dp))
                        .clickable(enabled = colori.isNotEmpty()) { onVerifica() },
                )
            }
            Text(
                text = "›",
                color = Palette.dark,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onVerifica() }
                    .padding(horizontal = 8.dp),
            )
        }

        if (modalitaNascosta) {
            // L'occhio compare solo a modalità accesa: è insieme il segno che
            // è accesa e il modo per spegnerla senza rifare il codice.
            Text(
                text = "👁",
                fontSize = 18.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSpegni() }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

/**
 * Il riquadro rosso in basso a sinistra: i punti che restano da spendere.
 *
 * È il `#score-panel` del web, compreso il fatto che si tocca — ed è l'unico
 * modo per arrivare al catalogo premi, di là come di qua.
 */
@Composable
private fun PannelloTotale(
    totale: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(RossoPremi)
            .border(BORDO_BOLLA, NeroBordo, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            // `heightIn` implicito: niente altezza fissa attorno a due scritte
            // che coi caratteri di sistema grandi crescono da sole.
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Text(
            text = "TOTALE",
            color = Palette.light.copy(alpha = 0.7f),
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelSmall,
        )
        Text(
            text = String.format(Locale.ITALY, "%,d", totale),
            color = Palette.light,
            fontSize = 28.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

/**
 * Dimensione del testo dentro la bolla.
 *
 * Il web fa una ricerca binaria misurando davvero il testo (`fitFontSize`);
 * qui è una stima sulla parola più lunga, che con nomi di due o tre parole dà
 * lo stesso risultato a occhio senza dover misurare fuori schermo.
 *
 * Col punteggio sotto il nome il tetto si abbassa: la riga in più vuole il suo
 * posto, e un nome alto quanto prima uscirebbe dal cerchio invece di
 * stringersi.
 */
private fun misuraTesto(
    nome: String,
    diametro: Float,
    densita: Float,
    conPunteggio: Boolean,
) = run {
    val parolaPiuLunga = max(nome.split(" ").maxOfOrNull { it.length } ?: 1, 1)
    val larghezzaUtile = diametro * 0.64f
    val stimaPx = larghezzaUtile / (parolaPiuLunga * 0.58f)
    val tetto = (diametro * if (conPunteggio) 0.22f else 0.26f) / densita
    (stimaPx / densita).coerceIn(9f, tetto).sp
}

@Composable
private fun FumettoAvvisi(
    avvisi: List<Avviso>,
    onApri: (String) -> Unit,
    onChiudi: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7E0)),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth()) {
                Text(
                    text = "Avvisi",
                    fontWeight = FontWeight.Bold,
                    color = Palette.dark,
                )
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Chiudi avvisi",
                    tint = Palette.muted,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(20.dp)
                        .clickable(onClick = onChiudi),
                )
            }
            avvisi.forEach { avviso ->
                Text(
                    text = avviso.testo,
                    color = Palette.dark,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onApri(avviso.route) },
                )
            }
        }
    }
}
