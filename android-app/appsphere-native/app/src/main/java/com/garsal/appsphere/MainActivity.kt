package com.garsal.appsphere

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.garsal.appsphere.core.AppSphereTheme
import com.garsal.appsphere.core.AuthRepo
import com.garsal.appsphere.core.BiometricGate
import com.garsal.appsphere.core.CerchiOlimpici
import com.garsal.appsphere.core.Palette
import com.garsal.appsphere.core.Supabase
import com.garsal.appsphere.eventslog.EventsLogScreen
import com.garsal.appsphere.home.HomeScreen
import com.garsal.appsphere.home.Route
import com.garsal.appsphere.obiettivi.ObiettiviScreen
import com.garsal.appsphere.spuntiamola.SpuntiamolaScreen
import com.garsal.appsphere.tasks.TasksScreen
import kotlinx.coroutines.delay

/**
 * Unica Activity dell'app: tutto il resto è Compose.
 *
 * Estende FragmentActivity e non ComponentActivity perché BiometricPrompt
 * pretende una FragmentActivity.
 */
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // L'equivalente del console.log stilizzato che ogni pagina web stampa
        // all'avvio: serve a sapere quale versione sta girando davvero.
        Log.i("AppSphere", "AppSphere nativa v${BuildConfig.VERSION_NAME} · ${BuildConfig.SUPABASE_URL}")

        // Il deep link di ritorno dall'OAuth può arrivare sia come intent
        // iniziale (app chiusa) sia in onNewIntent (app già aperta): vanno
        // gestiti tutti e due, o il login riesce solo in uno dei due casi.
        gestisciDeepLink(intent)

        setContent {
            AppSphereTheme {
                AppRoot(activity = this)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        gestisciDeepLink(intent)
    }

    /**
     * Accoglie il rientro dal login, in **qualunque** forma arrivi.
     *
     * Non si delega a `handleDeeplinks`: quella guarda solo il formato
     * corrispondente al `flowType` configurato e, se trova l'altro, esce in
     * silenzio — ed è precisamente così che la 1.0.2 buttava via i token che
     * il server le stava mandando. Qui si guarda cosa c'è davvero:
     * un `?code=` da scambiare, un `#access_token=` da importare, o un errore
     * da mostrare. Nessuno dei tre può più passare inosservato.
     *
     * ⚠️ **Un deep link si consuma una volta sola.** `getIntent()` continua a
     * restituire quello di partenza per tutta la vita dell'Activity: a ogni
     * ricreazione — cambio di tema, di lingua, ritorno dopo che il sistema ha
     * ucciso il processo — `onCreate` si ritroverebbe fra le mani lo stesso
     * `access_token`, che nel frattempo è scaduto o già speso. Reimportarlo non
     * è innocuo: rimpiazza una sessione buona con una che il server rifiuterà.
     * Quindi appena è stato letto lo si toglie dall'intent.
     */
    private fun gestisciDeepLink(intent: Intent?) {
        if (intent == null) return
        val dati = intent.data
        if (dati == null || dati.scheme != Supabase.DEEPLINK_SCHEME) return

        // Consumato: né questa chiamata né una futura ricreazione lo rivedranno.
        intent.data = null
        setIntent(intent)

        val errore = dati.getQueryParameter("error_description")
            ?: dati.getQueryParameter("error")
        val codice = dati.getQueryParameter("code")
        // `Uri.fragment` normalmente basta, ma su alcune forme di Uri torna
        // vuoto: stesso ripiego di `oauthFragment()` nell'APK WebView, dove è
        // lì per lo stesso motivo. Costa una riga e toglie un modo di perdere
        // i token senza accorgersene.
        val fragment = dati.fragment?.takeIf { it.isNotBlank() }
            ?: dati.toString().substringAfter('#', "")

        when {
            errore != null ->
                AuthRepo.annotaRientro("Supabase ha risposto con un errore: $errore")
            codice != null ->
                AuthRepo.completaConCodice(codice)
            !fragment.isNullOrBlank() ->
                AuthRepo.completaConFragment(fragment)
            else ->
                AuthRepo.annotaRientro("rientro senza token né errore (host=${dati.host})")
        }
    }
}

