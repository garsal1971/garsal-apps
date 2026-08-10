package com.garsal.appsphere.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.garsal.appsphere.core.GarsalTopBar
import com.garsal.appsphere.core.Palette
import com.garsal.appsphere.core.coloreDaHex
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    onApriApp: (String) -> Unit,
    vm: HomeViewModel = viewModel(),
) {
    val stato by vm.state.collectAsStateWithLifecycle()
    val totale = stato.bolle.sumOf { it.punteggio }

    Scaffold(
        topBar = {
            GarsalTopBar(
                titolo = if (stato.modalitaNascosta) "AppSphere ·" else "AppSphere",
                azioni = {
                    Text(
                        text = String.format(Locale.ITALY, "%,d", totale),
                        color = Palette.light,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Ricarica",
                        tint = Palette.light,
                        modifier = Modifier
                            .size(26.dp)
                            // Tocco = ricarica; pressione lunga = modalità
                            // nascosta, come sul web dove è un gesto discreto
                            // e non un pulsante etichettato.
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = { vm.ricarica() },
                                    onLongPress = { vm.cambiaModalitaNascosta() },
                                )
                            },
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
                    CampoBolle(bolle = stato.bolle, onApri = onApriApp)
                }
            }
        }
    }
}

/**
 * L'area delle bolle: dimensioni e collocazione le decide [BubbleLayout],
 * qui si disegnano e si trascinano.
 */
@Composable
private fun CampoBolle(bolle: List<Bolla>, onApri: (String) -> Unit) {
    val densita = LocalDensity.current.density

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()

        val raggi = remember(bolle, w, h, densita) {
            val massimo = bolle.maxOfOrNull { it.punteggio } ?: 0
            bolle.map { BubbleLayout.diametro(it.punteggio, massimo, densita) / 2f }
        }

        // Le posizioni si ricalcolano solo quando cambiano le bolle o la
        // dimensione dell'area: un trascinamento non deve rimescolare tutto.
        var posizioni by remember(bolle, w, h) {
            mutableStateOf(
                buildList {
                    val nodi = mutableListOf<BubbleLayout.Nodo>()
                    raggi.forEachIndexed { i, r ->
                        val (x, y) = BubbleLayout.collocazioneIniziale(r, nodi, w, h)
                        nodi += BubbleLayout.Nodo(i, x, y, r)
                    }
                    BubbleLayout.assesta(nodi, w, h)
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
                onApri = { onApri(bolla.route) },
                onTrascina = { spostamento ->
                    val nodi = posizioni.mapIndexed { j, p ->
                        BubbleLayout.Nodo(j, p.x, p.y, raggi[j])
                    }.toMutableList()
                    nodi[i].x += spostamento.x
                    nodi[i].y += spostamento.y
                    BubbleLayout.risolviTrascinamento(nodi, i, w, h)
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
    onApri: () -> Unit,
    onTrascina: (Offset) -> Unit,
) {
    val densita = LocalDensity.current.density
    val coloreCerchio = coloreDaHex(bolla.colore) ?: Palette.olimpici.first()
    // Testo bianco o scuro secondo quanto è chiaro lo sfondo: il giallo
    // olimpico con scritta bianca sopra non si legge.
    val coloreTesto = if (coloreCerchio.luminance() > 0.6f) Palette.dark else Palette.light
    val diametroDp = (raggio * 2f / densita).dp
    val dimensioneTesto = misuraTesto(bolla.nome, raggio * 2f, densita)

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
            .pointerInput(bolla.htmlFile) {
                detectDragGestures { cambiamento, spostamento ->
                    cambiamento.consume()
                    onTrascina(spostamento)
                }
            }
            .clickable(onClick = onApri),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = bolla.nome,
            color = coloreTesto,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            fontSize = dimensioneTesto,
            lineHeight = dimensioneTesto * 1.2f,
            maxLines = 3,
            modifier = Modifier.padding(horizontal = (diametroDp.value * 0.12f).dp),
        )
    }
}

/**
 * Dimensione del testo dentro la bolla.
 *
 * Il web fa una ricerca binaria misurando davvero il testo (`fitFontSize`);
 * qui è una stima sulla parola più lunga, che con nomi di due o tre parole dà
 * lo stesso risultato a occhio senza dover misurare fuori schermo.
 */
private fun misuraTesto(nome: String, diametro: Float, densita: Float) = run {
    val parolaPiuLunga = max(nome.split(" ").maxOfOrNull { it.length } ?: 1, 1)
    val larghezzaUtile = diametro * 0.64f
    val stimaPx = larghezzaUtile / (parolaPiuLunga * 0.58f)
    (stimaPx / densita).coerceIn(9f, (diametro * 0.26f) / densita).sp
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
