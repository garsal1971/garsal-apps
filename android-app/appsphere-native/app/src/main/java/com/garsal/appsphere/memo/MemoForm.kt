package com.garsal.appsphere.memo

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.garsal.appsphere.core.GarsalTopBar
import com.garsal.appsphere.core.Palette
import com.garsal.appsphere.core.coloreDaHex
import kotlinx.coroutines.launch

/**
 * La scheda che si sta scrivendo. `testo` è nei **marcatori** di [MemoHtml],
 * non in HTML: la conversione avviene aprendo il form e salvando.
 */
data class BozzaScheda(
    val titolo: String = "",
    val testo: String = "",
    val fissata: Boolean = false,
    val colore: String = MmScheda.BIANCO,
    val categorie: List<String> = emptyList(),
) {
    /** Come `saveCard()`: basta il titolo **oppure** il contenuto. */
    val valida: Boolean get() = titolo.isNotBlank() || testo.isNotBlank()

    companion object {
        fun da(scheda: MmScheda) = BozzaScheda(
            titolo = scheda.titolo,
            testo = MemoHtml.aMarcatori(scheda.contenuto),
            fissata = scheda.fissata,
            colore = scheda.colore,
            categorie = scheda.categorie,
        )
    }
}

/**
 * Creazione e modifica di una scheda: titolo, testo con la barra di
 * formattazione, colore, categorie, evidenza e foto (con OCR).
 *
 * ⚠️ Il testo si scrive **con i marcatori** (`**grassetto**`, `# titolo`,
 * `- elenco`…), che i pulsanti della barra infilano attorno a quello che si è
 * selezionato; l'occhio 👁 mostra come verrà. È la scelta spiegata in
 * [MemoHtml]: così quello che il web ha scritto in HTML torna in HTML uguale
 * anche passando da qui.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MemoForm(
    bozzaIniziale: BozzaScheda,
    id: String?,
    immaginiEsistenti: List<MmImmagine>,
    categorie: List<CmCategoria>,
    onAnnulla: () -> Unit,
    onSalva: (BozzaScheda, List<FotoInAttesa>, List<MmImmagine>) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var b by remember { mutableStateOf(bozzaIniziale) }
    // Il testo passa da TextFieldValue e non da String perché i pulsanti della
    // barra devono sapere **cosa è selezionato**: senza la selezione
    // «grassetto» non saprebbe attorno a cosa mettersi.
    var testo by remember { mutableStateOf(TextFieldValue(bozzaIniziale.testo)) }
    var anteprima by remember { mutableStateOf(false) }

    var nuove by remember { mutableStateOf(listOf<FotoInAttesa>()) }
    var daTogliere by remember { mutableStateOf(listOf<MmImmagine>()) }
    var conOcr by remember { mutableStateOf(false) }
    var avviso by remember { mutableStateOf<String?>(null) }
    var uriScatto by remember { mutableStateOf<Uri?>(null) }

    fun accodaTesto(estratto: String) {
        val pulito = estratto.trim()
        if (pulito.isEmpty()) {
            avviso = "⚠️ Nessun testo trovato nella foto"
            return
        }
        // Stessa regola del web: se c'era già del testo, il pezzo nuovo arriva
        // dopo una linea di separazione invece di attaccarsi a quello di prima.
        val prima = testo.text.trimEnd()
        val unito = if (prima.isBlank()) pulito else "$prima\n\n---\n\n$pulito"
        testo = TextFieldValue(unito, TextRange(unito.length))
        avviso = "✅ Testo estratto (${pulito.length} caratteri)"
    }

    fun aggiungiFoto(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val descritte = uris.mapNotNull { MemoFoto.descrivi(context, it) }
        if (descritte.size < uris.size) {
            avviso = "⚠️ Qualche foto supera i 5 MB e non è stata aggiunta"
        }
        nuove = nuove + descritte
        if (conOcr && descritte.isNotEmpty()) {
            avviso = "🔍 OCR in corso…"
            scope.launch {
                descritte.forEach { foto ->
                    runCatching { MemoFoto.testoDaFoto(context, foto.uri) }
                        .onSuccess { accodaTesto(it) }
                        .onFailure { avviso = "❌ OCR non riuscito: ${it.message ?: "errore"}" }
                }
            }
        }
    }

    val galleria = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(5)
    ) { uris -> aggiungiFoto(uris) }

    val fotocamera = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { scattata -> if (scattata) uriScatto?.let { aggiungiFoto(listOf(it)) } }

    Scaffold(
        topBar = {
            GarsalTopBar(
                titolo = if (id == null) "📝 Nuova scheda" else "✏️ Modifica scheda",
                onIndietro = onAnnulla,
                azioni = {
                    val pronta = b.copy(testo = testo.text).valida
                    Text(
                        text = "Salva",
                        color = if (pronta) Palette.light else Palette.light.copy(alpha = 0.4f),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = pronta) {
                                onSalva(b.copy(testo = testo.text), nuove, daTogliere)
                            }
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
                value = b.titolo,
                onValueChange = { b = b.copy(titolo = it) },
                label = { Text("Titolo") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // ── Barra di formattazione ──────────────────────────────────
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Tasto("B", "Grassetto") { testo = avvolgi(testo, "**") }
                Tasto("I", "Corsivo") { testo = avvolgi(testo, "*") }
                Tasto("U", "Sottolineato") { testo = avvolgi(testo, "__") }
                Tasto("S", "Barrato") { testo = avvolgi(testo, "~~") }
                Tasto("H1", "Titolo grande") { testo = prefissa(testo, "# ") }
                Tasto("H2", "Titolo piccolo") { testo = prefissa(testo, "## ") }
                Tasto("•", "Elenco puntato") { testo = prefissa(testo, "- ") }
                Tasto("1.", "Elenco numerato") { testo = prefissa(testo, "1. ") }
                Tasto("🔗", "Collegamento") { testo = collegamento(testo) }
                Tasto("—", "Linea") { testo = inserisci(testo, "\n---\n") }
                Tasto(
                    testo = if (anteprima) "✏️" else "👁",
                    descrizione = if (anteprima) "Torna a scrivere" else "Anteprima",
                    attivo = anteprima,
                ) { anteprima = !anteprima }
            }

            if (anteprima) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(coloreDaHex(b.colore) ?: Palette.inputBg)
                        .padding(12.dp)
                ) {
                    val html = MemoHtml.aHtml(testo.text)
                    Text(
                        text = remember(html) {
                            runCatching { AnnotatedString.fromHtml(html) }
                                .getOrElse { AnnotatedString(testo.text) }
                        },
                        color = Palette.dark,
                    )
                }
            } else {
                OutlinedTextField(
                    value = testo,
                    onValueChange = { testo = it },
                    label = { Text("Contenuto") },
                    // Niente altezza fissa: è un minimo, e col testo lungo o coi
                    // caratteri di sistema grandi il campo cresce.
                    modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp),
                )
            }

            // ── Colore ──────────────────────────────────────────────────
            Text("Colore", color = Palette.muted, style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MmScheda.COLORI.forEach { (hex, nome) ->
                    val scelto = b.colore.equals(hex, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(coloreDaHex(hex) ?: Palette.cardBg)
                            .clickable { b = b.copy(colore = hex) },
                        contentAlignment = Alignment.Center,
                    ) {
                        // Il bianco su fondo bianco sparirebbe: il segno di
                        // spunta e un bordo dicono qual è quello scelto.
                        Text(
                            text = if (scelto) "✓" else "",
                            color = Palette.dark,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            // ── Categorie ───────────────────────────────────────────────
            Text("Categorie", color = Palette.muted, style = MaterialTheme.typography.bodyMedium)
            if (categorie.isEmpty()) {
                Text(
                    text = "Nessuna categoria: si creano da AppSphere → Dati Comuni.",
                    color = Palette.muted,
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    categorie.forEach { cat ->
                        val scelta = cat.id in b.categorie
                        Text(
                            text = cat.etichetta,
                            color = if (scelta) Palette.light else Palette.dark,
                            fontWeight = if (scelta) FontWeight.Bold else FontWeight.Normal,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .padding(vertical = 2.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (scelta) coloreDaHex(cat.colore) ?: BluMemo
                                    else Palette.inputBg
                                )
                                .clickable {
                                    b = b.copy(
                                        categorie = if (scelta) b.categorie - cat.id
                                        else b.categorie + cat.id
                                    )
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }

            // ── Evidenza ────────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = b.fissata, onCheckedChange = { b = b.copy(fissata = it) })
                Text(
                    text = "📌 Tienila in evidenza",
                    color = Palette.dark,
                    modifier = Modifier.clickable { b = b.copy(fissata = !b.fissata) },
                )
            }

            // ── Foto ────────────────────────────────────────────────────
            Text("Foto", color = Palette.muted, style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = conOcr, onCheckedChange = { conOcr = it })
                Text(
                    text = "🔍 Estrai il testo dalla foto (OCR)",
                    color = Palette.dark,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tasto("🖼 Galleria", "Scegli dalla galleria") {
                    galleria.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
                Tasto("📷 Fotocamera", "Scatta una foto") {
                    val uri = MemoFoto.uriPerScatto(context)
                    uriScatto = uri
                    fotocamera.launch(uri)
                }
            }

            avviso?.let { messaggio ->
                Text(
                    text = messaggio,
                    color = Palette.muted,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.clickable { avviso = null },
                )
            }

            val rimaste = immaginiEsistenti.filterNot { it in daTogliere }
            if (rimaste.isNotEmpty() || nuove.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rimaste.forEach { foto ->
                        Miniatura(modello = foto.url) { daTogliere = daTogliere + foto }
                    }
                    nuove.forEach { foto ->
                        Miniatura(modello = foto.uri) { nuove = nuove - foto }
                    }
                }
                if (daTogliere.isNotEmpty() || nuove.isNotEmpty()) {
                    Text(
                        text = "Le foto si caricano — e quelle tolte si cancellano — al salvataggio.",
                        color = Palette.muted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun Miniatura(modello: Any?, onTogli: () -> Unit) {
    Box {
        AsyncImage(
            model = modello,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Palette.inputBg),
        )
        Text(
            text = "✕",
            color = Palette.light,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .clip(CircleShape)
                .background(Color(0xCC111111))
                .clickable(onClick = onTogli)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun Tasto(
    testo: String,
    descrizione: String,
    attivo: Boolean = false,
    onTocca: () -> Unit,
) {
    Text(
        text = testo,
        color = if (attivo) Palette.light else Palette.dark,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        modifier = Modifier
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (attivo) BluMemo else Palette.inputBg)
            .clickable(onClick = onTocca)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
}

// ── I pulsanti della barra: cosa fanno al testo ─────────────────────────────
//
// Sono funzioni pure sul TextFieldValue, che è testo più selezione: infilano i
// marcatori e rimettono il cursore dove uno se lo aspetta — dentro i marcatori
// se non c'era niente di selezionato, dopo il pezzo formattato se c'era.

internal fun avvolgi(v: TextFieldValue, marcatore: String): TextFieldValue {
    val inizio = minOf(v.selection.start, v.selection.end)
    val fine = maxOf(v.selection.start, v.selection.end)
    val nuovo = v.text.substring(0, inizio) + marcatore +
        v.text.substring(inizio, fine) + marcatore + v.text.substring(fine)
    val cursore = if (inizio == fine) inizio + marcatore.length
    else fine + marcatore.length * 2
    return TextFieldValue(nuovo, TextRange(cursore))
}

internal fun prefissa(v: TextFieldValue, prefisso: String): TextFieldValue {
    val inizio = minOf(v.selection.start, v.selection.end)
    val inizioRiga = v.text.lastIndexOf('\n', (inizio - 1).coerceAtLeast(0))
        .let { if (it < 0 || inizio == 0) 0 else it + 1 }
    val nuovo = v.text.substring(0, inizioRiga) + prefisso + v.text.substring(inizioRiga)
    return TextFieldValue(nuovo, TextRange(inizio + prefisso.length))
}

internal fun inserisci(v: TextFieldValue, pezzo: String): TextFieldValue {
    val inizio = minOf(v.selection.start, v.selection.end)
    val fine = maxOf(v.selection.start, v.selection.end)
    val nuovo = v.text.substring(0, inizio) + pezzo + v.text.substring(fine)
    return TextFieldValue(nuovo, TextRange(inizio + pezzo.length))
}

/**
 * Il collegamento: `[quello che era selezionato](https://)`, col cursore
 * pronto sull'indirizzo — che è l'unica cosa che resta da scrivere.
 */
internal fun collegamento(v: TextFieldValue): TextFieldValue {
    val inizio = minOf(v.selection.start, v.selection.end)
    val fine = maxOf(v.selection.start, v.selection.end)
    val etichetta = v.text.substring(inizio, fine).ifBlank { "testo" }
    val pezzo = "[$etichetta](https://)"
    val nuovo = v.text.substring(0, inizio) + pezzo + v.text.substring(fine)
    return TextFieldValue(nuovo, TextRange(inizio + pezzo.length - 1))
}
