package com.garsal.appsphere.core

import android.util.Log
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.parseFragmentAndImportSession
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Login Google e stato della sessione.
 *
 * Il giro è lo stesso dell'APK WebView (`MainActivity.kt`): Chrome Custom Tabs
 * verso Supabase, che rimanda a un deep link dell'app. L'unica differenza è lo
 * schema — `garsalnative://oauth` invece di `garsalapps://oauth`, perché con lo
 * stesso schema Android chiederebbe ogni volta quale delle due app aprire.
 */
object AuthRepo {

    private const val TAG = "AppSphereAuth"

    enum class State { CARICAMENTO, DENTRO, FUORI }

    /**
     * Cosa è arrivato dall'ultimo rientro dal browser.
     *
     * `handleDeeplinks` esce in silenzio quando l'URL non è quello che si
     * aspetta: senza questa traccia un login che non va a buon fine è
     * indistinguibile da un login mai tentato — l'app torna in primo piano e
     * non succede niente. Qui si annota cosa si è visto, e la schermata di
     * login lo mostra invece di lasciare l'utente a guardare una rotella.
     */
    val ultimoRientro = MutableStateFlow<String?>(null)

    fun annotaRientro(descrizione: String?) {
        Log.i(TAG, "rientro dal browser: ${descrizione ?: "entrato"}")
        ultimoRientro.value = descrizione
    }

    /** Scope proprio: il rientro può arrivare quando nessuna schermata è viva. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * Rientro col flusso implicito: i token sono nel fragment del deep link.
     * È la forma in cui risponde questo progetto Supabase.
     */
    @OptIn(SupabaseInternal::class)
    fun completaConFragment(fragment: String) {
        if (fragment.contains("error")) {
            annotaRientro("il fragment contiene un errore: $fragment")
            return
        }
        try {
            Supabase.client().auth.parseFragmentAndImportSession(fragment) {
                annotaRientro(null)
            }
        } catch (e: Exception) {
            Log.w(TAG, "fragment non utilizzabile", e)
            annotaRientro("token ricevuti ma non utilizzabili: ${e.message}")
        }
    }

    /**
     * Rientro col flusso PKCE: c'è un codice da scambiare col server.
     * Oggi non è la strada che il progetto usa, ma accoglierla costa poche
     * righe e toglie di mezzo la dipendenza da come il server decide di
     * rispondere — che è esattamente ciò che ha rotto la 1.0.2.
     */
    fun completaConCodice(codice: String) {
        scope.launch {
            try {
                Supabase.client().auth.exchangeCodeForSession(codice)
                annotaRientro(null)
            } catch (e: Exception) {
                Log.w(TAG, "scambio del codice fallito", e)
                annotaRientro("scambio del codice fallito: ${e.message}")
            }
        }
    }

    val state: Flow<State> = Supabase.client().auth.sessionStatus.map { status ->
        when (status) {
            is SessionStatus.Authenticated -> State.DENTRO
            is SessionStatus.Initializing -> State.CARICAMENTO
            is SessionStatus.NotAuthenticated -> State.FUORI
            // Il refresh non è riuscito (rete assente o refresh token revocato).
            // La pagina web in questo caso pulisce i token e torna al login
            // senza rumore: stessa scelta qui.
            is SessionStatus.RefreshFailure -> {
                Log.w(TAG, "refresh del JWT fallito: ${status.cause}")
                State.FUORI
            }
        }
    }

    /** Email dell'utente collegato, usata per le etichette e per i log. */
    fun email(): String? = Supabase.client().auth.currentUserOrNull()?.email

    /** Id dell'utente: è lo stesso `auth.uid()` con cui filtrano tutte le RLS. */
    fun userId(): String? = Supabase.client().auth.currentUserOrNull()?.id

    suspend fun loginConGoogle() {
        Supabase.client().auth.signInWith(Google)
    }

    suspend fun logout() {
        Supabase.client().auth.signOut()
    }
}
