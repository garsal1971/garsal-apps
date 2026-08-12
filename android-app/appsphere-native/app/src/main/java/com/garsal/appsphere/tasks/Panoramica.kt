package com.garsal.appsphere.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garsal.appsphere.core.Palette
import com.garsal.appsphere.core.coloreDaHex

/**
 * La **panoramica**, come la disegna `renderDashboard()` in `tasks.html`.
 *
 * Le sezioni sono le stesse cinque e nello stesso ordine — ⚠️ SCADUTI,
 * 🎯 OGGI, 📅 PROSSIMI, 🔄 A LIBERA RIPETIZIONE, 👁️ NON IN PANORAMICA — con
 * la stessa striscia colorata a sinistra, e la scheda porta le stesse cose:
 * segno del tipo, data e ora, etichette delle categorie a destra, titolo, e in
 * fondo i pulsanti che agiscono subito.
 *
 * Quali pulsanti compaiono è la regola del web, non una scelta di qui:
 * **Completa** sempre, **Fallisci** su tutto tranne i `free_repeat`, **Salta**
 * solo su `recurring`, `simple_recurring` e `multiple` — sono gli unici tipi
 * che hanno una prossima occorrenza a cui saltare, e su un `single`
 * `task_skip` chiederebbe di quanti giorni spostarlo.
 *
 * Le tre file della scheda — segno e data con le etichette, titolo, pulsanti —
 * stanno **ognuna su una riga sola** e non vanno a capo: coi caratteri di
 * sistema grandi quel che non entra si raggiunge scorrendo col dito
 * (`RigaScorrevole`). La scheda non ha comunque nessuna altezza fissa: cresce
 * col testo, semplicemente non si moltiplica in righe.
 */
@Composable
fun VistaPanoramica(
    stato: TasksState,
    onApri: (TsTask) -> Unit,
    onCompleta: (TsTask) -> Unit,
    onFallisci: (TsTask) -> Unit,
    onSalta: (TsTask) -> Unit,
) {
    if (stato.panoramicaVuota) {
        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            Text("Niente in panoramica.", color = Palette.muted)
        }
        return
    }

    val azioni = AzioniScheda(onApri, onCompleta, onFallisci, onSalta)

    LazyColumn(
        contentPadding = PaddingValues(10.dp, 6.dp, 10.dp, 88.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        sezione("⚠️ SCADUTI", stato.scaduti, Palette.danger, stato, azioni)
        sezione("🎯 OGGI", stato.diOggi, Palette.primary, stato, azioni)
        sezione("📅 PROSSIMI", stato.prossimi, Palette.warning, stato, azioni)
        sezioneRaggruppata(
            titolo = "🔄 A LIBERA RIPETIZIONE",
            gruppi = stato.liberiPerCategoria,
            colore = Palette.accent,
            stato = stato,
            azioni = azioni,
            quanti = stato.liberi.size,
            // Nessuna categoria configurata per la dashboard: la sezione resta
            // e lo dice, come nel web. Se sparisse, quei task non si
            // vedrebbero da nessun'altra parte — non hanno una data che li
            // porti in calendario.
            mostraSeVuota = stato.liberi.isNotEmpty(),
            seVuota = "Nessuna categoria configurata per la dashboard. " +
                "Si attiva da tasks.html → Categorie.",
        )
        sezioneRaggruppata(
            titolo = "👁️ NON IN PANORAMICA",
            gruppi = stato.nascostiPerCategoria,
            colore = Palette.muted,
            stato = stato,
            azioni = azioni,
            // Il conteggio del web è quello dei task, non quello dei gruppi:
            // un task in due categorie compare due volte ma resta uno solo.
            quanti = stato.nascosti.size,
        )
    }
}

/** Le quattro cose che si possono fare su una scheda, passate in blocco. */
private data class AzioniScheda(
    val apri: (TsTask) -> Unit,
    val completa: (TsTask) -> Unit,
    val fallisci: (TsTask) -> Unit,
    val salta: (TsTask) -> Unit,
)

/**
 * Una sezione è **un solo `item`** della lista, non un'intestazione più tante
 * righe: la striscia colorata a sinistra deve correre senza interruzioni da
 * cima a fondo, e fra due `item` distinti si spezzerebbe alla prima spaziatura.
 * I task in panoramica sono qualche decina, quindi non c'è niente da
 * risparmiare tenendoli pigri.
 */
private fun LazyListScope.sezione(
    titolo: String,
    task: List<TsTask>,
    colore: Color,
    stato: TasksState,
    azioni: AzioniScheda,
) {
    if (task.isEmpty()) return
    item(key = "sezione-$titolo") {
        Sezione(titolo, task.size, colore) {
            task.forEach { SchedaTask(it, stato, azioni) }
        }
    }
}