@Composable
private fun AppRoot(activity: FragmentActivity) {
    val stato by AuthRepo.state.collectAsStateWithLifecycle()

    // Lo sblocco vale per la vita del processo: chiedere l'impronta a ogni
    // ritorno in foreground sarebbe più severo dell'APK attuale, che la chiede
    // solo all'avvio.
    var sbloccato by remember { mutableStateOf(false) }
    var biometriaRifiutata by remember { mutableStateOf(false) }
    // Il prompt si chiede da solo una volta sola. Dopo una rinuncia riparte dal
    // pulsante: rilanciarlo a ogni sussulto dello stato annullerebbe quello già
    // a schermo, e la rinuncia finta che ne esce riaprirebbe il giro da capo.
    var sbloccoChiesto by remember { mutableStateOf(false) }

    // ── La rotella non deve poter girare per sempre ──────────────────────
    // Se il rientro non porta una sessione — deep link mancato, rete assente,
    // token non importato — lo stato resta su CARICAMENTO, e mapparlo su una
    // schermata di attesa senza uscita dà un'app che gira a vuoto senza dire
    // perché. Prima si prova a rileggere la sessione dall'archivio (è il caso
    // normale di chi ha già fatto il login una volta), e solo se nemmeno quella
    // risponde si mostra il login, che almeno si può ripremere.
    var attesaScaduta by remember { mutableStateOf(false) }
    LaunchedEffect(stato) {
        attesaScaduta = false
        if (stato == AuthRepo.State.CARICAMENTO) {
            delay(3000)
            AuthRepo.ricaricaDaArchivio()
            delay(3000)
            attesaScaduta = true
        }
    }

    fun chiediSblocco() {
        biometriaRifiutata = false
        sbloccoChiesto = true
        BiometricGate.chiedi(
            activity = activity,
            onSbloccato = { sbloccato = true },
            onRinuncia = { biometriaRifiutata = true },
        )
    }

    // La biometria ha senso solo a sessione presente: prima del primo login
    // non c'è ancora niente da proteggere.
    LaunchedEffect(stato) {
        if (stato == AuthRepo.State.DENTRO && !sbloccato && !sbloccoChiesto) {
            // Mezzo secondo di respiro. Subito dopo il login questo effetto
            // parte mentre l'Activity sta ancora tornando in primo piano dalla
            // Custom Tab, e un prompt biometrico chiesto in quel momento
            // risponde ERROR_CANCELED all'istante — cioè una rinuncia che
            // nessuno ha fatto, e una schermata "App bloccata" da sbloccare a
            // mano al primo accesso.
            delay(500)
            chiediSblocco()
        }
    }

    when {
        stato == AuthRepo.State.CARICAMENTO && !attesaScaduta -> SchermataAttesa()
        stato == AuthRepo.State.CARICAMENTO || stato == AuthRepo.State.FUORI -> SchermataLogin()
        !sbloccato -> SchermataBloccata(
            rifiutata = biometriaRifiutata,
            onRiprova = { chiediSblocco() },
        )
        else -> Navigazione()
    }
}

@Composable
private fun Navigazione() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Route.HOME) {
        composable(Route.HOME) {
            HomeScreen(onApriApp = { rotta -> nav.navigate(rotta) })
        }
        composable(Route.SPUNTIAMOLA) {
            SpuntiamolaScreen(onIndietro = { nav.popBackStack() })
        }
        composable(Route.OBIETTIVI) {
            ObiettiviScreen(onIndietro = { nav.popBackStack() })
        }
        composable(Route.TASKS) {
            TasksScreen(onIndietro = { nav.popBackStack() })
        }
        composable(Route.EVENTS_LOG) {
            EventsLogScreen(onIndietro = { nav.popBackStack() })
        }
    }
}

@Composable
private fun SchermataAttesa() {
    Scaffold { p ->
        Box(Modifier.fillMaxSize().padding(p), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Palette.topBar)
        }
    }
}

@Composable
private fun SchermataLogin() {
    val context = LocalContext.current
    val rientro by AuthRepo.ultimoRientro.collectAsStateWithLifecycle()

    Scaffold { p ->
        Column(
            modifier = Modifier.fillMaxSize().padding(p).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CerchiOlimpici(Modifier.size(120.dp))
            Text(
                text = "AppSphere",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = 24.dp),
            )
            Text(
                text = "Versione nativa · v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.muted,
                modifier = Modifier.padding(top = 4.dp, bottom = 40.dp),
            )
            // Niente stato "in corso": il login se ne va nel browser di
            // sistema e questa Activity passa in secondo piano subito dopo.
            // Un pulsante disabilitato in attesa di un ritorno che avviene
            // altrove resterebbe disabilitato anche quando si torna indietro
            // senza aver fatto il login.
            Button(onClick = { AuthRepo.loginConGoogle(context) }) {
                Text("Entra con Google")
            }

            Text(
                text = "Il login si apre nel browser. Alla fine tocca " +
                    "«Apri AppSphere nativa» per tornare qui.",
                color = Palette.muted,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 16.dp),
            )

            // Se si è già tornati dal browser una volta senza entrare, dirlo:
            // è l'unico modo per capire dove si è rotto senza collegare il
            // telefono al computer e leggere i log.
            rientro?.let {
                Text(
                    text = "Ultimo rientro dal login: $it",
                    color = Palette.muted,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
        }
    }
}

@Composable
private fun SchermataBloccata(rifiutata: Boolean, onRiprova: () -> Unit) {
    Scaffold { p ->
        Column(
            modifier = Modifier.fillMaxSize().padding(p).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CerchiOlimpici(Modifier.size(96.dp))
            Text(
                text = if (rifiutata) "App bloccata" else "Sblocca AppSphere",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 24.dp, bottom = 24.dp),
            )
            Button(onClick = onRiprova) { Text("Sblocca") }
        }
    }
}
