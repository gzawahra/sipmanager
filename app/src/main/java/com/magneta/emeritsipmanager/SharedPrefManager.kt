package com.magneta.emeritsipmanager

import android.content.Context
import android.os.Bundle

class SharedPrefManager {

    companion object {

        private const val SHARED_PREF_NAME = "EmeritSipManagerSharedPref"

        fun saveSipAccountToDisk(context: Context?, sipAccountBundle: Bundle): Boolean {

            if (context == null) return false

            val sipAccountName: String =
                sipAccountBundle.getString(SipActionReceiver.BUNDLE_SIP_ACCOUNT_NAME)
                    ?: return false
            val sipUserName: String =
                sipAccountBundle.getString(SipActionReceiver.BUNDLE_SIP_USER_NAME) ?: return false
            val sipUserPassword: String =
                sipAccountBundle.getString(SipActionReceiver.BUNDLE_SIP_PASSWORD) ?: return false
            val sipServerUrl: String =
                sipAccountBundle.getString(SipActionReceiver.BUNDLE_SIP_SERVER_URL) ?: return false

            val sharedPreferences =
                context.getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE)

            val editor = sharedPreferences.edit()
            editor.putString(
                SipActionReceiver.BUNDLE_SIP_ACCOUNT_NAME, sipAccountName
            )
            editor.putString(SipActionReceiver.BUNDLE_SIP_USER_NAME, sipUserName)
            editor.putString(SipActionReceiver.BUNDLE_SIP_PASSWORD, sipUserPassword)
            editor.putString(SipActionReceiver.BUNDLE_SIP_SERVER_URL, sipServerUrl)

            return editor.commit()
        }

        fun getSipAccount(context: Context?): SipAccount? {

            if (context == null) return null

            val sharedPreferences =
                context.getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE)

            val sipAccountName: String =
                sharedPreferences.getString(SipActionReceiver.BUNDLE_SIP_ACCOUNT_NAME, null)
                    ?: return null
            val sipUserName: String =
                sharedPreferences.getString(SipActionReceiver.BUNDLE_SIP_USER_NAME, null)
                    ?: return null
            val sipUserPassword: String =
                sharedPreferences.getString(SipActionReceiver.BUNDLE_SIP_PASSWORD, null)
                    ?: return null
            val sipServerUrl: String =
                sharedPreferences.getString(SipActionReceiver.BUNDLE_SIP_SERVER_URL, null)
                    ?: return null

            return SipAccount(sipAccountName, sipUserName, sipUserPassword, sipServerUrl)
        }
    }
}