private fun LazyListScope.sezioneRaggruppata(
    titolo: String,
    gruppi: List<GruppoCategoria>,
    colore: Color,
    stato: TasksState,
    azioni: AzioniScheda,
    quanti: Int = gruppi.sumOf { it.task.size },
    mostraSeVuota: Boolean = false,
    seVuota: String = "",
) {
    if (gruppi.isEmpty() && !mostraSeVuota) return
    item(key = "sezione-$titolo") {
        Sezione(titolo, quanti, colore) {
            if (gruppi.isEmpty()) {
                Text(seVuota, color = Palette.muted, style = MaterialTheme.typography.bodyMedium)
            }
            gruppi.forEach { gruppo ->
                Text(
                    text = "${gruppo.etichetta} (${gruppo.task.size})",
                    fontWeight = FontWeight.Bold,
                    color = coloreDaHex(gruppo.colore) ?: Palette.muted,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
                )
                gruppo.task.forEach { SchedaTask(it, stato, azioni) }
            }
        }
    }
}

/**
 * Il riquadro bianco con la striscia colorata a sinistra (`border-left: 4px`).
 *
 * La striscia è uno sfondo dietro la colonna, non un `Box` alto quanto le
 * schede: si stira da sé sull'altezza del contenuto, che coi caratteri grandi
 * è l'unica misura che non si può decidere prima.
 */
@Composable
private fun Sezione(
    titolo: String,
    quanti: Int,
    colore: Color,
    contenuto: @Composable () -> Unit,
) {
    val larghezzaStriscia = with(LocalDensity.current) { 4.dp.toPx() }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Palette.cardBg)
            .border(1.dp, Palette.border, RoundedCornerShape(6.dp))
            .drawBehind { drawRect(colore, size = Size(larghezzaStriscia, size.height)) }
            .padding(start = 4.dp)
            .padding(12.dp),
    ) {
        Text(
            text = "$titolo ($quanti)",
            color = colore,
            fontWeight = FontWeight.Black,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        contenuto()
    }
}

@Composable
private fun SchedaTask(task: TsTask, stato: TasksState, azioni: AzioniScheda) {
    // Le prime due categorie e poi «+N», come nel web: tre etichette lunghe
    // riempirebbero da sole tutta la riga.
    val categorie = task.categorie.mapNotNull { stato.categoriaDi(it) }
    val data = dataOraItaliana(task.dataDiRiferimento)

    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Palette.inputBg)
            .border(1.dp, Palette.border, RoundedCornerShape(6.dp))
            .clickable { azioni.apri(task) }
            .padding(10.dp),
    ) {
        RigaScorrevole(Arrangement.spacedBy(6.dp)) {
            Text(
                text = "${TsTask.segnoTipo(task.tipo)}  ${data.ifEmpty { TsTask.etichettaTipo(task.tipo) }}",
                fontWeight = FontWeight.SemiBold,
                color = Palette.dark,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                softWrap = false,
            )
            categorie.take(2).forEach { c ->
                Etichetta(c.etichetta, coloreDaHex(c.colore) ?: Palette.accent)
            }
            if (categorie.size > 2) Etichetta("+${categorie.size - 2}", Palette.muted)
        }

        RigaScorrevole(Arrangement.Start, Modifier.padding(top = 6.dp, bottom = 8.dp)) {
            Text(
                text = task.titolo,
                color = Palette.dark,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Visible,
            )
        }

        RigaScorrevole(Arrangement.spacedBy(8.dp)) {
            Pulsante("Completa", Palette.success) { azioni.completa(task) }
            if (task.tipo != "free_repeat") {
                Pulsante("Fallisci", Palette.danger) { azioni.fallisci(task) }
            }
            if (task.tipo in TIPI_CON_SALTA) {
                Pulsante("Salta", Palette.accent) { azioni.salta(task) }
            }
        }
    }
}

/**
 * Una riga che **non va mai a capo**: quello che non entra si raggiunge
 * scorrendo col dito verso sinistra.
 *
 * È la scelta opposta al solito consiglio sui caratteri di sistema grandi, ed
 * è voluta. Andare a capo tiene tutto leggibile ma allunga la scheda: con
 * l'ingrandimento alto tre pulsanti diventano tre righe, il titolo altre tre,
 * e in uno schermo ci sta un task e mezzo. Una riga sola tiene la scheda
 * compatta e non nasconde niente — il testo tagliato dal bordo non è perso,
 * basta trascinarlo — a patto che quello che conta di più stia **a sinistra**:
 * la data prima delle etichette, *Completa* prima di *Salta*.
 *
 * Lo scorrimento è orizzontale e quello della lista verticale: i due gesti non
 * si contendono niente, e il tocco sulla scheda continua ad aprire il dettaglio
 * perché un trascinamento non è un tocco.
 */
@Composable
private fun RigaScorrevole(
    disposizione: Arrangement.Horizontal,
    modifier: Modifier = Modifier,
    contenuto: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = disposizione,
        verticalAlignment = Alignment.CenterVertically,
        content = contenuto,
    )
}

/** Gli unici tipi su cui il web offre **Salta**: hanno una prossima occorrenza. */
private val TIPI_CON_SALTA = setOf("recurring", "simple_recurring", "multiple")

@Composable
private fun Pulsante(testo: String, sfondo: Color, onClick: () -> Unit) {
    Text(
        text = testo,
        color = Palette.light,
        fontWeight = FontWeight.SemiBold,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(sfondo)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

@Composable
private fun Etichetta(testo: String, colore: Color) {
    Text(
        text = testo,
        color = Palette.light,
        fontWeight = FontWeight.SemiBold,
        style = MaterialTheme.typography.labelMedium,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Visible,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(colore)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}
