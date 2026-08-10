package com.garsal.appsphere.core

import android.util.Log
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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
