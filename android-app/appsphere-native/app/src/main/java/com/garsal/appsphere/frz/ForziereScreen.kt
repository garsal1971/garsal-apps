package com.garsal.appsphere.frz

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.garsal.appsphere.core.GarsalTopBar
import com.garsal.appsphere.core.Palette
import com.garsal.appsphere.core.Pillola
import com.garsal.appsphere.core.RigaScorrevole
import java.io.File

/**
 * Il Forziere sul telefono.
 *
 * ⚠️ **Quattro cose e non di più**: sbloccare, sfogliare, aprire un documento,
 * metterne dentro uno. Creazione del forziere, collaudo delle 24 parole, export
 * `.7z` e cambio della passphrase restano su `forziere.html` — si fanno una
 * volta, vanno fatte bene, e il telefono è il posto sbagliato per ciascuna.
 *
 * ⚠️ **Il forziere si chiude quando l'app va in secondo piano**, oltre che dopo
 * dieci minuti. È l'equivalente del `visibilitychange` della pagina: uno
 * schermo lasciato acceso su un elenco di documenti è esattamente la cosa da
 * cui il forziere protegge. Rientrare costa la passphrase o l'impronta, cioè
 * due secondi.
 */
@Composable
fun ForziereScreen(onIndietro: () -> Unit) {
    val ctx = LocalContext.current
    val vm: ForziereViewModel = viewModel()
    val s by vm.stato.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.avvia(ctx) }

    // ⚠️ Non basta il timer: un'app messa via non lo fa scorrere più in fretta,
    // e resterebbe aperta finché non la si riapre. `ON_STOP` è il momento in cui
    // lo schermo non è più davanti a nessuno.
    val ciclo = LocalLifecycleOwner.current
    DisposableEffect(ciclo) {
        val oss = LifecycleEventObserver { _, ev ->
            if (ev == Lifecycle.Event.ON_STOP) vm.chiudi(ctx, "l'app è passata in secondo piano")
        }
        ciclo.lifecycle.addObserver(oss)
        onDispose { ciclo.lifecycle.removeObserver(oss) }
    }

    Scaffold(
        topBar = {
            GarsalTopBar(
                titolo = "🔐 Forziere",
                onIndietro = onIndietro,
                azioni = {
                    if (s.fase == FaseForziere.APERTO) {
                        Text(
                            "Chiudi",
                            color = Palette.light,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clickable { vm.chiudi(ctx, "l'hai chiuso tu") }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (s.fase == FaseForziere.APERTO && s.visione == null) FabMetti(vm)
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (s.fase) {
                FaseForziere.CARICO -> Attesa()
                FaseForziere.ASSENTE -> Assente(s.errore)
                FaseForziere.CHIUSO -> Sblocco(vm, s)
                FaseForziere.APERTO -> {
                    val v = s.visione
                    if (v == null) Dentro(vm, s) else Visore(vm, v)
                }
            }
        }
    }
}

// ── Il pulsante che mette dentro ────────────────────────────────────────────

@Composable
private fun FabMetti(vm: ForziereViewModel) {
    val ctx = LocalContext.current
    val scelta = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.metti(ctx, uri)
    }
    ExtendedFloatingActionButton(
        onClick = { vm.tocca(); vm.apriUnaFinestraDiSistema(); scelta.launch("*/*") },
        containerColor = Palette.dark,
        contentColor = Palette.light,
    ) { Text("＋ Metti dentro") }
}

// ── Le tre schermate ────────────────────────────────────────────────────────

@Composable
private fun Attesa() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
private fun Assente(errore: String?) {
    Column(
        Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Qui non c'è ancora un forziere", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(
            "Il forziere si crea dal computer, su forziere.html: si scelgono le 24 parole, " +
                "si fa il collaudo del recupero e si mette la passphrase. Sono cose che si " +
                "fanno una volta sola e vanno fatte con calma — il telefono è il posto " +
                "sbagliato per ciascuna.",
            color = Palette.muted,
        )
        if (errore != null) Avviso("⚠️ $errore", Palette.danger)
    }
}

@Composable
private fun Sblocco(vm: ForziereViewModel, s: ForziereState) {
    val ctx = LocalContext.current
    var pw by remember { mutableStateOf("") }
    var parole by remember { mutableStateOf("") }
    var conParole by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("🔐 Il forziere è chiuso", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        if (s.motivoChiusura != null) Text(s.motivoChiusura, color = Palette.muted)

        if (!conParole) {
            OutlinedTextField(
                value = pw,
                onValueChange = { pw = it },
                label = { Text("Passphrase") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { vm.apriConPassphrase(pw); pw = "" },
                colors = ButtonDefaults.buttonColors(containerColor = Palette.success),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Apri il forziere") }

            if (s.improntaDisponibile && s.improntaRegistrata) {
                Button(
                    onClick = {
                        val cipher = ForziereBiometria.decifratore(ctx)
                        if (cipher == null) {
                            vm.togliImpronta(ctx)
                        } else {
                            chiediImpronta(
                                ctx, "Apri il forziere", cipher,
                                onOk = { c -> vm.apriConImpronta(ctx, c) },
                                onNiente = {},
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.accent),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("👆 Sblocca con l'impronta") }
            }

            TextButton(onClick = { conParole = true }) { Text("Ho dimenticato la passphrase — uso le 24 parole") }
        } else {
            Text("Tutto minuscolo, separate da uno spazio.", color = Palette.muted)
            OutlinedTextField(
                value = parole,
                onValueChange = { parole = it },
                label = { Text("Le 24 parole") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { vm.apriConParole(parole); parole = "" },
                colors = ButtonDefaults.buttonColors(containerColor = Palette.success),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Apri il forziere") }
            // ⚠️ Entrando con le parole la passphrase è persa, e si rifà **dal
            // PC**: cambiarla vuol dire riscrivere `scorciatoia.gpg`, che è una
            // delle operazioni che di proposito non stanno qui.
            Avviso(
                "Sei entrato con le 24 parole: vuol dire che la passphrase non la ricordi più. " +
                    "Rifalla dal computer, su forziere.html → Impostazioni.",
                Palette.warning,
            )
            TextButton(onClick = { conParole = false }) { Text("Torna alla passphrase") }
        }

        if (s.stato != null) Text(s.stato, color = Palette.muted)
        if (s.errore != null) Avviso("❌ ${s.errore}", Palette.danger)
    }
}

@Composable
private fun Dentro(vm: ForziereViewModel, s: ForziereState) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize()) {

        if (s.avanzamento != null || s.stato != null) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                if (s.stato != null) Text(s.stato, color = Palette.muted, fontSize = 13.sp)
                if (s.avanzamento != null) {
                    LinearProgressIndicator(
                        progress = { s.avanzamento },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                }
            }
        }
        if (s.errore != null) {
            Box(Modifier.padding(horizontal = 12.dp)) { Avviso("❌ ${s.errore}", Palette.danger) }
        }
        if (s.indiciRotti) {
            Box(Modifier.padding(horizontal = 12.dp)) {
                Avviso(
                    "Gli indici su Drive non si sono riscritti. Il documento è dentro e la riga " +
                        "c'è: a mancare è la copia che serve a orientarsi col solo Drive. " +
                        "Si rifà dal computer, Impostazioni → 🔄 Rifai gli indici.",
                    Palette.warning,
                )
            }
        }

        // ⚠️ La barra degli scomparti **scorre e non va a capo**: coi caratteri
        // di sistema grandi le pillole andrebbero su tre righe mangiandosi
        // l'elenco. Stessa regola delle righe di pulsanti.
        RigaScorrevole(
            Arrangement.spacedBy(8.dp),
            Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Pillola("Tutti", if (s.filtro == Filtro.Tutti) Palette.dark else Palette.muted) {
                vm.filtra(Filtro.Tutti)
            }
            s.scomparti.forEach { b ->
                Pillola(
                    "${b.emoji} ${b.nome}",
                    if (s.filtro == Filtro.Uno(b.id)) Palette.dark else Palette.muted,
                ) { vm.filtra(Filtro.Uno(b.id)) }
            }
            // ⚠️ «Senza scomparto» compare **solo se serve**: senza nessuno
            // scomparto sarebbe un doppione di «Tutti».
            if (s.scomparti.isNotEmpty()) {
                Pillola(
                    "Senza scomparto",
                    if (s.filtro == Filtro.Senza) Palette.dark else Palette.muted,
                ) { vm.filtra(Filtro.Senza) }
            }
        }

        // ⚠️ L'impronta si registra **da dentro**, con le 24 parole già in
        // memoria: chiederle di nuovo per registrarle sarebbe farsele riscrivere
        // per niente. E si toglie da qui, che è dove si vede che c'è.
        if (s.improntaDisponibile) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (s.improntaRegistrata) {
                    Text("👆 L'impronta apre questo forziere", color = Palette.muted, fontSize = 13.sp)
                    TextButton(onClick = { vm.togliImpronta(ctx) }) { Text("Togli") }
                } else {
                    TextButton(onClick = {
                        val cipher = runCatching { ForziereBiometria.cifratore() }.getOrNull()
                        if (cipher != null) chiediImpronta(
                            ctx, "Registra l'impronta", cipher,
                            onOk = { c -> vm.registraImpronta(ctx, c) },
                            onNiente = {},
                        )
                    }) { Text("👆 Aprilo con l'impronta la prossima volta") }
                }
            }
        }

        OutlinedTextField(
            value = s.cerca,
            onValueChange = { vm.cerca(it) },
            label = { Text("Cerca fra i nomi") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        )

        val elenco = s.visibili
        if (elenco.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (s.documenti.isEmpty()) "Il forziere è vuoto." else "Nessun documento qui.",
                    color = Palette.muted,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(elenco, key = { it.id }) { d ->
                    SchedaDocumento(d, s.miniature[d.id]) { vm.apriDocumento(ctx, d) }
                }
            }
        }
    }
}

@Composable
private fun SchedaDocumento(d: FrzDocumento, mini: ByteArray?, onApri: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Palette.cardBg),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Palette.border, RoundedCornerShape(12.dp))
            .clickable(onClick = onApri),
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1.4f).background(Palette.inputBg),
            contentAlignment = Alignment.Center,
        ) {
            val bmp = remember(mini) {
                mini?.let {
                    runCatching { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) }.getOrNull()
                }
            }
            if (bmp != null) {
                Image(bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize())
            } else {
                Text(iconaDi(d.meta.tipo), fontSize = 34.sp)
            }
        }
        Column(Modifier.padding(10.dp)) {
            Text(
                d.meta.nome.ifBlank { "(senza nome)" },
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(peso(d.meta.size ?: d.pesoCifrato), color = Palette.muted, fontSize = 12.sp)
        }
    }
}

