package com.calltranslator

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Re-enables call detection after device reboot.
 * Nothing to start here — CallReceiver handles calls automatically.
 * This receiver just ensures the app is alive to receive call broadcasts.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // App will receive call broadcasts automatically after boot
            // No explicit action needed — Android re-registers receivers on boot
        }
    }
}
