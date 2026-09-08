package com.garsal.speseingiro

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

/**
 * «Che versione ho, e ce n'è una più nuova?» — più il link per scaricarla.
 *
 * ⚠️ Gemello di `Aggiornamento.kt` di SOS, di Smart Blocker, dell'APK WebView e
 * di `core/Aggiornamento.kt` del nativo: stessa scheda `-latest.json` scritta
 * dal workflow accanto all'APK, stesse sette chiavi, stesso dialogo. Se cambia
 * la forma della scheda in un workflow, cambia negli altri e in
 * `mostraVersione()` di `comandi.html`. Qui la differenza è solo di forma —
 * Compose invece di un AlertDialog di AppCompat.
 *
 * Serve perché il nome dell'APK è fisso (`-latest.apk`): da fuori una build
 * vale l'altra, e scaricare quella di ieri al posto di quella appena pubblicata
 * è indistinguibile da un aggiornamento riuscito.
 */
object Rilascio {

    private const val SITO = "https://garsal.netlify.app"
    private const val BASE = "SpeseInGiro-latest"

    /** Il `?v=` non serve al server: impedisce al browser di riproporre il
     *  pacchetto già scaricato quando l'indirizzo è identico. */
    fun apk(versione: String): String = "$SITO/releases/$BASE.apk?v=$versione"

    data class Scheda(val version: String, val versionCode: Int, val bytes: Long, val sha256: String)

    suspend fun scheda(): Scheda? = withContext(Dispatchers.IO) {
        runCatching {
            // `?t=` per la stessa ragione del `?v=`: senza, una scheda in cache
            // racconterebbe la build di ieri.
            val o = JSONObject(URL("$SITO/releases/$BASE.json?t=${System.currentTimeMillis()}").readText())
            Scheda(o.optString("version"), o.optInt("versionCode"), o.optLong("bytes"), o.optString("sha256"))
        }.getOrNull()
    }

    /** La versione **installata**, letta dal pacchetto e non da BuildConfig: è
     *  quella vera, non quella che il codice credeva di essere.
     *
     *  ⚠️ `longVersionCode` è arrivato con Android 9 e qui il minimo è Android 8:
     *  senza il ramo vecchio la build non passa il lint, e su un telefono
     *  dell'8 sarebbe un errore in faccia proprio aprendo le impostazioni. */
    @Suppress("DEPRECATION")
    fun installata(ctx: Context): Pair<String, Int> {
        val i = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        val codice =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) i.longVersionCode.toInt()
            else i.versionCode
        return (i.versionName ?: "?") to codice
    }
}

@Composable
fun DialogoAggiornamento(onChiudi: () -> Unit) {
    val ctx = LocalContext.current
    val (nome, codice) = remember { Rilascio.installata(ctx) }
    var scheda by remember { mutableStateOf<Rilascio.Scheda?>(null) }
    var stato by remember { mutableStateOf("Controllo cosa c'è pubblicato…") }

    LaunchedEffect(Unit) {
        val s = Rilascio.scheda()
        scheda = s
        stato = when {
            s == null -> "Non sono riuscito a leggere la scheda della build: senza, non posso dire se c'è qualcosa di nuovo."
            s.versionCode > codice -> "C'è la v${s.version} (build ${s.versionCode}), ${"%.1f".format(s.bytes / 1048576.0)} MB."
            else -> "Sei aggiornato: pubblicata la v${s.version} (build ${s.versionCode})."
        }
    }

    AlertDialog(
        onDismissRequest = onChiudi,
        title = { Text("📱 Versione app") },
        text = {
            Text(
                "Installata: v$nome (build $codice)\n\n$stato",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            val s = scheda
            if (s != null) {
                TextButton(onClick = {
                    // Il browser di sistema: è l'unico posto dove l'utente
                    // ritrova il file fra i suoi scaricamenti e dove il gestore
                    // pacchetti lo può installare sopra a questo.
                    ctx.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(Rilascio.apk(s.version)))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                    onChiudi()
                }) { Text(if (s.versionCode > codice) "⬇ Scarica la v${s.version}" else "⬇ Riscarica") }
            }
        },
        dismissButton = { TextButton(onClick = onChiudi) { Text("Chiudi") } },
    )
}
