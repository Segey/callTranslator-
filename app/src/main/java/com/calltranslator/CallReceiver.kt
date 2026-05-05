package com.calltranslator

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

/**
 * Detects incoming and outgoing calls, starts/stops TranslationService accordingly.
 */
class CallReceiver : BroadcastReceiver() {

    companion object {
        private var lastState = TelephonyManager.CALL_STATE_IDLE
        private var callStarted = false
    }

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("enabled", true)) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return

        val currentState = when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> TelephonyManager.CALL_STATE_RINGING
            TelephonyManager.EXTRA_STATE_OFFHOOK -> TelephonyManager.CALL_STATE_OFFHOOK
            TelephonyManager.EXTRA_STATE_IDLE -> TelephonyManager.CALL_STATE_IDLE
            else -> return
        }

        when {
            // Call answered (ringing -> offhook OR idle -> offhook for outgoing)
            currentState == TelephonyManager.CALL_STATE_OFFHOOK &&
                    lastState != TelephonyManager.CALL_STATE_OFFHOOK -> {
                startTranslation(context)
                callStarted = true
            }

            // Call ended
            currentState == TelephonyManager.CALL_STATE_IDLE && callStarted -> {
                stopTranslation(context)
                callStarted = false
            }
        }

        lastState = currentState
    }

    private fun startTranslation(context: Context) {
        val serviceIntent = Intent(context, TranslationService::class.java).apply {
            action = TranslationService.ACTION_START
        }
        ContextCompat.startForegroundService(context, serviceIntent)
    }

    private fun stopTranslation(context: Context) {
        val serviceIntent = Intent(context, TranslationService::class.java).apply {
            action = TranslationService.ACTION_STOP
        }
        context.startService(serviceIntent)
    }
}
