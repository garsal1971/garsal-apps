package com.garsal.appsphere.core

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Impronta all'avvio, con il PIN del telefono come ripiego — le stesse due
 * strade dell'APK WebView (`MainActivity.kt`, `BIOMETRIC_STRONG or DEVICE_CREDENTIAL`).
 *
 * Se il telefono non ha né impronta né PIN registrati non si blocca niente:
 * chiedere una credenziale che non esiste lascerebbe l'app inutilizzabile.
 */
object BiometricGate {

    private const val CONSENTITI = BIOMETRIC_STRONG or DEVICE_CREDENTIAL

    fun disponibile(activity: FragmentActivity): Boolean =
        BiometricManager.from(activity).canAuthenticate(CONSENTITI) ==
            BiometricManager.BIOMETRIC_SUCCESS

    fun chiedi(
        activity: FragmentActivity,
        onSbloccato: () -> Unit,
        onRinuncia: () -> Unit,
    ) {
        if (!disponibile(activity)) {
            onSbloccato()
            return
        }

        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSbloccato()
                }

                // Errore ≠ tentativo sbagliato: qui ci finiscono l'annullamento
                // e il "troppi tentativi". In entrambi i casi l'app resta chiusa
                // e si riprova dal pulsante, senza sbloccare niente.
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onRinuncia()
                }
            }
        )

        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("AppSphere")
                .setSubtitle("Sblocca per continuare")
                .setAllowedAuthenticators(CONSENTITI)
                .build()
        )
    }
}
