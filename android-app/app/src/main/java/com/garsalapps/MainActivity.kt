package com.garsalapps

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.JsResult
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.WeightRecord
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.Calendar
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    private val APP_URL             = "https://garsal.netlify.app/"
    private val OAUTH_CALLBACK_SCHEME = "garsalapps"
    private val OAUTH_CALLBACK_HOST   = "oauth"
    private val PREFS_OAUTH           = "oauth_pending"

    // ── Health Connect permissions ────────────────────────────────────────────
    // Usa il contratto ufficiale PermissionController: è l'unico modo per cui
    // HC Service riceva la notifica del grant e sblocchi le chiamate readRecords().
    // Con startActivity manuale il grant appare concesso ma HC rimane in attesa
    // della conferma ufficiale → readRecords() si blocca indefinitamente.
    private val HC_PERMISSIONS = setOf(
        HealthPermission.getReadPermission(WeightRecord::class)
    )

    private lateinit var requestHcPermissions: ActivityResultLauncher<Set<String>>

    // File chooser per input[type=file] nel WebView
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private var cameraImageUri: Uri? = null

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        fileChooserCallback?.onReceiveValue(uris.toTypedArray())
        fileChooserCallback = null
    }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = cameraImageUri
        if (success && uri != null) {
            fileChooserCallback?.onReceiveValue(arrayOf(uri))
        } else {
            fileChooserCallback?.onReceiveValue(null)
        }
        fileChooserCallback = null
        cameraImageUri = null
    }

    // Flag nativo: true se l'utente ha aperto Renpho e deve tornare
    @Volatile
    private var renphoLaunched = false

    // OCR da condivisione screenshot
    private var pendingOcrText: String? = null
    private var pendingOcrImageBase64: String = ""
    private var pendingOcrImageMime: String = "image/jpeg"
    private var openMemoAfterAuth: Boolean = false  // apri memo.html invece del launcher dopo biometrica
    private val MEMO_URL = "https://garsal.netlify.app/memo.html"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Disabilita edge-to-edge: impedisce al WebView di espandersi sotto la status bar
        WindowCompat.setDecorFitsSystemWindows(window, true)

        // Registra il launcher PRIMA di setContentView (requisito ActivityResult API)
        requestHcPermissions = registerForActivityResult(
            PermissionController.createRequestPermissionResultContract()
        ) { granted ->
            Log.d("MainActivity", "HC permissions result: granted=$granted")
            // Il callback conferma ad HC Service che i permessi sono stati concessi.
            // Nessuna azione aggiuntiva necessaria: la prossima readRecords() funzionerà.
        }

        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        setupWebView()
        scheduleNotifications()

        // Caso: app uccisa da Android mentre Chrome Custom Tabs era aperto.
        // Il deep link torna via onCreate invece di onNewIntent.
        intent?.data?.oauthFragment()?.let { saveTokensToPending(it) }

        // Condivisione screenshot → OCR → memo
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
            openMemoAfterAuth = true
            handleSharedImage(intent)
        }

        showBiometricPrompt()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack()
                else finish()
            }
        })
    }

    /**
     * Caso normale: app in background, Chrome Custom Tabs completa l'OAuth.
     * Android chiama onNewIntent con garsalapps://oauth#access_token=...
     */
    override fun onResume() {
        super.onResume()
        if (renphoLaunched) {
            renphoLaunched = false
            Log.d("MainActivity", "onResume: ritorno da Renpho — chiamo onAndroidResume()")
            webView.evaluateJavascript("try{onAndroidResume();}catch(e){console.warn('onAndroidResume:'+e);}", null)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        // Condivisione screenshot con app già aperta
        if (intent.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
            openMemoAfterAuth = true
            handleSharedImage(intent)
            return
        }

        val fragment = intent.data?.oauthFragment() ?: return
        saveTokensToPending(fragment)
        webView.loadUrl(APP_URL)
    }

    /**
     * Riceve un'immagine condivisa (es. screenshot WhatsApp), esegue OCR con ML Kit,
     * salva l'immagine come base64 e apre Memorandum con testo e immagine pre-compilati.
     */
    private fun handleSharedImage(intent: Intent) {
        @Suppress("DEPRECATION")
        val imageUri: Uri = intent.getParcelableExtra(Intent.EXTRA_STREAM) ?: run {
            Log.w("MainActivity", "handleSharedImage: nessun URI nell'intent")
            return
        }

        Log.d("MainActivity", "Condivisione immagine ricevuta: $imageUri")

        // Legge i byte dell'immagine e li salva come base64 per il JS
        try {
            val mime = contentResolver.getType(imageUri) ?: "image/jpeg"
            val bytes = contentResolver.openInputStream(imageUri)?.use { it.readBytes() }
            if (bytes != null) {
                pendingOcrImageBase64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                pendingOcrImageMime = mime
                Log.d("MainActivity", "Immagine letta: ${bytes.size} byte, mime=$mime")
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Lettura immagine fallita: $e")
        }

        val image = try {
            InputImage.fromFilePath(this, imageUri)
        } catch (e: Exception) {
            Log.e("MainActivity", "InputImage.fromFilePath fallito: $e")
            pendingOcrText = ""
            runOnUiThread { webView.loadUrl(MEMO_URL) }
            return
        }

        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(image)
            .addOnSuccessListener { result ->
                val text = result.text.trim()
                Log.d("MainActivity", "OCR completato: ${text.length} caratteri")
                pendingOcrText = text
                openMemoAfterAuth = false
                runOnUiThread { webView.loadUrl(MEMO_URL) }
            }
            .addOnFailureListener { e ->
                Log.e("MainActivity", "OCR fallito: $e")
                pendingOcrText = ""
                openMemoAfterAuth = false
                runOnUiThread { webView.loadUrl(MEMO_URL) }
            }
    }

    /**
     * Salva i token OAuth in SharedPreferences (storage nativo Android).
     * Vengono iniettati nel WebView in onPageFinished dopo il prossimo caricamento.
     */
    private fun saveTokensToPending(fragment: String) {
        val params = fragment.split("&").associate { kv ->
            val eq = kv.indexOf('=')
            if (eq > 0) kv.substring(0, eq) to Uri.decode(kv.substring(eq + 1)) else kv to ""
        }
        val at = params["access_token"] ?: return  // se non c'è l'access token ignora
        val rt = params["refresh_token"] ?: ""
        val gt = params["provider_token"] ?: ""

        getSharedPreferences(PREFS_OAUTH, MODE_PRIVATE).edit()
            .putString("access_token", at)
            .putString("refresh_token", rt)
            .putString("provider_token", gt)
            .apply()
    }

    /**
     * Interfaccia JavaScript esposta al WebView come window.AndroidBridge.
     * Permette al JS di rilevare in modo affidabile che sta girando nell'app Android.
     */
    inner class AndroidBridge {
        @android.webkit.JavascriptInterface
        fun isNativeApp(): Boolean = true

        @android.webkit.JavascriptInterface
        fun openApp(packageName: String) {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                Log.d("MainActivity", "openApp: avvio $packageName")
                renphoLaunched = true
                runOnUiThread { startActivity(intent) }
            } else {
                Log.w("MainActivity", "openApp: pacchetto non trovato — $packageName")
            }
        }

        // Restituisce l'immagine condivisa come base64 — chiamato da memo.html dopo openMemoFromOcr()
        @android.webkit.JavascriptInterface
        fun getPendingImageBase64(): String = pendingOcrImageBase64

        @android.webkit.JavascriptInterface
        fun getPendingImageMime(): String = pendingOcrImageMime

        // JS chiama questo dopo aver consumato l'immagine
        @android.webkit.JavascriptInterface
        fun clearPendingImage() {
            pendingOcrImageBase64 = ""
            pendingOcrImageMime = "image/jpeg"
        }

        // OCR su immagine base64 — chiamato dal popup immagini di memo.html
        // Quando finisce chiama ocrCallback(callbackId, testo) nel WebView
        @android.webkit.JavascriptInterface
        fun performOcr(base64: String, mime: String, callbackId: String) {
            try {
                val bytes  = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
                val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?: run {
                        runOnUiThread { webView.evaluateJavascript("ocrCallback('$callbackId','');", null) }
                        return
                    }
                TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    .process(InputImage.fromBitmap(bitmap, 0))
                    .addOnSuccessListener { result ->
                        val text = result.text.trim()
                            .replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n")
                        runOnUiThread { webView.evaluateJavascript("ocrCallback('$callbackId','$text');", null) }
                    }
                    .addOnFailureListener {
                        runOnUiThread { webView.evaluateJavascript("ocrCallback('$callbackId','');", null) }
                    }
            } catch (e: Exception) {
                Log.e("MainActivity", "performOcr: $e")
                runOnUiThread { webView.evaluateJavascript("ocrCallback('$callbackId','');", null) }
            }
        }
    }

    /**
     * Richiede i permessi Health Connect tramite ActivityResultLauncher ufficiale.
     * Questo è l'UNICO modo corretto: il contratto PermissionController notifica
     * HC Service del grant, sbloccando le successive chiamate readRecords().
     */
    fun requestHealthConnectPermissions() {
        Log.d("MainActivity", "Richiesta permessi HC via ActivityResultLauncher")
        requestHcPermissions.launch(HC_PERMISSIONS)
    }

    private fun setupWebView() {
        webView.visibility = View.GONE
        webView.apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                cacheMode = WebSettings.LOAD_DEFAULT
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false  // nasconde pulsanti +/- overlay
                useWideViewPort = true
                loadWithOverviewMode = true
                textZoom = 100  // ignora dimensione font di sistema
            }
            // Espone window.AndroidBridge al JavaScript della pagina
            addJavascriptInterface(AndroidBridge(), "AndroidBridge")
            // Espone window.HealthConnectBridge per la sync dati peso da Health Connect
            addJavascriptInterface(HealthConnectBridge(this@MainActivity, this), "HealthConnectBridge")

            webViewClient = object : WebViewClient() {

                /**
                 * Quando la pagina è completamente caricata, controlla se ci sono
                 * token OAuth in attesa in SharedPreferences e li inietta nel localStorage.
                 */
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    // Rimuove user-scalable=no dal viewport per abilitare pinch-to-zoom
                    view?.evaluateJavascript("""
                        (function(){
                            var m=document.querySelector('meta[name="viewport"]');
                            if(m){m.setAttribute('content',m.content
                                .replace(/user-scalable\s*=\s*(no|0)/gi,'user-scalable=yes')
                                .replace(/maximum-scale\s*=\s*[0-9.]+/gi,'maximum-scale=5.0'));}
                        })();
                    """.trimIndent(), null)
                    if (url?.contains("garsal.netlify.app") != true) return

                    // Inietta testo OCR in memo.html se disponibile
                    val ocrText = pendingOcrText
                    if (ocrText != null && url?.contains("memo.html") == true) {
                        pendingOcrText = null
                        val escaped = ocrText.jsEscape()
                        view?.evaluateJavascript(
                            "setTimeout(function(){ if(typeof openMemoFromOcr==='function') openMemoFromOcr('$escaped'); }, 800);",
                            null
                        )
                    }

                    val prefs = getSharedPreferences(PREFS_OAUTH, MODE_PRIVATE)
                    val at = prefs.getString("access_token", "") ?: ""
                    if (at.isEmpty()) return

                    val rt = prefs.getString("refresh_token", "") ?: ""
                    val gt = prefs.getString("provider_token", "") ?: ""

                    // Cancella i token pending PRIMA di iniettarli (evita loop)
                    prefs.edit().clear().apply()

                    val js = buildString {
                        append("localStorage.setItem('sb_token','${at.jsEscape()}');")
                        if (rt.isNotEmpty())
                            append("localStorage.setItem('refresh_token','${rt.jsEscape()}');")
                        if (gt.isNotEmpty())
                            append("localStorage.setItem('google_token','${gt.jsEscape()}');")
                        // Chiama init() per aggiornare l'UI senza un altro reload
                        append("if(typeof init==='function')init();")
                    }
                    view?.evaluateJavascript(js, null)
                }

                /**
                 * Intercetta la navigazione verso l'endpoint OAuth di Supabase
                 * e la apre in Chrome Custom Tabs.
                 * Il redirect_to=garsalapps://oauth è già impostato dal JS
                 * tramite window.AndroidBridge.isNativeApp().
                 */
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val uri = request?.url ?: return false
                    val url = uri.toString()

                    if (url.contains("supabase.co/auth/v1/authorize") &&
                        url.contains("provider=google")
                    ) {
                        CustomTabsIntent.Builder()
                            .setShowTitle(true)
                            .build()
                            .launchUrl(this@MainActivity, uri)
                        return true
                    }

                    // Il WebView non gestisce intent:// e market:// — li passiamo al sistema
                    if (uri.scheme == "intent" || uri.scheme == "market") {
                        try {
                            val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                            startActivity(intent)
                        } catch (e: Exception) {
                            Log.w("MainActivity", "Intent.parseUri fallito: $e")
                            // Fallback: estrai package name e lancia direttamente
                            val pkg = url.substringAfter("package=").substringBefore(";").trim()
                            if (pkg.isNotEmpty()) {
                                val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
                                if (launchIntent != null) startActivity(launchIntent)
                                else Log.w("MainActivity", "Pacchetto non trovato: $pkg")
                            }
                        }
                        return true
                    }

                    return false
                }
            }

            // Gestisce alert() / confirm() dal JavaScript — senza questo vengono ignorati
            webChromeClient = object : WebChromeClient() {
                override fun onJsAlert(
                    view: WebView?, url: String?, message: String?, result: JsResult?
                ): Boolean {
                    android.app.AlertDialog.Builder(this@MainActivity)
                        .setMessage(message)
                        .setPositiveButton("OK") { _, _ -> result?.confirm() }
                        .setOnCancelListener { result?.cancel() }
                        .show()
                    return true
                }

                // Senza questo override input[type=file] è silenziosamente ignorato nel WebView
                override fun onShowFileChooser(
                    webView: WebView?,
                    callback: ValueCallback<Array<Uri>>?,
                    params: FileChooserParams?
                ): Boolean {
                    fileChooserCallback?.onReceiveValue(null)
                    fileChooserCallback = callback

                    if (params?.isCaptureEnabled == true) {
                        // capture="environment" — apre direttamente la fotocamera
                        try {
                            val imgFile = File.createTempFile("cam_", ".jpg",
                                getExternalFilesDir(Environment.DIRECTORY_PICTURES))
                            val uri = FileProvider.getUriForFile(
                                this@MainActivity,
                                "${packageName}.fileprovider",
                                imgFile
                            )
                            cameraImageUri = uri
                            cameraLauncher.launch(uri)
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Camera launch fallita: $e")
                            fileChooserCallback?.onReceiveValue(null)
                            fileChooserCallback = null
                        }
                    } else {
                        val accept = params?.acceptTypes?.firstOrNull() ?: "image/*"
                        fileChooserLauncher.launch(accept.ifEmpty { "image/*" })
                    }
                    return true
                }
            }
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
            flush()
        }
    }

    /** Ritorna il fragment OAuth dalla Uri se schema e host corrispondono, altrimenti null. */
    private fun Uri.oauthFragment(): String? {
        if (scheme != OAUTH_CALLBACK_SCHEME || host != OAUTH_CALLBACK_HOST) return null
        val frag = fragment ?: toString().substringAfter('#', "")
        return frag.ifEmpty { null }
    }

    /** Escapa backslash e single quote per uso sicuro in stringa JS. */
    private fun String.jsEscape() = replace("\\", "\\\\").replace("'", "\\'")

    private fun showBiometricPrompt() {
        val biometricManager = BiometricManager.from(this)

        // BIOMETRIC_STRONG | DEVICE_CREDENTIAL lancia IllegalArgumentException su API < 30
        // (Android 9 e 10). Usiamo solo BIOMETRIC_STRONG sulle versioni precedenti.
        val authenticators = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            BIOMETRIC_STRONG or DEVICE_CREDENTIAL
        } else {
            BIOMETRIC_STRONG
        }

        if (biometricManager.canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            showApp()
            return
        }

        val executor = ContextCompat.getMainExecutor(this)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                showApp()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                finish()
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
            }
        }

        val promptInfoBuilder = BiometricPrompt.PromptInfo.Builder()
            .setTitle("GarsalApps")
            .setSubtitle("Sblocca con impronta digitale o PIN")
            .setAllowedAuthenticators(authenticators)

        // Su API < 30, senza DEVICE_CREDENTIAL è obbligatorio impostare un pulsante negativo
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) {
            promptInfoBuilder.setNegativeButtonText("Annulla")
        }

        BiometricPrompt(this, executor, callback).authenticate(promptInfoBuilder.build())
    }

    private fun showApp() {
        webView.visibility = View.VISIBLE
        if (openMemoAfterAuth) {
            // Non resettare il flag qui — handleSharedImage chiamerà loadUrl(MEMO_URL)
            // quando l'OCR sarà pronto. Se l'OCR è già terminato, carichiamo subito.
            if (pendingOcrText != null) {
                openMemoAfterAuth = false
                webView.loadUrl(MEMO_URL)
            }
            // altrimenti il loadUrl(MEMO_URL) arriva dal callback OCR
        } else {
            webView.loadUrl(APP_URL)
        }
    }

    private fun scheduleNotifications() {
        val wm = WorkManager.getInstance(this)

        val habitWork = PeriodicWorkRequestBuilder<NotificationWorker>(24, TimeUnit.HOURS)
            .setInputData(workDataOf(
                "type" to "habit",
                "title" to "Habit Stack",
                "message" to "Hai completato le tue abitudini oggi? 💪"
            ))
            .setInitialDelay(delayUntil(hour = 20, minute = 0), TimeUnit.MILLISECONDS)
            .build()

        val taskWork = PeriodicWorkRequestBuilder<NotificationWorker>(24, TimeUnit.HOURS)
            .setInputData(workDataOf(
                "type" to "task",
                "title" to "Tasks",
                "message" to "Controlla i tuoi task di oggi 📋"
            ))
            .setInitialDelay(delayUntil(hour = 9, minute = 0), TimeUnit.MILLISECONDS)
            .build()

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
