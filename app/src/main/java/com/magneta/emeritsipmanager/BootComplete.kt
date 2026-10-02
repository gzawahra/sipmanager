package com.magneta.emeritsipmanager;

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootComplete : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i("BootComplete", "onReceive: Boot locked receive")
    }
}