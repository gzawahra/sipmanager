package com.magneta.emeritsipmanager

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.os.persistableBundleOf
import java.util.concurrent.TimeUnit


class SipUtils {

    companion object {

        private var saveConfig = false

        fun startSipService(context: Context?) {
            if (!SipService.isServiceRunning) {
                val sipServiceIntent = Intent(context, SipService::class.java)
                sipServiceIntent.action = "Start"
                context?.startForegroundService(sipServiceIntent)
            } else {
                Log.i(TAG, "Service already running")
            }
        }

        private fun stopSipService(context: Context?) {
            if (SipService.isServiceRunning) {
                val sipServiceIntent = Intent(context, SipService::class.java)
                sipServiceIntent.action = "Kill"
                context?.startService(sipServiceIntent)
                Log.i(TAG, "Stopping SipService")
            } else {
                Log.i(TAG, "SipService not running")
            }
        }

        fun unRegisterSipAccount(context: Context?) {

            try {

                val userAgent = UserAgent.uas()[0]
                Api.ua_destroy(userAgent.uap)
                userAgent.remove()

                stopSipService(context)

                Log.d(TAG, "Old account unregistered")
                EmeritCommunicationManager.broadcastSIPEvents(context, "unRegistered", "")

            } catch (e: Exception) {
                Log.d(TAG, "unRegister error")
                EmeritCommunicationManager.broadcastSIPEvents(context, "unRegister failed", "")
            }
        }

        fun registerAccount(context: Context?, sipAccount: SipAccount) {

            Log.i(TAG, "Registering new account")

            val sipDisplayName = sipAccount.sipAccountName
            val sipUserName = sipAccount.sipUserName
            val sipUserPassword = sipAccount.sipUserPassword
            val sipServerUrl = sipAccount.sipServerUrl

            Log.i(TAG, "Registering $sipUserName with url $sipServerUrl")

            val outbound1 = ""
            val outbound2 = ""
            val regCheck = true
            val mediaNat = ""
            val stunServer = ""
            val stunUser = ""
            val stunPass = ""
            val mediaEnc = ""
            val dtmfMode = 0
            val answerMode = Api.ANSWERMODE_MANUAL
            val vmUri = ""
            val defaultCheck = true
            val uaIndex = 0
            // set regint, registration duration: low limit appear to be 1 minute
            val registrationIntervalInSec = TimeUnit.MINUTES.toSeconds(10).toInt()

            val aor = "${sipUserName}@${sipServerUrl}"
            val laddr = "sip:$aor"

            if (!Utils.checkAor("sip:$aor")) {
                Log.e(TAG, "Invalid Address of Record $aor")
                return
            }
            if (Account.ofAor("sip:$aor") != null) {
                Log.d(TAG, "Account $aor already exists")
                return
            }

            val userAgent =
                UserAgent.uaAlloc("<$laddr>;stunserver=\"stun:stun.l.google.com:19302\";regq=0.5;pubint=0;regint=0;mwi=no")
            if (userAgent == null) {
                Log.e(TAG, "Failed to allocate UA for $aor")
                return
            }

            Log.d(
                TAG,
                "Allocated UA ${userAgent.uap} for ${Api.account_luri(userAgent.account.accp)}"
            )
            userAgent.add(R.drawable.dot_white)

            Log.d(TAG, "Account info : " + userAgent.account.print())

            val account = userAgent.account

            if (sipDisplayName != account.displayName) {
                if (Account.checkDisplayName(sipDisplayName)) {
                    if (Api.account_set_display_name(account.accp, sipDisplayName) == 0) {
                        account.displayName = Api.account_display_name(account.accp)
                        Log.d(TAG, "New display name is ${account.displayName}")
                        saveConfig = true
                    } else {
                        Log.e(TAG, "Setting of display name failed")
                    }
                } else {
                    Log.e(TAG, "Invalid display name")
                    return
                }
            }

            if (sipUserName != account.authUser) {
                if (Account.checkAuthUser(sipUserName)) {
                    if (Api.account_set_auth_user(account.accp, sipUserName) == 0) {
                        account.authUser = Api.account_auth_user(account.accp)
                        Log.d(TAG, "New auth user is ${account.authUser}")
                        saveConfig = true
                    } else {
                        Log.e(TAG, "Setting of auth user failed")
                    }
                } else {
                    Log.e(TAG, "Invalid auth username")
                    return
                }
            }

            if (sipUserPassword != "") {
                if (sipUserPassword != account.authPass) {
                    if (Account.checkAuthPass(sipUserPassword)) {
                        setAuthPass(account, sipUserPassword)
                        //aorPasswords.remove(aor)
                    } else {
                        Log.e(TAG, "Invalid password")
                        return
                    }
                } else {
                    saveConfig = true
                }
            } else { // ap == ""
                if (account.authUser != "") {
                    /*if (!aorPasswords.containsKey(aor)) {
                        setAuthPass(account, "")
                        aorPasswords[aor] = ""
                    }*/
                } else {
                    if (account.authPass != "") {
                        setAuthPass(account, "")
                    }
                }
            }

            val ob = java.util.ArrayList<String>()
            var uri: String
            if (outbound1 != "") {
                uri = outbound1.trim().replace(" ", "")
                if (!uri.startsWith("sip:")) uri = "sip:$uri"
                ob.add(uri)
            }
            if (outbound2.trim() != "") {
                uri = outbound2.trim().replace(" ", "")
                if (!uri.startsWith("sip:")) uri = "sip:$uri"
                ob.add(uri)
            }
            if (ob != account.outbound) {
                val outbound = java.util.ArrayList<String>()
                for (i in ob.indices) {
                    if ((ob[i] == "") || checkOutboundUri(ob[i])) {
                        if (Api.account_set_outbound(account.accp, ob[i], i) == 0) {
                            if (ob[i] != "")
                                outbound.add(Api.account_outbound(account.accp, i))
                        } else {
                            Log.e(TAG, "Setting of outbound proxy ${ob[i]} failed")
                            break
                        }
                    } else {
                        Log.e(TAG, "Invalid proxy server url")
                        return
                    }
                }
                Log.d(TAG, "New outbound proxies are $outbound")
                account.outbound = outbound
                if (outbound.isEmpty())
                    Api.account_set_sipnat(account.accp, "")
                else
                    Api.account_set_sipnat(account.accp, "outbound")
                saveConfig = true
            }

            var newRegint = -1
            if (regCheck) {
                if (account.regint != 3600) {
                    newRegint = registrationIntervalInSec
                    userAgent.uaUpdateStatus(R.drawable.dot_yellow)
                    Log.i(TAG, "Regint set to $registrationIntervalInSec sec")
                }
            } else {
                if (account.regint != 0) {
                    Api.ua_unregister(userAgent.uap)
                    userAgent.uaUpdateStatus(R.drawable.dot_white)
                    newRegint = 0
                }
            }
            if (newRegint != -1)
                if (Api.account_set_regint(account.accp, newRegint) == 0) {
                    account.regint = Api.account_regint(account.accp)
                    Log.d(TAG, "New regint is ${account.regint}")
                    saveConfig = true
                } else {
                    Log.e(TAG, "Setting of regint failed")
                }

            if (mediaNat != account.mediaNat) {
                if (Api.account_set_medianat(account.accp, mediaNat) == 0) {
                    account.mediaNat = Api.account_medianat(account.accp)
                    Log.d(TAG, "New medianat is ${account.mediaNat}")
                    saveConfig = true
                } else {
                    Log.e(TAG, "Setting of medianat failed")
                }
            }

            var newStunServer = stunServer.trim()
            if (mediaNat != "") {
                if (((mediaNat == "stun") || (mediaNat == "ice")) && (newStunServer == ""))
                    newStunServer = "stun:stun.l.google.com:19302"
                if (!Utils.checkStunUri(newStunServer) ||
                    (mediaNat == "turn" &&
                            newStunServer.substringBefore(":") !in setOf("turn", "turns"))
                ) {
                    Log.e(TAG, "Invalid stun server")
                    return
                }
            }

            if (account.stunServer != newStunServer) {
                if (Api.account_set_stun_uri(account.accp, newStunServer) == 0) {
                    account.stunServer = Api.account_stun_uri(account.accp)
                    Log.d(TAG, "New STUN/TURN server URI is '${account.stunServer}'")
                    saveConfig = true
                } else {
                    Log.e(TAG, "Setting of STUN/TURN URI server failed")
                }
            }

            val newStunUser = stunUser.trim()
            if (account.stunUser != newStunUser) {
                if (Account.checkAuthUser(newStunUser)) {
                    if (Api.account_set_stun_user(account.accp, newStunUser) == 0) {
                        account.stunUser = Api.account_stun_user(account.accp)
                        Log.d(TAG, "New STUN/TURN user is ${account.stunUser}")
                        saveConfig = true
                    } else {
                        Log.e(TAG, "Setting of STUN/TURN user failed")
                    }
                } else {
                    Log.e(TAG, "Invalid stun username")
                    return
                }
            }

            val newStunPass = stunPass.trim()
            if (account.stunPass != newStunPass) {
                if (newStunPass.isEmpty() || Account.checkAuthPass(newStunPass)) {
                    if (Api.account_set_stun_pass(account.accp, newStunPass) == 0) {
                        account.stunPass = Api.account_stun_pass(account.accp)
                        saveConfig = true
                    } else {
                        Log.e(TAG, "Setting of stun pass failed")
                    }
                } else {
                    Log.e(TAG, "Invalid stun password")
                    return
                }
            }

            if (mediaEnc != account.mediaEnc) {
                if (Api.account_set_mediaenc(account.accp, mediaEnc) == 0) {
                    account.mediaEnc = Api.account_mediaenc(account.accp)
                    Log.d(TAG, "New mediaenc is ${account.mediaEnc}")
                    saveConfig = true
                } else {
                    Log.e(TAG, "Setting of mediaenc $mediaEnc failed")
                }
            }

            if (dtmfMode != account.dtmfMode) {
                if (Api.account_set_dtmfmode(account.accp, dtmfMode) == 0) {
                    account.dtmfMode = Api.account_dtmfmode(account.accp)
                    Log.d(TAG, "New dtmfmode is ${account.dtmfMode}")
                    saveConfig = true
                } else {
                    Log.e(TAG, "Setting of dtmfmode $dtmfMode failed")
                }
            }

            if (answerMode != account.answerMode) {
                if (Api.account_set_answermode(account.accp, answerMode) == 0) {
                    account.answerMode = Api.account_answermode(account.accp)
                    Log.d(TAG, "New answermode is ${account.answerMode}")
                    saveConfig = true
                } else {
                    Log.e(TAG, "Setting of answermode $answerMode failed")
                }
            }

            var tVmUri = vmUri.trim()
            if (tVmUri != account.vmUri) {
                if (tVmUri != "") {
                    if (!tVmUri.startsWith("sip:")) tVmUri = "sip:$tVmUri"
                    if (!tVmUri.contains("@")) tVmUri = "$tVmUri@${account.host()}"
                    if (!Utils.checkSipUri(tVmUri)) {
                        Log.e(TAG, "Invalid voicemail uri")
                        return
                    }
                    Api.account_set_mwi(account.accp, true)
                } else {
                    Api.account_set_mwi(account.accp, false)
                }
                account.vmUri = tVmUri
                saveConfig = true
            }

            if (defaultCheck && (uaIndex > 0)) {
                val uasTmp = SipService.uas[0]
                val statusTmp = SipService.uasStatus[0]
                SipService.uas[0] = SipService.uas[uaIndex]
                SipService.uasStatus[0] = SipService.uasStatus[uaIndex]
                SipService.uas[uaIndex] = uasTmp
                SipService.uasStatus[uaIndex] = statusTmp
                saveConfig = true
            }


            if (saveConfig) {
                if (Api.ua_update_account(userAgent.uap).toInt() != 0)
                    Log.e(TAG, "Failed to update UA ${userAgent.uap} with AoR $aor")
            }

            val cm = context?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val capabilities = cm?.getNetworkCapabilities(cm.activeNetwork)
            val hasInternet = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

            if (hasInternet) {
                Handler(Looper.getMainLooper()).postDelayed({
                    Api.ua_register(userAgent.uap)
                    Log.i(TAG, "Delayed registration triggered after internet confirmed")
                }, 1500)
            } else {
                Log.e(TAG, "No internet connectivity — skipping SIP registration")
            }


            SipService.activities.remove("account,$aor")

        }

        private fun setAuthPass(acc: Account, ap: String) {
            if (Api.account_set_auth_pass(acc.accp, ap) == 0) {
                acc.authPass = Api.account_auth_pass(acc.accp)
                //aorPasswords.remove(acc.aor)
                saveConfig = true
            } else {
                Log.e(TAG, "Setting of auth pass failed")
            }
        }

        private fun checkOutboundUri(uri: String): Boolean {
            if (!uri.startsWith("sip:")) return false
            return Utils.checkHostPortParams(uri.substring(4))
        }


        fun makeCall(phoneNumber: String?) {
            Log.i(TAG, "MakeCall function called")
            if (phoneNumber == null) {
                Log.e(TAG, "Unknown phone number")
                return
            }
            try {
                val userAgent = UserAgent.uas()[0]
                val aor = userAgent.account.aor
                val callp = userAgent.callAlloc(0L, Api.VIDMODE_OFF)
                if (Call.calls().isEmpty()) {
                    val uriText = phoneNumber.trim()
                    if (uriText.isNotEmpty()) {
                        val uri = Utils.uriComplete(
                            findContactURI(uriText),
                            Utils.aorDomain(aor)
                        )
                        if (!Utils.checkSipUri(uri)) {
                            Log.e(TAG, "Invalid SIP URI")
                       } else if(callp != 0L) {
                            val call = Call(callp = callp, ua = userAgent, peerUri = uri, dir = "out", status = "outgoing")
                            call.add()
                            if (call.connect(uri)) {
                                Log.d(TAG, "Adding call outgoing ${userAgent.uap}/$uri")

                            } else {
                                Log.e(TAG, "ua_connect ${userAgent.uap}/$uri failed")
                                call.remove()
                              call.destroy()
                            }
                        }
                    }
                }
            } catch (e: Error) {
                Log.e(TAG, "Make Call failed: $e")
            }
        }

        fun answerCall() {
            Handler(Looper.getMainLooper()).postDelayed({

                val userAgent = UserAgent.uas()[0]
                val incomingCalls = Call.uaCalls(userAgent, "in")

                if (incomingCalls.isNotEmpty()) {
                    val callp = incomingCalls[0].callp
                    Api.ua_answer(userAgent.uap, callp, Api.VIDMODE_OFF)
                } else {
                    Log.w(TAG, "[answerCall] No incoming call to answer")
                }

            }, TimeUnit.SECONDS.toMillis(1))
            Log.d(TAG, "[answerCall] OUSIDE")
        }

        fun endCall() {
            val ua = UserAgent.uas()[0]
            val aor = ua.account.aor
            val uaCalls = Call.uaCalls(ua, "")
            if (uaCalls.size > 0) {
                val callp = uaCalls[uaCalls.size - 1].callp
                Log.d(TAG, "AoR $aor hanging up call $callp")
                Api.ua_hangup(ua.uap, callp, 0, "")
            }
        }


        private fun findContactURI(name: String): String = name

        fun sendMessage(senderId: String, serverURL: String, message: String) {

            Handler(Looper.getMainLooper()).postDelayed({

                if (message.isNotEmpty() && !message.startsWith("+")) {

                    val time = System.currentTimeMillis()
                    val peerUri = getPeerURI(senderId, serverURL)
                    val userAgent = UserAgent.uas()[0]

                    Log.d(TAG, "sendMessage called with $peerUri - $message")

                    if (Api.message_send(userAgent.uap, peerUri, message, time.toString()) == 0) {
                        Log.d(TAG, "Message send successfully")
                    } else {
                        Log.d(TAG, "Message send failed")
                    }

                }

            }, TimeUnit.SECONDS.toMillis(1))
        }

        private fun getPeerURI(senderId: String, serverURL: String): String {
            return "sip:$senderId@$serverURL"
        }

        fun aecAgcCheck() {

            val sessionId = Api.AAudio_open_stream()
            if (sessionId == -1) {
                Log.e(TAG, "Open AAudio stream failure")
                return
            } else {
                Log.i(TAG, "Open AAudio stream success")
            }

            if (AcousticEchoCanceler.isAvailable()) {
                val aec = AcousticEchoCanceler.create(sessionId)
                if (aec != null) {
                    SipService.aecAvailable = true
                    //aec.release() might be the culprit in echo issue
                    aec.enabled = true
                    Log.d(TAG, "Creation of hardware AEC for $sessionId succeeded")
                } else {
                    Log.w(TAG, "Creation of hardware AEC for $sessionId failed")
                }
            }
            else
                Log.i(TAG, "Hardware AEC is NOT available")

            if (AutomaticGainControl.isAvailable()) {
                val agc = AcousticEchoCanceler.create(sessionId)
                if (agc != null) {
                    SipService.agcAvailable = true
                    agc.release()
                    Log.d(TAG, "Creation of hardware AGC for $sessionId succeeded")
                } else {
                    Log.w(TAG, "Creation of hardware AGC for $sessionId failed")
                }
            }
            else
                Log.i(TAG, "Hardware AGC is NOT available")

            Api.AAudio_close_stream()
        }
    }
}