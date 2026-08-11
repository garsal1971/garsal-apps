package com.garsal.appsphere.core

import com.garsal.appsphere.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.ExternalAuthAction
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.engine.okhttp.OkHttp

/**
 * L'unico punto da cui passa il database.
 *
 * Le pagine HTML parlano con PostgREST a mano
 * (`fetch("${SUPABASE_URL}/rest/v1/...", { Authorization: "Bearer " + sb_token })`);
 * qui la stessa cosa la fa supabase-kt, che in più tiene la sessione fra un
 * avvio e l'altro e rinnova il JWT prima che scada — le tre funzioni che
 * `index.html` si scrive da sé (`refreshSession`, `startTokenRefresh`, `parseHash`).
 *
 * URL e anon key arrivano da BuildConfig: `debug` punta al progetto Supabase di
 * sviluppo, `release` a quello di produzione. È l'equivalente del blocco
 * `_IS_DEV` degli HTML, che qui non avrebbe un hostname da guardare.
 * La anon key è pubblica per costruzione: a proteggere i dati è la RLS.
 */
object Supabase {

    /** Schema del deep link di ritorno dall'OAuth. Vedi AndroidManifest. */
    const val DEEPLINK_SCHEME = "garsalnative"
    const val DEEPLINK_HOST = "oauth"

    @Volatile
    private var instance: SupabaseClient? = null

    fun client(): SupabaseClient = instance ?: synchronized(this) {
        instance ?: build().also { instance = it }
    }

    private fun build(): SupabaseClient = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY
    ) {
        install(Auth) {
            scheme = DEEPLINK_SCHEME
            host = DEEPLINK_HOST
            // Flusso implicito, che è anche il default della libreria.
            //
            // Provato PKCE nella 1.0.2 e tornati indietro su **prova diretta**:
            // il consenso restava appeso nel browser, e quando il deep link
            // arrivava portava un `#access_token=…` — cioè un fragment, non il
            // `?code=…` che PKCE si aspetta. Questo progetto Supabase risponde
            // in implicito, e configurare PKCE significava solo scartare in
            // silenzio quello che il server manda davvero.
            //
            // Il difetto vero della 1.0.2 non era il flusso ma il fatto che
            // scartasse senza dirlo: ora il deep link è gestito a mano in
            // MainActivity e **tutti e due i formati** vengono accolti, quindi
            // questa riga non è più un punto di rottura.
            flowType = FlowType.IMPLICIT
            // Custom Tabs e non il browser esterno: è quello che fa già
            // MainActivity dell'APK WebView, ed è l'unico modo per cui Google
            // non rifiuti il login come "user agent non sicuro".
            defaultExternalAuthAction = ExternalAuthAction.CustomTabs()
            // ⚠️ Spente di proposito. Sono l'osservatore di ProcessLifecycle che
            // supabase-kt installa da sé, e fa due cose che qui fanno danno:
            //
            //  • in `onStop` chiama `resetLoadingState()`, cioè riporta
            //    `sessionStatus` a `Initializing`. Andare in background non è
            //    un'eccezione qui: aprire la Custom Tab del login *è* andare in
            //    background, e lo è anche il PIN del telefono chiesto dalla
            //    biometria. Ne usciva un'app che dopo il login mostrava la
            //    rotella e poi ricadeva sul pulsante "Entra con Google".
            //  • in `onStart` rilegge la sessione dall'archivio — e al rientro
            //    dal browser lo fa **in parallelo** all'import del token appena
            //    ricevuto: due scritture su `sessionStatus`, e quale delle due
            //    arriva ultima dipende da chi finisce prima.
            //
            // Il refresh del JWT non ha bisogno di quell'osservatore: il job di
            // rinnovo parte da `importSession` e dorme fino all'80 % della
            // scadenza, esattamente come `startTokenRefresh()` nelle pagine web,
            // che nessuno sospende quando la scheda passa in secondo piano.
            enableLifecycleCallbacks = false
        }
        install(Postgrest)
        // L'engine va scelto: supabase-kt non ne porta uno di serie.
        httpEngine = OkHttp.create()
    }
}
