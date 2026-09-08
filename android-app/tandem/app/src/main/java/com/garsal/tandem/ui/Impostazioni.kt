package com.garsal.tandem.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.garsal.tandem.DialogoAggiornamento
import com.garsal.tandem.Prefs
import com.garsal.tandem.Rilascio
import com.garsal.tandem.UiState

/**
 * Impostazioni: il codice da dettare all'altro, i viaggi di questo telefono,
 * la versione dell'app e l'uscita.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchermataImpostazioni(
    ui: UiState,
    onScegli: (Prefs.Viaggio) -> Unit,
    onNuovoViaggio: () -> Unit,
    onEsci: (String) -> Unit,
    onIndietro: () -> Unit,
) {
    val ctx = LocalContext.current
    var aggiornamento by remember { mutableStateOf(false) }
    var confermaUscita by remember { mutableStateOf(false) }
    val attivo = ui.attivo
    val versione = remember { Rilascio.installata(ctx) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("⚙️ Impostazioni") },
                navigationIcon = { TextButton(onClick = onIndietro) { Text("‹ Indietro") } },
            )
        }
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            attivo?.let { v ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("🔑 Il codice del viaggio", fontWeight = FontWeight.SemiBold)
                        Text(v.codice, style = MaterialTheme.typography.headlineSmall,
                             fontWeight = FontWeight.Bold)
                        Text(
                            "Dettalo all'altro: gli serve per entrare, e a te per rientrare se " +
                                "cambi telefono. Chi ce l'ha entra — trattalo come si tratta il conto.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(
                            onClick = {
                                val i = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "Entra nel viaggio «${v.nome}» su Tandem col codice ${v.codice}"
                                    )
                                }
                                ctx.startActivity(Intent.createChooser(i, "Manda il codice"))
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) { Text("📤 Manda il codice") }
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    Text("🚲 I viaggi di questo telefono",
                         fontWeight = FontWeight.SemiBold,
                         modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    ui.viaggi.forEach { v ->
                        ListItem(
                            headlineContent = { Text(v.nome) },
                            supportingContent = { Text("codice ${v.codice} · tu sei ${v.io}") },
                            trailingContent = { if (v.token == attivo?.token) Text("✓") },
                            modifier = Modifier.clickable { onScegli(v) },
                        )
                    }
                    TextButton(
                        onClick = onNuovoViaggio,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    ) { Text("➕ Apri o entra in un altro viaggio") }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                ListItem(
                    headlineContent = { Text("📱 Versione app") },
                    supportingContent = { Text("v${versione.first} (build ${versione.second}) · qui si scarica la nuova") },
                    modifier = Modifier.clickable { aggiornamento = true },
                )
            }

            attivo?.let { v ->
                OutlinedButton(
                    onClick = { confermaUscita = true },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text("Esci da «${v.nome}» su questo telefono") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (aggiornamento) DialogoAggiornamento { aggiornamento = false }

    if (confermaUscita && attivo != null) {
        AlertDialog(
            onDismissRequest = { confermaUscita = false },
            title = { Text("Uscire dal viaggio?") },
            // ⚠️ La finestra dice **cosa non succede**: davanti a un viaggio
            // intero di spese, «esci» senza spiegazioni si legge come «cancella».
            text = {
                Text(
                    "Il viaggio resta dov'è: non si cancella niente, e l'altro continua a " +
                        "vedere tutto. Sparisce da questo telefono, e col codice ${attivo.codice} " +
                        "ci rientri quando vuoi."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confermaUscita = false
                    onEsci(attivo.viaggioId)
                }) { Text("Esci") }
            },
            dismissButton = { TextButton(onClick = { confermaUscita = false }) { Text("Resto") } },
        )
    }
}
