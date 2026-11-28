package com.github.codeworkscreativehub.mlauncher.helper.utils

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.github.codeworkscreativehub.common.getLocalizedString
import com.github.codeworkscreativehub.common.showLongToast
import com.github.codeworkscreativehub.mlauncher.R
import com.github.codeworkscreativehub.mlauncher.data.AppListItem

class BiometricHelper(private val activity: FragmentActivity) {

    interface CallbackApp {
        fun onAuthenticationSucceeded(appListItem: AppListItem)
        fun onAuthenticationFailed()
        fun onAuthenticationError(errorCode: Int, errorMessage: CharSequence?)
    }

    interface CallbackSettings {
        fun onAuthenticationSucceeded()
        fun onAuthenticationFailed()
        fun onAuthenticationError(errorCode: Int, errorMessage: CharSequence?)
    }

    fun startBiometricAuth(appListItem: AppListItem, callbackApp: CallbackApp) = authenticate(
        getLocalizedString(R.string.text_biometric_login_app, appListItem.activityLabel),
        onSuccess = { callbackApp.onAuthenticationSucceeded(appListItem) },
        onFailed = callbackApp::onAuthenticationFailed,
        onError = callbackApp::onAuthenticationError
    )

    fun startBiometricSettingsAuth(callbackApp: CallbackSettings) = authenticate(
        getLocalizedString(R.string.text_biometric_login_sub),
        onSuccess = callbackApp::onAuthenticationSucceeded,
        onFailed = callbackApp::onAuthenticationFailed,
        onError = callbackApp::onAuthenticationError
    )

    /** Protects an action such as opening a locked folder. Failures and cancellations do nothing. */
    fun protect(subtitle: String, onSuccess: () -> Unit) =
        authenticate(subtitle, onSuccess, onFailed = {}, onError = { _, _ -> })

    /**
     * Without any screen lock Android cannot verify the user, and anyone holding the phone can
     * already reach everything. Blocking would only lock the owner out of the launcher (for
     * example after removing their PIN), so the action proceeds with a visible warning.
     */
    private fun authenticate(
        subtitle: String,
        onSuccess: () -> Unit,
        onFailed: () -> Unit,
        onError: (Int, CharSequence?) -> Unit
    ) {
        if (!canAuthenticate(activity)) {
            activity.showLongToast(getLocalizedString(R.string.mooncat_lock_inactive))
            onSuccess()
            return
        }
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
            override fun onAuthenticationFailed() = onFailed()
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onError(errorCode, errString)
        })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(getLocalizedString(R.string.text_biometric_login))
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(AUTHENTICATORS)
                .setConfirmationRequired(false)
                .build()
        )
    }

    companion object {
        private const val AUTHENTICATORS =
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL

        fun canAuthenticate(activity: FragmentActivity): Boolean =
            BiometricManager.from(activity).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS
    }
}
