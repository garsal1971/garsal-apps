package com.garsal.appsphere.memo

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.garsal.appsphere.core.GarsalTopBar
import com.garsal.appsphere.core.Palette
import com.garsal.appsphere.core.RigaScorrevole
import com.garsal.appsphere.core.Tendina
import com.garsal.appsphere.core.TendinaFacoltativa
import com.garsal.appsphere.core.coloreDaHex
import com.garsal.appsphere.core.larghezzaPulsanti

/** Blu di Memo, il `--primary` della pagina. */
internal val BluMemo = Color(0xFF2563EB)

/** Il viola `--secondary`, che sul web tinge il segno 🙈 Riservato. */
internal val ViolaMemo = Color(0xFF6C5CE7)

/** Sfondo di default della scheda, quando non ha un colore scelto a mano. */
internal val AzzurroSchedaMemo = Color(0xFFE6F4FE)

/** Sfondo delle sole schede diario, stessa eccezione del colore scelto a mano. */
internal val GialloSchedaMemo = Color(0xFFFEF9E0)

/**
 * Memo in nativo: le schede di `memo.html` — note, liste e diari — con ricerca,
 * filtro per categoria, ordinamento, dettaglio e modifica.
 *
 * ⚠️ Gemella di `memo.html`, sulle stesse tabelle `mm_*` e sulle stesse
 * categorie condivise `cm_categories`. I due punti dove le implementazioni
 * possono divergere in silenzio sono il **contenuto**, che è HTML e passa da
 * [MemoHtml], e le **regole delle liste e dei diari**, che sono documentate
 * dove vivono: la spunta ottimistica in `MemoViewModel`, le misure aggiornate
 * riga per riga in `MemoRepository`, la misura non toccata che non finisce in
 * `measures` in [MemoRegistrazione].
 */
