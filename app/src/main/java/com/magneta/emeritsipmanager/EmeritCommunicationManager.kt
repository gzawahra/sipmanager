package com.magneta.emeritsipmanager

import android.content.Context
import android.content.Intent
import android.util.Log

class EmeritCommunicationManager {

    companion object {

        private val intent = Intent().setPackage("com.magneta.e_wg200")

        fun broadcastSIPEvents(context: Context?, status: String, extraMessage: String) {
            broadcast(context, intent, status, extraMessage)
            Log.i(TAG, "broadcastSIPEvents send with status $status extraMessage $extraMessage")

        }

        private fun broadcast(
            context: Context?,
            intent: Intent,
            status: String,
            extraMessage: String
        ) {
            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            intent.action = "com.magneta.coreapp.SIP_EVENTS"
            intent.putExtra("ACTION_EXTRA", status)
            if (extraMessage.isNotEmpty()) {
                intent.putExtra("ACTION_EXTRA_MESSAGE", extraMessage)
            }
            context?.sendBroadcast(intent)
        }
    }
}