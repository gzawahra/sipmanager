package com.magneta.emeritsipmanager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File

class SipActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {

        Log.i(TAG, "EmeritAppCmdReceiver called ${intent?.getStringExtra(ACTION_EXTRA)}")

        when (intent?.getStringExtra(ACTION_EXTRA)) {

            ACTION_REGISTER_SIP -> {
                intent.getBundleExtra(ACTION_DATA)?.let {
                    if (SharedPrefManager.saveSipAccountToDisk(context, it)) {
                        Log.i(TAG, "Sip credentials saved, restarting")
                        SipUtils.startSipService(context)
                        val restartIntent = Intent(context, SipService::class.java)
                        restartIntent.action = "restart"
                        context?.startService(restartIntent)
                    } else {
                        EmeritCommunicationManager.broadcastSIPEvents(
                            context,
                            "Account saving to disk failed"
                        ,"")
                    }
                }
            }

            ACTION_UNREGISTER_SIP -> {
                SipUtils.unRegisterSipAccount(context)
            }

            ACTION_MAKE_CALL -> {
                intent.getBundleExtra(ACTION_DATA)?.let {
                    val phoneNumber = it.getString(BUNDLE_PHONE_NUMBER) ?: return
                    SipService.isSpeakerPhoneEnable = it.getBoolean(BUNDLE_SPEAKERPHONE_ON)
                    SipService.isShowCallActivity = it.getBoolean(BUNDLE_SIP_IS_SHOW_CALL_ACTIVITY)
                    Log.i(TAG, "isShowCallActivity ACTION_MAKE_CALL : ${SipService.isShowCallActivity}")
                    SipUtils.makeCall(phoneNumber)
                }
            }

            ACTION_ANSWER_CALL -> {
                intent.getBundleExtra(ACTION_DATA)?.let {
                    SipService.isSpeakerPhoneEnable = it.getBoolean(BUNDLE_SPEAKERPHONE_ON)
                    SipService.isShowCallActivity = it.getBoolean(BUNDLE_SIP_IS_SHOW_CALL_ACTIVITY)
                    SipService.isPostAlertRunning = it.getBoolean(BUNDLE_SIP_IS_POST_ALERT_RUNNING)

                    Log.i(TAG, "isSpeakerphoneEnable : ${SipService.isSpeakerPhoneEnable}")
                    Log.i(TAG, "isShowCallActivity :ACTION_ANSWER_CALL  ${SipService.isShowCallActivity}")
                    Log.i(TAG, "isPostAlertRunning : ${SipService.isPostAlertRunning}")

                    if (context != null && !SipService.isSpeakerPhoneEnable) {
                        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                        audioManager.mode = android.media.AudioManager.MODE_IN_COMMUNICATION
                        audioManager.isSpeakerphoneOn = false
                    }

                    SipUtils.answerCall()
                }
            }

            ACTION_END_CALL -> {
                SipUtils.endCall()
            }

            ACTION_SEND_MESSAGE -> {
                intent.getBundleExtra(ACTION_DATA)?.let {
                    val senderID = it.getString("SENDER_ID") ?: return
                    val serverUrl = it.getString("SERVER_URL") ?: return
                    val messageToSend = it.getString("MESSAGE") ?: return
                    SipUtils.sendMessage(senderID, serverUrl, messageToSend)
                }
            }
        }
    }

    companion object {
        const val ACTION_EXTRA = "ACTION_EXTRA"
        const val ACTION_DATA = "ACTION_DATA"
        const val ACTION_REGISTER_SIP = "REGISTER_SIP"
        const val ACTION_UNREGISTER_SIP = "UNREGISTER_SIP"
        const val ACTION_MAKE_CALL = "MAKE_CALL"
        const val ACTION_ANSWER_CALL = "ANSWER_CALL"
        const val ACTION_END_CALL = "END_CALL"
        const val ACTION_SEND_MESSAGE = "SEND_MESSAGE"
        const val BUNDLE_SPEAKERPHONE_ON = "SPEAKERPHONE_ON"
        const val BUNDLE_PHONE_NUMBER = "PHONE_NUMBER"
        const val BUNDLE_SIP_ACCOUNT_NAME = "SIP_ACCOUNT_NAME"
        const val BUNDLE_SIP_USER_NAME = "SIP_USER_NAME"
        const val BUNDLE_SIP_PASSWORD = "SIP_PASSWORD"
        const val BUNDLE_SIP_SERVER_URL = "SIP_SERVER_URL"
        const val BUNDLE_SIP_IS_SHOW_CALL_ACTIVITY ="SHOW_CALL_ACTIVITY"
        const val BUNDLE_SIP_IS_POST_ALERT_RUNNING ="POST_ALERT_RUNNING"
    }

    init {
        if (!SipService.libraryLoaded) {
            Log.d(TAG, "Loading baresip library")
            System.loadLibrary("baresip")
            SipService.libraryLoaded = true
        }
    }

}