// ── Il visore ───────────────────────────────────────────────────────────────

@Composable
private fun Visore(vm: ForziereViewModel, v: Visione) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                v.doc.meta.nome.ifBlank { "(senza nome)" },
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(onClick = { vm.chiudiVisore(ctx) }) { Text("Chiudi") }
        }
        Box(Modifier.fillMaxSize()) {
            when {
                v.immagine != null -> {
                    val bmp = remember(v.immagine) {
                        android.graphics.BitmapFactory.decodeByteArray(v.immagine, 0, v.immagine.size)
                    }
                    if (bmp != null) {
                        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            Image(
                                bmp.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    } else NonSiApre("questa immagine il telefono non la sa decodificare")
                }
                v.pdf != null -> VisorePdf(v.pdf)
                v.testo != null -> Text(
                    v.testo,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState())
                        .padding(12.dp),
                )
                else -> NonSiApre(v.nonSiApre ?: "")
            }
        }
    }
}

/**
 * ⚠️ Il PDF si disegna **dentro l'app** con `PdfRenderer`, non passandolo a un
 * altro programma: un `Intent` ne farebbe una copia in chiaro dentro un'app che
 * non è questa, e da lì il forziere non la riprende più. Il temporaneo sta nella
 * cache privata e se ne va chiudendo il visore.
 */
