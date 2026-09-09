package com.kemzy.liveavatar

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

class PrivacyLockActivity : FragmentActivity() {
    private val preferences by lazy { getSharedPreferences("privacy_gate", MODE_PRIVATE) }
    private lateinit var passcode: EditText
    private lateinit var message: TextView
    private lateinit var biometricButton: Button

    private val biometricEnabled: Boolean
        get() = preferences.getBoolean(KEY_BIOMETRIC_ENABLED, false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        buildLockUi()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        // Do not allow the privacy gate to be dismissed.
        moveTaskToBack(false)
    }

    private fun buildLockUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(40, 40, 40, 40)
            setBackgroundColor(0xFF000000.toInt())
        }

        root.addView(TextView(this).apply {
            text = "Kemzy-LiveAvatar"
            textSize = 26f
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
        })
        root.addView(TextView(this).apply {
            text = "Enter your passcode to continue"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(0xFFBBBBBB.toInt())
            setPadding(0, 12, 0, 20)
        })

        passcode = EditText(this).apply {
            hint = "Passcode"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF888888.toInt())
            gravity = Gravity.CENTER
            maxLines = 1
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
            isLongClickable = false
            setTextIsSelectable(false)
        }
        root.addView(passcode, LinearLayout.LayoutParams(-1, -2))

        val unlock = Button(this).apply {
            text = "Unlock"
            setOnClickListener { verifyPasscodeAndUnlock() }
        }
        root.addView(unlock, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 16 })

        biometricButton = Button(this).apply {
            text = if (biometricEnabled) "Passcode + Fingerprint" else "Enable Fingerprint (Optional)"
            visibility = if (biometricAvailable()) View.VISIBLE else View.GONE
            setOnClickListener { biometricFlowAfterPasscode() }
        }
        root.addView(biometricButton, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 8 })

        message = TextView(this).apply {
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(0xFFFF8080.toInt())
            setPadding(0, 14, 0, 0)
        }
        root.addView(message, LinearLayout.LayoutParams(-1, -2))

        setContentView(root)
        passcode.requestFocus()
    }

    private fun verifyPasscodeAndUnlock() {
        if (!PrivacyGate.verifyPasscode(passcode.text)) {
            message.text = "Incorrect passcode"
            passcode.text?.clear()
            return
        }
        openMainActivity()
    }

    private fun biometricFlowAfterPasscode() {
        if (!PrivacyGate.verifyPasscode(passcode.text)) {
            message.text = "Enter the correct passcode before using fingerprint"
            passcode.text?.clear()
            return
        }

        showBiometricPrompt(enableAfterSuccess = !biometricEnabled)
    }

    private fun biometricAvailable(): Boolean {
        return BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun showBiometricPrompt(enableAfterSuccess: Boolean) {
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                if (enableAfterSuccess) {
                    preferences.edit().putBoolean(KEY_BIOMETRIC_ENABLED, true).apply()
                }
                openMainActivity()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                message.text = "Fingerprint was not confirmed"
            }
        })

        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(if (enableAfterSuccess) "Confirm fingerprint setup" else "Confirm fingerprint")
                .setSubtitle("Your Kemzy-LiveAvatar passcode was accepted first")
                .setDescription("Fingerprint is optional. Cancel to continue with passcode only.")
                .setNegativeButtonText("Cancel")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build()
        )
    }

    private fun openMainActivity() {
        (application as PrivacyApplication).markUnlocked()
        startActivity(android.content.Intent(this, MainActivity::class.java))
        finish()
    }

    companion object {
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
    }
}