package com.garsal.speseingiro.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * I due componenti che servono coi caratteri di sistema grandi.
 *
 * ⚠️ Sono ricalcati da `core/PulsantiTendine.kt` di appsphere-native e non
 * importati: sono due progetti Gradle separati, che non condividono sorgenti —
 * è la stessa duplicazione dichiarata di `ForziereBiometria` / `ForziereKeystore`.
 * Se la regola cambia là, va cambiata anche qui.
 */

/** Una riga di pulsanti non va MAI a capo: scorre col dito. Andando a capo,
 *  con l'ingrandimento alto tre pulsanti diventano tre righe e in uno schermo
 *  ci sta una voce e mezza. Niente è nascosto — quel che è tagliato dal bordo
 *  si trascina — **a patto che quel che conta di più stia a sinistra**. */
@Composable
fun RigaScorrevole(
    modifier: Modifier = Modifier,
    spazio: Dp = 8.dp,
    contenuto: @Composable () -> Unit,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(spazio),
    ) { contenuto() }
}

/**
 * La larghezza comune dei pulsanti di una riga: si **misura** sullo stile e
 * sull'ingrandimento correnti, mai una costante in `dp` — che o taglia
 * l'etichetta più lunga, o lascia le altre in un pulsante largo il doppio.
 *
 * ⚠️ Vanno passate **tutte** le etichette che possono comparire in quella riga,
 * comprese quelle che in questo momento non si vedono: altrimenti un pulsante
 * condizionale fa traballare la larghezza degli altri quando compare.
 */
@Composable
fun larghezzaPulsanti(vararg etichette: String, stile: TextStyle = MaterialTheme.typography.labelLarge): Dp {
    val misuratore = rememberTextMeasurer()
    val densita = LocalDensity.current
    val px = etichette.maxOfOrNull { misuratore.measure(AnnotatedString(it), stile).size.width } ?: 0
    return with(densita) { px.toDp() } + 40.dp
}

/** Una scelta fra poche opzioni fisse è **sempre** una tendina, mai una fila di
 *  pillole che va a capo o si accorcia. */
@Composable
fun <T> Tendina(
    etichetta: String,
    valore: T,
    opzioni: List<T>,
    testo: (T) -> String,
    modifier: Modifier = Modifier,
    abilitata: Boolean = true,
    onScelta: (T) -> Unit,
) {
    var aperta by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = testo(valore),
            onValueChange = {},
            readOnly = true,
            enabled = false,
            label = { Text(etichetta) },
            modifier = Modifier
                .fillMaxWidth()
                .clickableSeAbilitata(abilitata) { aperta = true },
            colors = coloriTendina(),
        )
        DropdownMenu(expanded = aperta, onDismissRequest = { aperta = false }) {
            opzioni.forEach { o ->
                DropdownMenuItem(
                    text = { Text(testo(o)) },
                    onClick = { onScelta(o); aperta = false },
                )
            }
        }
    }
}

@Composable
private fun coloriTendina() = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
    disabledTextColor = MaterialTheme.colorScheme.onSurface,
    disabledBorderColor = MaterialTheme.colorScheme.outline,
    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
)

private fun Modifier.clickableSeAbilitata(abilitata: Boolean, onClick: () -> Unit): Modifier =
    if (abilitata) this.clickable(onClick = onClick) else this

/** Un'etichetta di stato: colore + parola, mai il colore da solo. */
@Composable
fun Etichetta(testo: String, fondo: Color, davanti: Color, modifier: Modifier = Modifier) {
    Surface(color = fondo, shape = RoundedCornerShape(6.dp), modifier = modifier) {
        Text(
            testo,
            color = davanti,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/** Il pulsante pieno di una riga: altezza **minima** e non fissa, o al primo
 *  ingrandimento dei caratteri l'etichetta esce dal contenitore. */
@Composable
fun PulsantePieno(testo: String, larghezza: Dp?, abilitato: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = abilitato,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        modifier = Modifier.then(if (larghezza != null) Modifier.width(larghezza) else Modifier).heightIn(min = 44.dp),
    ) { Text(testo, textAlign = TextAlign.Center, maxLines = 2) }
}

@Composable
fun PulsanteVuoto(
    testo: String,
    larghezza: Dp?,
    colore: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = colore),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        modifier = Modifier.then(if (larghezza != null) Modifier.width(larghezza) else Modifier).heightIn(min = 44.dp),
    ) { Text(testo, textAlign = TextAlign.Center, maxLines = 2) }
}
