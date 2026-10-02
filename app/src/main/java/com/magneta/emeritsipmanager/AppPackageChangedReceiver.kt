package com.magneta.emeritsipmanager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class AppPackageChangedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        context ?: return

        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            val serviceIntent = Intent(context, SipService::class.java)
            Log.d(TAG, "LOCKED BOOT COMPLETED!")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } else {
            Log.d(TAG, "App updated!")
            EmeritCommunicationManager.broadcastSIPEvents(context, "SipManagerUpdated", "")
        }
    }
}