@Composable
fun MemoScreen(
    onIndietro: () -> Unit,
    vm: MemoViewModel = viewModel(),
) {
    val stato by vm.state.collectAsStateWithLifecycle()

    // Il dettaglio si tiene per id e non per copia della scheda: dopo un
    // salvataggio l'elenco si ricarica, e con la copia si continuerebbe a
    // leggere la versione di prima.
    var apertaId by remember { mutableStateOf<String?>(null) }
    var inCompilazione by remember { mutableStateOf<Pair<BozzaScheda, String?>?>(null) }

    val aperta = apertaId?.let { id -> stato.schede.firstOrNull { it.id == id } }
    // Una nota non ha una vista propria: se la scheda aperta è (o è tornata) una
    // nota si ricade nell'elenco, invece di restare su una schermata vuota.
    val inVista = stato.vista
        ?.let { v -> stato.scheda(v.schedaId) }
        ?.takeIf { it.tipo != TipoScheda.NOTA }

    // Editor, vista e dettaglio non sono destinazioni di navigazione ma
    // schermate che prendono il posto dell'elenco: senza intercettare il tasto
    // indietro si uscirebbe da Memo invece di chiudere quello che è aperto —
    // portandosi via una registrazione a metà.
    BackHandler(enabled = inCompilazione != null) { inCompilazione = null }
    BackHandler(enabled = inCompilazione == null && inVista != null) { vm.chiudiVista() }
    BackHandler(enabled = inCompilazione == null && inVista == null && aperta != null) {
        apertaId = null
    }

    inCompilazione?.let { (bozza, id) ->
        MemoForm(
            bozzaIniziale = bozza,
            id = id,
            immaginiEsistenti = id?.let { stato.immagini[it] }.orEmpty(),
            categorie = stato.categorie,
            onAnnulla = { inCompilazione = null },
            onSalva = { compilata, nuove, daTogliere ->
                vm.salva(id, compilata, nuove, daTogliere) { inCompilazione = null }
            },
        )
        return
    }

    // Una lista e un diario si aprono nella **loro** vista, non nell'editor: la
    // cosa che si fa più spesso su di loro — spuntare una voce, aggiungere una
    // registrazione — non è modificare la scheda. All'editor si arriva da lì.
    if (inVista != null && stato.vista != null) {
        val vista = stato.vista!!
        val apriEditor = {
            inCompilazione = BozzaScheda.da(inVista, vista.voci, vista.misure) to inVista.id
        }
        when (inVista.tipo) {
            TipoScheda.LISTA -> MemoListaView(
                scheda = inVista,
                vista = vista,
                onIndietro = { vm.chiudiVista() },
                onModifica = apriEditor,
                onSpunta = vm::spuntaVoce,
                onAggiungi = vm::aggiungiVoce,
                onElimina = vm::eliminaVoce,
            )
            TipoScheda.DIARIO -> MemoDiarioView(
                scheda = inVista,
                vista = vista,
                onIndietro = { vm.chiudiVista() },
                onModifica = apriEditor,
                onSalvaRegistrazione = { id, titolo, data, nota, misure, onFatto ->
                    vm.salvaRegistrazione(id, titolo, data, nota, misure, onFatto)
                },
                onEliminaRegistrazione = vm::eliminaRegistrazione,
            )
            // Esclusa dal `takeIf` qui sopra: resta per far chiudere il `when`.
            TipoScheda.NOTA -> Unit
        }
        return
    }

    if (aperta != null) {
        MemoDettaglio(
            scheda = aperta,
            immagini = stato.immagini[aperta.id].orEmpty(),
            categorie = stato.categorie,
            onIndietro = { apertaId = null },
            onModifica = { inCompilazione = BozzaScheda.da(aperta) to aperta.id },
            onFissa = { vm.cambiaFissata(aperta) },
            onElimina = { vm.elimina(aperta.id) { apertaId = null } },
            onChiediImmagini = { vm.caricaImmagini(aperta.id) },
        )
        return
    }

    Scaffold(
        topBar = {
            GarsalTopBar(
                titolo = stato.tipo?.plurale ?: "In evidenza",
                onIndietro = onIndietro,
                azioni = {
                    // Il 🙈/👁 compare **solo a modalità nascosta accesa**, come
                    // sul web: a modalità spenta non c'è niente da alzare, e un
                    // pulsante che non fa niente è un invito a chiedersi perché.
                    if (stato.modalitaNascosta) {
                        Text(
                            text = if (stato.soloRiservate) "👁" else "🙈",
                            fontSize = 18.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { vm.cambiaFiltroRiservate() }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                    Text(
                        text = "${stato.visibili.size}",
                        color = Palette.light,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
            )
        },
        floatingActionButton = {
            // Il + crea una scheda **del tipo della Tab** invece di chiedere
            // quale: da 📌 Fissa, che i tre tipi li attraversa, nasce una nota.
            val tipo = stato.tipo ?: TipoScheda.NOTA
            FloatingActionButton(
                onClick = { inCompilazione = BozzaScheda(tipo = tipo) to null },
                containerColor = BluMemo,
                contentColor = Palette.light,
            ) { Text("+", fontSize = 26.sp) }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            Tab(stato, vm)

            OutlinedTextField(
                value = stato.ricerca,
                onValueChange = vm::cerca,
                label = {
                    Text("Cerca fra le ${(stato.tipo?.plurale ?: "fissate").lowercase()}")
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            )

            Filtri(stato, vm)

            stato.errore?.let { messaggio ->
                Text(
                    text = messaggio,
                    color = Palette.danger,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .clickable { vm.scartaMessaggi() },
                )
            }

            Box(Modifier.fillMaxSize()) {
                val elenco = stato.visibili
                when {
                    stato.caricamento && stato.schede.isEmpty() ->
                        CircularProgressIndicator(
                            color = BluMemo,
                            modifier = Modifier.align(Alignment.Center),
                        )

                    elenco.isEmpty() -> Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = if (stato.ricerca.isBlank()) stato.tipo?.icona ?: "📌" else "🔍",
                            fontSize = 40.sp,
                        )
                        // `stato` è una proprietà delegata: `stato.tipo` non si
                        // smart-casta, va letto in una variabile.
                        val tipoAperto = stato.tipo
                        Text(
                            text = when {
                                stato.ricerca.isNotBlank() ->
                                    "Nessun risultato per «${stato.ricerca}»"
                                stato.soloRiservate -> "Nessuna scheda riservata."
                                tipoAperto == null -> "Nessuna scheda in evidenza."
                                else -> "Nessuna ${tipoAperto.etichetta.lowercase()}. " +
                                    "Creane una col +"
                            },
                            color = Palette.muted,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }

                    else -> LazyColumn(
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(elenco, key = { it.id }) { scheda ->
                            SchedaCard(
                                scheda = scheda,
                                categorie = stato.categorie,
                                onApri = {
                                    // `openCard()` del web: una nota si apre in
                                    // lettura, una lista e un diario nella loro
                                    // vista.
                                    if (scheda.tipo == TipoScheda.NOTA) apertaId = scheda.id
                                    else vm.apriVista(scheda)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Le quattro Tab: una per tipo più 📌 Fissa.
 *
 * Non esiste una vista che mescola i tipi — ogni Tab ha la sua ricerca, il suo
 * ordinamento e il suo filtro categoria — e **📌 Fissa è l'unica che li
 * attraversa**, che è la ragione per cui il segno del tipo resta sulla scheda
 * anche ora che ogni Tab ne mostra uno solo.
 */
@Composable
private fun Tab(stato: MemoState, vm: MemoViewModel) {
    val etichette = TipoScheda.entries.map { "${it.icona} ${it.plurale}" } + "📌 Fissa"
    val larghezza = larghezzaPulsanti(etichette)
    RigaScorrevole(
        Arrangement.spacedBy(6.dp),
        Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        TipoScheda.entries.forEach { tipo ->
            Etichetta(
                testo = "${tipo.icona} ${tipo.plurale}",
                attiva = stato.tipo == tipo,
                larghezza = larghezza,
                onTocca = { vm.apriTab(tipo) },
            )
        }
        Etichetta(
            testo = "📌 Fissa",
            attiva = stato.tipo == null,
            larghezza = larghezza,
            onTocca = { vm.apriTab(null) },
        )
    }
}

@Composable
private fun Filtri(stato: MemoState, vm: MemoViewModel) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Tendina(
            etichetta = "Ordina per",
            scelto = stato.ordinamento.etichetta,
            voci = Ordinamento.entries.map { it.name to it.etichetta },
        ) { scelta -> vm.ordina(Ordinamento.valueOf(scelta)) }

        // I conteggi seguono la Tab aperta: con «Liste» attiva una categoria
        // che ha solo note mostra 0, e sparisce dai filtri.
        TendinaFacoltativa(
            etichetta = "Categoria",
            tutte = "Tutte (${stato.quanteInTab})",
            scelto = stato.filtroCategoria,
            voci = stato.categorie
                .filter { stato.quante(it.id) > 0 }
                .map { it.id to "${it.etichetta} (${stato.quante(it.id)})" },
        ) { scelta -> vm.filtraCategoria(scelta) }
    }
}

@Composable
internal fun Etichetta(
    testo: String,
    attiva: Boolean,
    colore: Color = BluMemo,
    larghezza: Dp? = null,
    onTocca: () -> Unit,
) {
    Text(
        text = testo,
        color = if (attiva) Palette.light else Palette.dark,
        fontWeight = if (attiva) FontWeight.Bold else FontWeight.Normal,
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = Modifier
            .then(larghezza?.let { Modifier.width(it) } ?: Modifier)
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (attiva) colore else Palette.inputBg)
            .clickable(onClick = onTocca)
            .padding(horizontal = if (larghezza == null) 12.dp else 0.dp, vertical = 8.dp),
    )
}

/** Il segno del tipo e quello 🙈 Riservato, come le `.kind-badge` del web. */
@Composable
private fun Segno(testo: String, colore: Color) {
    Text(
        text = testo,
        color = Palette.light,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(colore)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/**
 * La scheda nell'elenco: colore, titolo, un pezzo del testo, le categorie, la
 * data e i segni — le stesse cose di `renderCardTile()`, compresa la riga di
 * conteggio che cambia col tipo (☑️ 3/7, 📊 12 · ultima 14/08/2026).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SchedaCard(
    scheda: MmScheda,
    categorie: List<CmCategoria>,
    onApri: () -> Unit,
) {
    // Il colore della scheda tinge tutta la card, dove il web ne fa una
    // striscia a sinistra: là le schede stanno su una griglia bianca e la
    // striscia basta a distinguerle, qui sono una sotto l'altra a tutta
    // larghezza, e il colore pieno si vede scorrendo col pollice. Senza un
    // colore scelto a mano (il bianco di default) lo sfondo è azzurino,
    // giallino per i soli diari — la stessa eccezione del web.
    val coloreScheda = coloreDaHex(scheda.colore.takeIf { it != MmScheda.BIANCO })
        ?: if (scheda.tipo == TipoScheda.DIARIO) GialloSchedaMemo else AzzurroSchedaMemo
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onApri),
        colors = CardDefaults.cardColors(containerColor = coloreScheda),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = scheda.titolo.ifBlank { "Senza titolo" },
                    color = if (scheda.titolo.isBlank()) Palette.muted else Palette.dark,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )

                // Il tipo si vede sempre sulla scheda, sotto il titolo: una
                // lista e un diario si aprono diversamente da una nota, e chi
                // tocca deve saperlo prima.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Segno("${scheda.tipo.icona} ${scheda.tipo.etichetta}", BluMemo)
                    if (scheda.riservato) Segno("🙈 Riservato", ViolaMemo)
                }

                if (scheda.anteprima.isNotBlank()) {
                    Text(
                        text = scheda.anteprima,
                        color = Palette.muted,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                scheda.avanzamento?.let { quota ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Palette.border)
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(quota)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Palette.success)
                        )
                    }
                }

                if (scheda.categorie.isNotEmpty()) {
                    // Le etichette di categoria sono testo libero: a differenza dei
                    // pulsanti-azione qui non ha senso una larghezza comune, che
                    // sprecherebbe spazio sulle etichette corte per far posto alla
                    // più lunga. Restano larghe quanto il loro testo, ma scorrono
                    // su una riga sola invece di andare a capo.
                    RigaScorrevole(Arrangement.spacedBy(6.dp)) {
                        scheda.categorie.forEach { id ->
                            categorie.firstOrNull { it.id == id }?.let { cat ->
                                Text(
                                    text = cat.etichetta,
                                    color = Palette.light,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier
                                        .padding(vertical = 2.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(coloreDaHex(cat.colore) ?: Palette.muted)
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                }

                Text(
                    text = buildString {
                        append(scheda.dataItaliana)
                        when (scheda.tipo) {
                            TipoScheda.LISTA ->
                                append("  ·  ☑️ ${scheda.vociFatte}/${scheda.vociTotali}")
                            TipoScheda.DIARIO -> {
                                append("  ·  📊 ${scheda.registrazioni}")
                                scheda.ultimaRegistrazione?.let {
                                    append(" · ultima ${giorno(it)}")
                                }
                            }
                            TipoScheda.NOTA -> Unit
                        }
                        if (scheda.fissata) append("  ·  📌")
                        if (scheda.immagini > 0) append("  ·  📷 ${scheda.immagini}")
                    },
                    color = Palette.muted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