@Composable
private fun VisorePdf(file: File) {
    val pagine = remember(file) {
        runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { r ->
                    (0 until r.pageCount).map { i ->
                        r.openPage(i).use { p ->
                            val larghezza = 1240
                            val altezza = (larghezza.toFloat() / p.width * p.height).toInt().coerceAtLeast(1)
                            Bitmap.createBitmap(larghezza, altezza, Bitmap.Config.ARGB_8888).also { b ->
                                b.eraseColor(android.graphics.Color.WHITE)
                                p.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            }
                        }
                    }
                }
            }
        }.getOrNull()
    }
    if (pagine == null) {
        NonSiApre("questo PDF non si lascia disegnare qui: aprilo dal computer")
    } else {
        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(pagine) { b ->
                Image(
                    b.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().border(1.dp, Palette.border),
                )
            }
        }
    }
}

@Composable
private fun NonSiApre(perche: String) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Qui non si apre", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(
            "Il telefono mostra immagini, PDF e testo. Per il resto ($perche) il documento " +
                "si apre dal computer, dove il visore del Forziere sa fare di più. " +
                "Il file è dentro e sta bene: qui manca il modo di guardarlo, non il documento.",
            color = Palette.muted,
        )
    }
}

// ── Utilità di schermo ──────────────────────────────────────────────────────

@Composable
private fun Avviso(testo: String, colore: Color) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(colore.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
            .border(1.dp, colore.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .padding(12.dp),
    ) { Text(testo, color = Palette.dark, fontSize = 13.sp) }
}

private fun iconaDi(tipo: String): String = when {
    tipo.startsWith("image/") -> "🖼️"
    tipo == "application/pdf" -> "📕"
    tipo.startsWith("video/") -> "🎬"
    tipo.startsWith("audio/") -> "🎵"
    tipo.startsWith("text/") -> "📄"
    else -> "📦"
}

private fun peso(n: Long): String = when {
    n < 1024 -> "$n B"
    n < 1_048_576 -> "${n / 1024} kB"
    n < 1_073_741_824 -> String.format("%.1f MB", n / 1_048_576.0)
    else -> String.format("%.2f GB", n / 1_073_741_824.0)
}

/**
 * L'impronta con un `CryptoObject`: senza un'autenticazione fresca quel
 * `Cipher` non produce un byte.
 *
 * ⚠️ Solo `BIOMETRIC_STRONG`: legare la chiave al PIN del telefono vorrebbe
 * dire che il forziere si apre con quello che si digita davanti a chiunque in
 * autobus.
 */
private fun chiediImpronta(
    ctx: Context,
    titolo: String,
    cipher: javax.crypto.Cipher,
    onOk: (javax.crypto.Cipher) -> Unit,
    onNiente: () -> Unit,
) {
    val activity = ctx as? FragmentActivity ?: return onNiente()
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val c = result.cryptoObject?.cipher
                if (c != null) onOk(c) else onNiente()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onNiente()
        },
    )
    prompt.authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("🔐 Forziere")
            .setSubtitle(titolo)
            .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setNegativeButtonText("Annulla")
            .build(),
        BiometricPrompt.CryptoObject(cipher),
    )
}
