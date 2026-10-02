package com.magneta.emeritsipmanager

import android.app.Application
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi

class EmeritSip : Application() {
    private var sipActionReceiver: SipActionReceiver? = null

    override fun onCreate() {
        super.onCreate()
        Log.i("EmeritSip", "onCreate: EmeritSip")
        sipActionReceiver = SipActionReceiver()
        this.registerReceiver(
            sipActionReceiver,
            IntentFilter("com.magneta.emeritsipmanager.actions"),
            RECEIVER_EXPORTED
        )
    }
}