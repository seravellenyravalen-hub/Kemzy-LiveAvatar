package com.kemzy.liveavatar

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.ComponentActivity

class LockActivity : ComponentActivity() {
    private val appLock by lazy { (application as KemzyApplication).privacyLock }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lock)
        val input = findViewById<EditText>(R.id.passcodeInput)
        val error = findViewById<TextView>(R.id.passcodeError)
        findViewById<Button>(R.id.unlockButton).setOnClickListener {
            if (appLock.unlock(input.text.toString())) {
                setResult(RESULT_OK)
                finish()
            } else {
                error.text = "Incorrect passcode"
                input.text.clear()
            }
        }
    }
}
