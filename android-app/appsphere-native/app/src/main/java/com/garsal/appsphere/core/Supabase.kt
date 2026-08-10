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
            // PKCE e non il flusso implicito (che è il default della libreria).
            // Con l'implicito i token tornano nel **fragment** del deep link
            // (`garsalnative://oauth#access_token=…`): se per qualsiasi ragione
            // il fragment non arriva, `handleDeeplinks` non trova niente ed
            // esce in silenzio — l'app rientra dal browser e non succede
            // niente, senza un errore da nessuna parte. Con PKCE il codice
            // viaggia come parametro di query (`?code=…`), che Android
            // consegna in modo molto più affidabile, e lo scambio col server
            // fallisce rumorosamente se qualcosa non va.
            flowType = FlowType.PKCE
            // Custom Tabs e non il browser esterno: è quello che fa già
            // MainActivity dell'APK WebView, ed è l'unico modo per cui Google
            // non rifiuti il login come "user agent non sicuro".
            defaultExternalAuthAction = ExternalAuthAction.CustomTabs()
        }
        install(Postgrest)
        // L'engine va scelto: supabase-kt non ne porta uno di serie.
        httpEngine = OkHttp.create()
    }
}
