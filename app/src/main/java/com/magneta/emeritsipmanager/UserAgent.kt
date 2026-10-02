package com.magneta.emeritsipmanager

import com.magneta.emeritsipmanager.SipService.Companion.uas
import com.magneta.emeritsipmanager.SipService.Companion.uasStatus

class UserAgent(var uap: Long) {

    val account = Account(Api.ua_account(uap))
    var status = R.drawable.dot_white

    fun callAlloc(xCall: Long, videoMode: Int): Long {
        return Api.ua_call_alloc(uap, xCall, videoMode)
    }

    fun add(status: Int) {
        uas.add(this)
        uasStatus.add(status)
    }

    fun remove() {
        val index = uas.indexOf(this)
        uas.remove(this)
        uasStatus.removeAt(index)
    }

    fun uaUpdateStatus(status: Int) {
        uasStatus[uas.indexOf(this)] = status
    }

    fun calls(dir: String = ""): ArrayList<Call> {
        val result = ArrayList<Call>()
        for (c in SipService.calls)
            if ((c.ua == this) && ((dir == "") || c.dir == dir)) result.add(c)
        return result
    }

    fun currentCall(): Call? {
        for (c in SipService.calls)
            if (c.ua == this)
                return c
        return null
    }

    companion object {

        fun uas(): ArrayList<UserAgent> = uas


        fun ofUap(uap: Long): UserAgent? {
            for (ua in uas)
                if (ua.uap == uap) return ua
            return null
        }

        fun uaAlloc(uri: String): UserAgent? {
            val uap = Api.ua_alloc(uri)
            if (uap != 0L) return UserAgent(uap)
            Log.e(TAG, "Failed to allocate UserAgent for $uri")
            return null
        }

        fun register() {
            for (ua in uas) {
                val regint = Api.account_regint(ua.account.accp)
                if (ua.account.regint > 0 && regint == 0) {
                    // Only register if not already registered!
                    if (Api.ua_register(ua.uap) != 0)
                        Log.d(TAG, "Failed to register ${ua.account.aor}")
                } else if (regint > 0) {
                    Log.d(TAG, "UA ${ua.account.aor} is already registered (regint=$regint)")
                }
            }
        }
    }
}