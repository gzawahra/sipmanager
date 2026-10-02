package com.magneta.emeritsipmanager

import java.util.GregorianCalendar

class Call(var callp: Long, val ua: UserAgent, val peerUri: String, val dir: String, var status: String) {

    var onhold = false
    var held = false
    var onHoldCall: Call? = null
    var newCall: Call? = null
    var rejected = false  // Incoming rejected by user or outgoing fails but not due to 408 or 480
    var security = 0
    var zid = ""
    var startTime: GregorianCalendar? = null  // Set when call is established
    var referTo = ""
    var dumpfiles = arrayOf("", "")
    var msgp: Long = 0L
    fun add() {
        SipService.calls.add(0, this)
    }

    fun remove() {
        SipService.calls.remove(this)
    }

    fun connect(uri: String): Boolean {
        return Api.call_connect(callp, uri) == 0
    }

    fun hold(): Boolean {
        return Api.call_hold(callp, true) == 0
    }

    fun resume(): Boolean {
        return Api.call_hold(callp, false) == 0
    }

    fun transfer(uri: String): Boolean {
        val err = Api.call_hold(callp, true)
        if (err != 0)
            return false
        referTo = uri
        return Api.call_transfer(callp, uri) == 0
    }

    fun executeTransfer(): Boolean {
        return if (this.onHoldCall != null) {
            if (Api.call_hold(callp, true) == 0)
                Api.call_replace_transfer(this.onHoldCall!!.callp, callp)
            else
                false
        } else
            false
    }

    fun sendDigit(digit: Char): Int {
        return Api.call_send_digit(callp, digit)
    }

    fun notifySipfrag(code: Int, reason: String) {
        Api.call_notify_sipfrag(callp, code, reason)
    }

    fun duration(): Int {
        return Api.call_duration(callp)
    }

    fun stats(stream: String): String {
        return Api.call_stats(callp, stream)
    }

    fun state(): Int {
        return Api.call_state(callp)
    }

    fun audioCodecs(): String {
        return Api.call_audio_codecs(callp)
    }

    fun replaces(): Boolean {
        return Api.call_replaces(callp)
    }

    fun diverterUri(): String {
        return Api.call_diverter_uri(callp)
    }

    init {
        if (ua.account.mediaEnc != "") security = R.drawable.box_red
    }

    fun destroy() {
        Api.call_destroy(callp)
    }

    companion object {

        fun calls(): ArrayList<Call> {
            return SipService.calls
        }

        fun uaCalls(ua: UserAgent, dir: String): ArrayList<Call> {
            val result = ArrayList<Call>()
            for (c in SipService.calls)
                if ((c.ua == ua) && ((dir == "") || c.dir == dir)) result.add(c)
            return result
        }

        fun ofCallp(callp: Long): Call? {
            for (c in SipService.calls)
                if (c.callp == callp) return c
            return null
        }

        fun call(status: String): Call? {
            for (c in SipService.calls.reversed())
                if (c.status == status) return c
            return null
        }

        fun inCall(): Boolean {
            return SipService.calls.isNotEmpty()
        }
    }
}