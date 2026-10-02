package com.magneta.emeritsipmanager

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.CountDownTimer
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import java.util.Locale


class CallActivity : Activity() {

    private lateinit var phoneNumberTextView: TextView
    private lateinit var iconCallBackground: FrameLayout
    private lateinit var callDurationTextView: TextView

    private var callDurationTimer: CountDownTimer? = null
    private var callStartTime: Long = 0L
    private var isTimerRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_call)
        Log.i(TAG, "CallActivity started")
        registerReceiver(
            hangupReceiver,
            IntentFilter("com.magneta.emeritsipmanager.HANGUP_CALL"),
            RECEIVER_NOT_EXPORTED
        )
        phoneNumberTextView = findViewById(R.id.text_view_phone_number)
        iconCallBackground = findViewById(R.id.frame_icon_call_background)
        callDurationTextView = findViewById(R.id.text_view_call_duration)
    }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        this.getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            WindowManager.LayoutParams.FLAG_FULLSCREEN or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
    }
    private val hangupReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.magneta.emeritsipmanager.HANGUP_CALL") {
                hangupCallOnNetworkLoss()
            }
        }
    }
    private fun getPhoneNumber(call: Call): String {
        val phoneNumber = Utils.uriUserPart(call.peerUri)
        return if (!phoneNumber.startsWith("+")) "+$phoneNumber" else phoneNumber
    }

    private fun refreshUI() {

        try {

            Log.i(TAG, "CallActivity refreshUI ua${UserAgent.uas()}")
            val ua = UserAgent.uas()[0]
            Log.i(TAG, "CallActivity refreshUI call${Call.uaCalls(ua, "")}")

            val call = Call.uaCalls(ua, "")[0]

            phoneNumberTextView.text = getPhoneNumber(call)

            when (call.status) {

                "outgoing" -> {
                    iconCallBackground.setBackgroundResource(R.drawable.red_circle_background)
                }

                "incoming" -> {
                    iconCallBackground.setBackgroundResource(R.drawable.green_circle_background)
                }

                "connected" -> {
                    iconCallBackground.setBackgroundResource(R.drawable.red_circle_background)
                    callDurationTextView.visibility = View.VISIBLE
                    startCallDurationTimer()
                }
            }

            Log.i(TAG, "CallActivity call status: ${call.status}")
            Log.i(TAG, "CallActivity phone number: ${call.peerUri}")

        } catch (e: Exception) {
            Log.e(TAG, "CallActivity refreshUI() error : $e")
            finish()
        }

    }

    private fun startCallDurationTimer() {
        if (isTimerRunning) return

        callStartTime = SystemClock.elapsedRealtime()
        isTimerRunning = true

        callDurationTimer = object : CountDownTimer(Long.MAX_VALUE, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val elapsed = SystemClock.elapsedRealtime() - callStartTime
                val elapsedSeconds = elapsed / 1000
                val seconds = (elapsedSeconds % 60)
                val minutes = (elapsedSeconds / 60) % 60
                val hours = (elapsedSeconds / 3600)

                callDurationTextView.text = if (hours > 0) {
                    String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
                } else {
                    String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
                }
            }

            override fun onFinish() {}
        }.start()
    }

    private fun stopCallDurationTimer() {
        if (isTimerRunning) {
            callDurationTimer?.cancel()
            callDurationTimer = null
            isTimerRunning = false
        }
    }

    private fun frontButtonPressed() {
        try {

            val ua = UserAgent.uas()[0]
            val call = Call.uaCalls(ua, "")[0]

            Log.i(TAG, "CallActivity frontButtonPressed")
            Log.i(TAG, "CallActivity call status: ${call.status}")

            when (call.status) {

                "outgoing" -> {
                    hangupCall()
                }

                "incoming" -> {
                    answerCall()
                }

                "connected" -> {
                    hangupCall()
                }

            }

        } catch (e: Exception) {
            Log.e(TAG, "CallActivity frontButtonPressed() error : $e")
            finish()
        }
    }

    private fun answerCall() {
        val ua = UserAgent.uas()[0]
        val call = Call.uaCalls(ua, "in").firstOrNull()

        if (call == null) {
            Log.w(TAG, "No incoming call found to answer")
            return
        }

        Log.d(TAG, "CallActivity answering call ${call.callp} ${ua.uap} with status ${call.status}")

        if (call.status != "incoming") {
            Log.w(TAG, "Call is not in incoming state, skipping answer")
            return
        }

        Api.ua_answer(ua.uap, call.callp, Api.VIDMODE_OFF)
    }

    public fun hangupCallOnNetworkLoss(){
        hangupCall()
    }

    private fun hangupCall() {
        val uas = UserAgent.uas()
        if (uas.isEmpty()) {
            Log.w(TAG, "No user agent found to hang up")
            finish()
            return
        }
        val ua = uas[0]
        val uaCalls = Call.uaCalls(ua, "")
        if (uaCalls.isNotEmpty()) {
            val callp = uaCalls.last().callp
            Log.d(TAG, "CallActivity hanging up call")
            Api.ua_hangup(ua.uap, callp, 0, "")
        } else {
            Log.w(TAG, "No calls found to hang up")
        }
        finish()
    }

    override fun onResume() {
        super.onResume()
        refreshUI()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCallDurationTimer()
        unregisterReceiver(hangupReceiver)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {

        when (keyCode) {

            FRONT_BUTTON_KEY_CODE -> {
                frontButtonPressed()
            }
        }

        return true
    }

    companion object {
        const val FRONT_BUTTON_KEY_CODE = 66
        fun sendHangupBroadcast(context: Context) {
            val intent = Intent("com.magneta.emeritsipmanager.HANGUP_CALL")
            context.sendBroadcast(intent)
        }
    }
}