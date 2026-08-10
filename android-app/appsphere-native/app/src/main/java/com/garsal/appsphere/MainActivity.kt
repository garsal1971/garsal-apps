package com.garsal.appsphere

import android.content.Intent
import android.os.Bundle
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.garsal.appsphere.core.Palette
import com.garsal.appsphere.core.Supabase
import com.garsal.appsphere.eventslog.EventsLogScreen
import com.garsal.appsphere.home.HomeScreen
import com.garsal.appsphere.home.Route
import com.garsal.appsphere.obiettivi.ObiettiviScreen
import com.garsal.appsphere.spuntiamola.SpuntiamolaScreen
import io.github.jan.supabase.auth.handleDeeplinks
import kotlinx.coroutines.launch

/**
 * Unica Activity dell'app: tutto il resto è Compose.
 *
 * Estende FragmentActivity e non ComponentActivity perché BiometricPrompt
 * pretende una FragmentActivity.
 */
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Il deep link di ritorno dall'OAuth può arrivare sia come intent
        // iniziale (app chiusa) sia in onNewIntent (app già aperta): vanno
        // gestiti tutti e due, o il login riesce solo in uno dei due casi.
        Supabase.client().handleDeeplinks(intent)

        setContent {
            AppSphereTheme {
                AppRoot(activity = this)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        Supabase.client().handleDeeplinks(intent)
    }
}

@Composable
private fun AppRoot(activity: FragmentActivity) {
    val stato by AuthRepo.state.collectAsStateWithLifecycle(
        initialValue = AuthRepo.State.CARICAMENTO
    )

    // Lo sblocco vale per la vita del processo: chiedere l'impronta a ogni
    // ritorno in foreground sarebbe più severo dell'APK attuale, che la chiede
    // solo all'avvio.
    var sbloccato by remember { mutableStateOf(false) }
    var biometriaRifiutata by remember { mutableStateOf(false) }

    fun chiediSblocco() {
        biometriaRifiutata = false
        BiometricGate.chiedi(
            activity = activity,
            onSbloccato = { sbloccato = true },
            onRinuncia = { biometriaRifiutata = true },
        )
    }

    // La biometria ha senso solo a sessione presente: prima del primo login
    // non c'è ancora niente da proteggere.
    LaunchedEffect(stato) {
        if (stato == AuthRepo.State.DENTRO && !sbloccato) chiediSblocco()
    }

    when {
        stato == AuthRepo.State.CARICAMENTO -> SchermataAttesa()
        stato == AuthRepo.State.FUORI -> SchermataLogin()
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
    val scope = rememberCoroutineScope()
    var errore by remember { mutableStateOf<String?>(null) }
    var inCorso by remember { mutableStateOf(false) }

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
                text = "Versione nativa",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.muted,
                modifier = Modifier.padding(top = 4.dp, bottom = 40.dp),
            )
            Button(
                enabled = !inCorso,
                onClick = {
                    inCorso = true
                    errore = null
                    scope.launch {
                        try {
                            AuthRepo.loginConGoogle()
                        } catch (e: Exception) {
                            errore = e.message ?: "Login non riuscito"
                        } finally {
                            inCorso = false
                        }
                    }
                },
            ) {
                Text(if (inCorso) "Apertura…" else "Entra con Google")
            }
            errore?.let {
                Text(
                    text = it,
                    color = Palette.danger,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 16.dp),
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
