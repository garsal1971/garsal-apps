package com.garsalapps

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.Calendar
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    // ⬇️ Cambia qui con la tua URL Netlify
    private val APP_URL = "https://garsal.netlify.app/"

    // Custom scheme usato come redirect_to dopo Google OAuth
    private val OAUTH_CALLBACK_SCHEME = "garsalapps"
    private val OAUTH_CALLBACK_HOST   = "oauth"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        setupWebView()
        scheduleNotifications()
        showBiometricPrompt()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack()
                else finish()
            }
        })
    }

    /**
     * Gestisce il ritorno del deep link OAuth (garsalapps://oauth#access_token=...)
     * Lanciato da Android quando Chrome Custom Tabs completa il login Google.
     */
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        val data = intent?.data ?: return
        if (data.scheme == OAUTH_CALLBACK_SCHEME && data.host == OAUTH_CALLBACK_HOST) {
            // Ricostruisce l'URL completo per far parsare il token al JS di Supabase
            val fragment = data.toString().substringAfter('#', "")
            val callbackUrl = if (fragment.isNotEmpty()) "${APP_URL}#$fragment" else APP_URL
            webView.loadUrl(callbackUrl)
        }
    }

    private fun setupWebView() {
        webView.visibility = View.GONE
        webView.apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true          // localStorage persiste tra sessioni
                databaseEnabled = true
                cacheMode = WebSettings.LOAD_DEFAULT
                setSupportZoom(false)
                useWideViewPort = true
                loadWithOverviewMode = true
            }
            webViewClient = object : WebViewClient() {

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                }

                /**
                 * Intercetta la navigazione verso l'endpoint OAuth di Supabase
                 * e la apre in Chrome Custom Tabs, sostituendo il redirect_to
                 * con il custom scheme dell'app (garsalapps://oauth).
                 *
                 * Questo evita l'errore "Accesso bloccato" di Google che blocca
                 * i flussi OAuth all'interno di WebView.
                 */
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val url = request?.url?.toString() ?: return false

                    if (url.contains("supabase.co/auth/v1/authorize") &&
                        url.contains("provider=google")
                    ) {
                        val original = request.url
                        // Sostituisce redirect_to con il custom scheme
                        val modified = original.buildUpon()
                            .clearQuery()
                            .apply {
                                original.queryParameterNames.forEach { param ->
                                    val value = if (param == "redirect_to") {
                                        "$OAUTH_CALLBACK_SCHEME://$OAUTH_CALLBACK_HOST"
                                    } else {
                                        original.getQueryParameter(param)
                                    }
                                    appendQueryParameter(param, value)
                                }
                            }
                            .build()

                        CustomTabsIntent.Builder()
                            .setShowTitle(true)
                            .build()
                            .launchUrl(this@MainActivity, modified)
                        return true
                    }

                    return false
                }
            }
        }
        // Cookie persistenti → Supabase ricorda il login
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
            flush()
        }
    }

    private fun showBiometricPrompt() {
        val biometricManager = BiometricManager.from(this)
        val canAuthenticate = biometricManager.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)

        if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS) {
            val executor = ContextCompat.getMainExecutor(this)
            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    showApp()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    // Utente ha annullato o errore → chiudi app
                    finish()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    // Tentativo fallito, il prompt rimane aperto automaticamente
                }
            }

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("AppSphere")
                .setSubtitle("Sblocca con impronta digitale o PIN")
                .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
                .build()

            BiometricPrompt(this, executor, callback).authenticate(promptInfo)
        } else {
            // Biometria non disponibile → apri direttamente
            showApp()
        }
    }

    private fun showApp() {
        webView.visibility = View.VISIBLE
        webView.loadUrl(APP_URL)
    }

    private fun scheduleNotifications() {
        val wm = WorkManager.getInstance(this)

        // Reminder abitudini ogni giorno alle 20:00
        val habitWork = PeriodicWorkRequestBuilder<NotificationWorker>(24, TimeUnit.HOURS)
            .setInputData(workDataOf(
                "type" to "habit",
                "title" to "Habit Stack",
                "message" to "Hai completato le tue abitudini oggi? 💪"
            ))
            .setInitialDelay(delayUntil(hour = 20, minute = 0), TimeUnit.MILLISECONDS)
            .build()

        // Reminder task ogni giorno alle 09:00
        val taskWork = PeriodicWorkRequestBuilder<NotificationWorker>(24, TimeUnit.HOURS)
            .setInputData(workDataOf(
                "type" to "task",
                "title" to "Tasks",
                "message" to "Controlla i tuoi task di oggi 📋"
            ))
            .setInitialDelay(delayUntil(hour = 9, minute = 0), TimeUnit.MILLISECONDS)
            .build()

        // Reminder peso ogni settimana
        val weightWork = PeriodicWorkRequestBuilder<NotificationWorker>(7, TimeUnit.DAYS)
            .setInputData(workDataOf(
                "type" to "weight",
                "title" to "Weight Quest",
                "message" to "Ricordati di registrare il tuo peso! ⚖️"
            ))
            .setInitialDelay(delayUntil(hour = 8, minute = 0), TimeUnit.MILLISECONDS)
            .build()

        wm.enqueueUniquePeriodicWork("habit_reminder", ExistingPeriodicWorkPolicy.KEEP, habitWork)
        wm.enqueueUniquePeriodicWork("task_reminder", ExistingPeriodicWorkPolicy.KEEP, taskWork)
        wm.enqueueUniquePeriodicWork("weight_reminder", ExistingPeriodicWorkPolicy.KEEP, weightWork)
    }

    /** Calcola i millisecondi mancanti alla prossima occorrenza dell'orario specificato */
    private fun delayUntil(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (target.before(now)) target.add(Calendar.DAY_OF_MONTH, 1)
        return target.timeInMillis - now.timeInMillis
    }

}
