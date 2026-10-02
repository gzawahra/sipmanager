package com.magneta.emeritsipmanager

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioManager.MODE_IN_COMMUNICATION
import android.media.AudioManager.MODE_NORMAL
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Build.VERSION
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.system.OsConstants
import android.telephony.TelephonyManager
import androidx.annotation.Keep
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.lifecycle.MutableLiveData
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import com.magneta.emeritsipmanager.Utils.normalizeAor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.InetAddress
import java.nio.charset.StandardCharsets
import java.util.Timer
import java.util.TimerTask

class SipService : Service() {

    internal lateinit var intent: Intent
    private lateinit var audioManager: AudioManager
    internal lateinit var rt: Ringtone
    private lateinit var nt: Ringtone
    private lateinit var nm: NotificationManager
    private lateinit var cm: ConnectivityManager
    private lateinit var pm: PowerManager
    private lateinit var wm: WifiManager
    private lateinit var tm: TelephonyManager
    private lateinit var btm: BluetoothManager
    private lateinit var partialWakeLock: PowerManager.WakeLock
    private lateinit var proximityWakeLock: PowerManager.WakeLock
    private lateinit var wifiLock: WifiManager.WifiLock
    private lateinit var vibratorManager: VibratorManager
    private lateinit var vibrator: Vibrator
    private var vbTimer: Timer? = null
    private var origVolume = mutableMapOf<Int, Int>()
    private var linkAddresses = mutableMapOf<String, String>()
    private var allNetworks = mutableSetOf<Network>()
    private var activeNetwork: Network? = null
    private var hotSpotAddresses = mapOf<String, String>()
    private var hotSpotIsEnabled = false
    private var mediaPlayer: MediaPlayer? = null
    private var isUpdatingNetwork = false
    private var lastTransport: Int? = null
    private var activeNetworkId: Int? = null
    private var isSipRegistered = false
    private var lastLostSipNetwork = "0"
    private var preferredSipNetwork = "0"
    private var secondarySipNetworkLossRestart = false
    private var forcedSipNetworkUpdate = false
    private var lastSipNetwork: Network? = null
    private var currentSipTransport: Int = -1
    private var currentLinkAddresses = mutableMapOf<String, String>()
    private fun listAllFilesRecursively(dir: File) {
        if (dir.exists() && dir.isDirectory) {
            dir.listFiles()?.forEach { file ->
                Log.d("FileExplorer", file.absolutePath)
                if (file.isDirectory) {
                    listAllFilesRecursively(file)
                }
            }
        } else {
            Log.d("FileExplorer", "${dir.absolutePath} does not exist or is not a directory.")
        }
    }

    @SuppressLint("WakelockTimeout")
    override fun onCreate() {

        Log.d(TAG, "SipService onCreate")

        nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        createNotificationChannels()
        showStatusNotification()
        Api.log_level_set(level = 0)

        intent = Intent("com.magneta.emeritsipmanager.EVENT")
        intent.setPackage("com.magneta.emeritsipmanager")

        filesPath = filesDir.absolutePath

        vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibrator = vibratorManager.defaultVibrator
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val rtUri = RingtoneManager.getActualDefaultRingtoneUri(
            applicationContext,
            RingtoneManager.TYPE_RINGTONE
        )
        rt = RingtoneManager.getRingtone(applicationContext, rtUri)

        val ntUri = RingtoneManager.getActualDefaultRingtoneUri(
            applicationContext,
            RingtoneManager.TYPE_NOTIFICATION
        )
        nt = RingtoneManager.getRingtone(applicationContext, ntUri)


        pm = getSystemService(POWER_SERVICE) as PowerManager

        // This is needed to keep service running also in Doze Mode
        partialWakeLock = pm.run {
            newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "com.magneta.emeritsipmanager:partial_wakelog"
            ).apply {
                acquire()
            }
        }

        cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val builder = NetworkRequest.Builder()
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
        cm.registerNetworkCallback(
            builder.build(),
            object : ConnectivityManager.NetworkCallback() {

                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    Log.i(TAG, "Network $network is available")
                    updateNetwork()
                }

                override fun onLost(network: Network) {
                    Log.i(TAG, "Network $network is lost")
                    secondarySipNetworkLossRestart = false
                    lastLostSipNetwork = network.toString()
                    if(lastLostSipNetwork == preferredSipNetwork){
                        isSipRegistered = false
                        updateNetwork()
                        EmeritCommunicationManager.broadcastSIPEvents(
                            this@SipService,
                            "network lost",
                            ""
                        )
                    } else{
                        Log.i(TAG, " Secondary Network $network is lost")
                        secondarySipNetworkLossRestart = true
                    }

                }

                override fun onLinkPropertiesChanged(network: Network, props: LinkProperties) {
                    super.onLinkPropertiesChanged(network, props)
                    Log.i(TAG, "Network $network link properties changed")
                    if (activeNetwork == network)
                        updateNetwork()
                }

                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    super.onCapabilitiesChanged(network, caps)
                    Log.i(TAG, "Network $network capabilities changed: $caps")
                }
            }

        )

        wm = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager

        tm = getSystemService(TELEPHONY_SERVICE) as TelephonyManager

        proximityWakeLock = pm.newWakeLock(
            PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
            "com.magneta.emeritsipmanager:proximity_wakelog"
        )

        wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "Baresip")
        wifiLock.setReferenceCounted(false)
        startRegistrationCheck()
        super.onCreate()
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        //disableHardwareAEC()// <-- Place this before baresip starts

        val action: String

        if (intent == null) {
            action = "Start"
            Log.d(TAG, "Received onStartCommand with null intent")
        } else {
            action = intent.action ?: "Start Activity"
            Log.d(TAG, "Received onStartCommand action $action")
        }

        when (action) {
            "Start" -> {


                updateDnsServers()

                var file = File(filesPath)
                if (!file.exists()) {
                    Log.d(TAG, "Creating sip directory")
                    try {
                        File(filesPath).mkdirs()
                    } catch (e: Error) {
                        Log.e(TAG, "Failed to create directory: $e")
                    }
                }

                val configFile = File("$filesPath/config")
                Log.d(TAG, "Replacing asset '$configFile'")
                Utils.copyAssetToFile(applicationContext, "config", "$filesPath/config")

                Config.initialize(applicationContext)

                hotSpotAddresses = Utils.hotSpotAddresses()
//                linkAddresses = currentLinkAddresses
                var addresses = ""
                for (la in linkAddresses)
                    addresses = "$addresses;${la.key};${la.value}"
                Log.i(TAG, "Link addresses: $addresses")
                activeNetwork = cm.activeNetwork
                val userAgent = Config.variable("user_agent")

                Thread {
                    baresipStart(
                        path = filesPath,
                        addresses = addresses.removePrefix(";"),
                        logLevel = logLevel,
                        software = if (userAgent != "")
                            userAgent
                        else
                            "e-WG200" +
                                    "(Android ${VERSION.RELEASE}/${System.getProperty("os.arch") ?: "?"})"
                    )
                }.start()
                isServiceRunning = true

            }
            "restart" ->{
               restartsip()
            }
            "Call Reject" -> {
                val callp = intent!!.getLongExtra("callp", 0L)
                val call = Call.ofCallp(callp)
                if (call == null) {
                    Log.w(TAG, "onStartCommand did not find call $callp")
                } else {
                    val peerUri = call.peerUri
                    val aor = call.ua.account.aor
                    Log.d(TAG, "Aor $aor rejected incoming call $callp from $peerUri")
                    Api.ua_hangup(call.ua.uap, callp, 486, "Rejected")
                }
            }
            "Transfer Deny" -> {
                val callp = intent!!.getLongExtra("callp", 0L)
                val call = Call.ofCallp(callp)
                if (call == null)
                    Log.w(TAG, "onStartCommand did not find call $callp")
                else
                    call.notifySipfrag(603, "Decline")
                nm.cancel(TRANSFER_NOTIFICATION_ID)
            }
            "Message Save" -> {
                val uap = intent!!.getLongExtra("uap", 0L)
                val ua = UserAgent.ofUap(uap)
                if (ua == null)
                    Log.w(TAG, "onStartCommand did not find UA $uap")
                nm.cancel(MESSAGE_NOTIFICATION_ID)
            }
            "Message Delete" -> {
                val uap = intent!!.getLongExtra("uap", 0L)
                val ua = UserAgent.ofUap(uap)
                if (ua == null)
                    Log.w(TAG, "onStartCommand did not find UA $uap")

                nm.cancel(MESSAGE_NOTIFICATION_ID)
            }
            "Stop", "Stop Force" -> {
                if (!isServiceClean) cleanService()
                if (isServiceRunning) baresipStop(action == "Stop Force")
            }
            "Kill" -> {
                if (!isServiceClean) cleanService()
                if (isServiceRunning) baresipStop(action == "Stop Force")
                isServiceRunning = false
                stopForeground(true)
                stopSelf()
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRegistrationCheck()
        Log.d(TAG, "At Baresip Service onDestroy")
        cleanService()
        if (isServiceRunning)
            sendBroadcast(Intent("com.magneta.emeritsipmanager.Restart"))
    }

    @SuppressLint("UnspecifiedImmutableFlag", "DiscouragedApi")
    @Keep
    fun uaEvent(event: String, uap: Long, callp: Long) {

        if (!isServiceRunning) return

        val ev = event.split(",")


        val ua = UserAgent.ofUap(uap)
        if (ua == null) {
            Log.w(TAG, "uaEvent $event did not find ua $uap")
            return
        }

        val aor = ua.account.aor

        Log.d(TAG, "got uaEvent $event/$aor/$callp")

        val call = Call.ofCallp(callp)
        if (call == null && callp != 0L && !setOf("incoming call", "call outgoing", "call closed").contains(ev[0])) {
            Log.w(TAG, "uaEvent $event did not find call $callp")
            return
        }

        val shouldBroadcast = when (ev[0]) {
            "call closed" -> {
                val otherCallsExist = Call.calls().any { it.callp != callp }
                !otherCallsExist
            }
            else ->true
        }

        if (shouldBroadcast) {
            broadcastSipEvent(ev[0], "")
        } else {
            Log.d(TAG, "Skipping broadcast for '$ev[0]' because another call is active")
        }

        val toTest = "incoming call"

        for (accountIndex in uas.indices) {
            Log.d(TAG, "DEBUG[accountIndex=${accountIndex}] ev[0] raw: '${ev[0]}' (length=${ev[0].length}) incoming call=${ev[0]==toTest}")
            val uaAor = uas[accountIndex].account.aor
            Log.d(TAG, "DEBUG AoR comparison (raw): uaAor='$uaAor' (length=${uaAor.length}), aor='$aor' (length=${aor.length})")
            Log.d(TAG, "DEBUG AoR comparison (==): ${uaAor == aor}")
            Log.d(TAG, "DEBUG AoR comparison (normalized): ${normalizeAor(uaAor)} == ${normalizeAor(aor)}")

            val index = uas.indexOfFirst { it.account.aor == aor }
            if (index != -1) {
                Log.d(TAG, "Compte SIP trouvé à l’index $index pour l’aor: $aor")
            } else {
                Log.d(TAG, "Compte SIP trouvé à l’index $index pour l’aor: $aor")
            }
            when (ev[0].trim().lowercase()) {
                "recorder sessionid" -> {
                    Log.d(TAG, "recorder sessionid")
                    launchCallActivity()
                    recorderSessionId = ev[1].toInt()
                    Log.d(TAG, "got recorder sessionid $recorderSessionId")
                    if (recorderSessionId != 0) {
                        if (aecAvailable) {
                            aec = AcousticEchoCanceler.create(recorderSessionId)
                            if (aec != null) {
                                if (!aec!!.enabled) {
                                    aec!!.setEnabled(true)
                                    if (aec!!.enabled)
                                        Log.d(TAG, "AEC is enabled")
                                    else
                                        Log.w(TAG, "Failed to enable AEC")
                                }
                                else
                                    Log.d(TAG, "AEC is already enabled")
                            } else
                                Log.w(TAG, "Failed to create AEC for session $recorderSessionId")
                        }
                        if (agcAvailable) {
                            agc = AutomaticGainControl.create(recorderSessionId)
                            if (agc != null) {
                                if (!agc!!.enabled) {
                                    agc!!.setEnabled(true)
                                    if (agc!!.enabled)
                                        Log.d(TAG, "AGC is enabled")
                                }
                            } else
                                Log.w(TAG, "Failed to create AGC")
                        }
                        if (nsAvailable) {
                            ns = NoiseSuppressor.create(recorderSessionId)
                            if (ns != null) {
                                if (!ns!!.enabled) {
                                    ns!!.setEnabled(true)
                                    if (ns!!.enabled)
                                        Log.d(TAG, "NS is enabled")
                                }
                            } else
                                Log.w(TAG, "Failed to create NS")
                        }
                        recorderSessionId = 0
                    }
                    // add record here

                    return
                }
                "incoming call" -> {
                    val peerUri = ev[1]
                    val callp = ev[2].toLongOrNull()

                    if (callp == null || callp == 0L) {
                        Log.e(TAG, "Invalid or missing call pointer for ua_answer")
                        return
                    }
                    // Block if outgoing call exists
                    if (Call.calls().any { it.dir == "out" }) {
                        Api.ua_hangup(ua.uap, callp, 486, "Rejected")
                        return
                    }
                    Log.d(TAG, "Incoming call $uap/$callp/$peerUri")

                    val call = Call(callp, ua, peerUri, "in", "incoming")
                    call.callp = callp
                    call.add()

                    if (ua.account.answerMode == Api.ANSWERMODE_MANUAL) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            launchCallActivity()

                            Log.d(TAG, "CurrentInterruptionFilter ${nm.currentInterruptionFilter}")
                            if (nm.currentInterruptionFilter <= NotificationManager.INTERRUPTION_FILTER_ALL) {
                                startRinging()
                            }
                        }, 1000)
                    } else {
                        // Optional: auto-answer fallback
                        Handler(Looper.getMainLooper()).postDelayed({
                            Api.ua_answer(uap, callp, Api.VIDMODE_OFF)
                        }, 1000)
                    }
                }

                "registering" -> {
                    Log.d(TAG, "registering")
                    return
                }
                "unregistering" -> {
                    isSipRegistered = false
                    if(secondarySipNetworkLossRestart){
                        Log.d(TAG, "restarting updatenetwork in 5s due to secondary network loss restart")
                        forcedSipNetworkUpdate = true
                        CoroutineScope(Dispatchers.Main).launch {
                            delay(5000)
                            Log.d(TAG, "restarting updatenetwork due to secondary network loss restart")
                        updateNetwork()}
                        secondarySipNetworkLossRestart = false;
                    }
                    Log.d(TAG, "unregistering")
                    if (!Utils.isVisible())
                        return
                }
                "registered" -> {
                    Log.d(TAG, "registered setting var")
                    isSipRegistered = true
                    ua.uaUpdateStatus(
                        if (Api.account_regint(ua.account.accp) == 0)
                            R.drawable.dot_white
                        else
                            R.drawable.dot_green
                    )
                    if (!Utils.isVisible())
                        return
                }
                "registering failed" -> {
                    isSipRegistered = false
                    Log.d(TAG, "registering failed")
                    ua.uaUpdateStatus(if (Api.account_regint(ua.account.accp) == 0)
                        R.drawable.dot_white
                    else
                        R.drawable.dot_red)
                    if (!Utils.isVisible())
                        return
                    return
                }
                "call outgoing" -> {
                    Log.d(TAG, "call outgoing")
                    setCallVolume()
                    proximitySensing(true)
                    launchCallActivity()
                    return
                }
                "call progress" -> {
                    Log.d(TAG, "call progress")
                    if ((ev[1].toInt() and Api.SDP_RECVONLY) != 0)
                        stopMediaPlayer()
                    else
                        playRingBack()
                    return
                }
                "call ringing" -> {
                    Log.d(TAG, "call ringing")
                    playRingBack()
                    return
                }
                "call incoming" -> {
                    Log.d(TAG, "Call incoming BRO peerUri ${Call.calls().size} - ${tm.callState} ")
                    return
                }
                "call answered" -> {
                    Log.d(TAG, "Call answered")
                    stopRinging()
                    stopMediaPlayer()
                    audioManager.mode = MODE_IN_COMMUNICATION
                    requestAudioFocus(ctx = applicationContext)
                    setCallVolume()
                    proximitySensing(true)
                    return
                }
                "call established" -> {
                    Log.d(TAG, "Call established")
                    nm.cancel(CALL_NOTIFICATION_ID)
                    val call = Call.ofCallp(callp)
                    if (call == null) {
                        Log.w(TAG, "Call $callp that is established is not found")
                        return
                    }
                    Log.d(TAG, "AoR $aor call $callp established in mode ${audioManager.mode}")
                    audioManager.mode = MODE_IN_COMMUNICATION

                    call.status = "connected"


                    Api.calls_mute(false)
                    Api.call_start_audio(callp)

                    launchCallActivity()
                    if (!Utils.isVisible())
                        return
                }
                "call update" -> {
                    Log.d(TAG, "Call update")
                    if (call!!.state() == Api.CALL_STATE_EARLY) {
                        if ((ev[1].toInt() and Api.SDP_RECVONLY) != 0)
                            stopMediaPlayer()
                        else
                            playRingBack()
                    }
                    if (!Utils.isVisible())
                        return
                }
                "call verified", "call secure" -> {
                    Log.d(TAG, "Call verified, call secure")
                    val call = Call.ofCallp(callp)
                    if (call == null) {
                        Log.w("Baresip", "Call $callp that is verified is not found")
                        return
                    }
                    if (ev[0] == "call secure") {
                        call.security = R.drawable.box_yellow
                    } else {
                        call.security = R.drawable.box_green
                        call.zid = ev[1]
                    }
                    if (!Utils.isVisible())
                        return
                }
                "call transfer" -> {
                    Log.d(TAG, "Call transfer")
                    val call = Call.ofCallp(callp)
                    if (call == null) {
                        Log.w(TAG, "Call $callp to be transferred is not found")
                        return
                    }
                }
                "transfer failed" -> {
                    Log.d(TAG, "transfer failed")
                    Log.d(TAG, "AoR $aor call $callp transfer failed: ${ev[1]}")
                    return
                }
                "call closed" -> {
                    Log.d(TAG, "transfer closed")
                    nm.cancel(CALL_NOTIFICATION_ID)
                    if (call == null) {
                        Log.d(TAG, "AoR $aor call $callp that is closed is not found")
                        return
                    }
                    stopRinging()
                    stopMediaPlayer()
                    aec?.release()
                    aec = null
                    agc?.release()
                    agc = null
                    ns?.release()
                    ns = null
                    call.remove()
                    if (!Call.inCall()) {
                        resetCallVolume()
                        audioManager.isSpeakerphoneOn = false
                        abandonAudioFocus(applicationContext)
                        audioManager.mode = MODE_NORMAL
                        proximitySensing(enable = false)
                    }
                    launchCallActivity()
                }
                "refer failed" -> {
                    Log.d(TAG, "refer failed")
                    Log.d(TAG, "AoR $aor hanging up call $callp with ${ev[1]}")
                    Api.ua_hangup(uap, callp, 0, "")
                    val call = Call.ofCallp(callp)
                    if (call == null) {
                        Log.w(TAG, "Call $callp with failed refer is not found")
                    } else {
                        call.referTo = ""
                    }
                    if (!Utils.isVisible()) return
                }
            }
        }

        postServiceEvent(ServiceEvent(event, arrayListOf(uap, callp), System.nanoTime()))
    }

    private fun broadcastSipEvent(event: String, extraMessage: String) {
        if (!event.equals(previousEvent, true)) {
            EmeritCommunicationManager.broadcastSIPEvents(this, event, extraMessage)
            previousEvent = event
        }
    }

    private fun launchCallActivity() {
        Log.d(TAG, "launchCallActivity 1")
        val intent = Intent(this, CallActivity::class.java)
        intent.putExtra("action", "Call Activity")
        intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        Log.d(TAG, "launchCallActivity 2")
    }

    private fun postServiceEvent(event: ServiceEvent) {
        serviceEvents.add(event)
        if (serviceEvents.size == 1) {
            Log.d(TAG, "Posted service event ${event.event} at ${event.timeStamp}")
            serviceEvent.postValue(Event(event.timeStamp))
        } else {
            Log.d(TAG, "Added service event ${event.event}")
        }
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    @Keep
    fun messageEvent(uap: Long, peer: String, msg: ByteArray) {
        var receivedMessage = "Decoding of message failed!"
        try {
            receivedMessage = String(msg, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            Log.w(TAG, "UTF-8 decode failed")
        }
        val ua = UserAgent.ofUap(uap)
        if (ua == null) {
            Log.w(TAG, "messageEvent did not find ua $uap")
            return
        }
        val timeStamp = System.currentTimeMillis().toString()
        Log.d(TAG, "Received text : $receivedMessage")
        Log.d(TAG, "Message event for $uap from $peer at $timeStamp")
        ua.account.unreadMessages = true

        broadcastSipEvent("message incoming", receivedMessage)

        val intent = Intent("service event")
        intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        intent.putExtra("event", "message show")
        intent.putExtra("params", arrayListOf(uap, peer))
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    @Keep
    @Suppress("UNUSED")
    fun messageResponse(responseCode: Int, responseReason: String, time: String) {
        Log.d(TAG, "Message response '$responseCode $responseReason' at $time")
        val intent = Intent("message response")
        intent.putExtra("response code", responseCode)
        intent.putExtra("response reason", responseReason)
        intent.putExtra("time", time)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    @Keep
    fun getPassword(aor: String): String {
        if (!isServiceRunning) return ""
        Log.d(TAG, "getPassword of $aor")
        /*return if (MainActivity.aorPasswords[aor] != null)
            MainActivity.aorPasswords[aor]!!
        else
            ""*/

        SharedPrefManager.getSipAccount(this)?.let {
            return it.sipUserPassword
        }

        return ""
    }

    @Keep
    fun started() {
        Log.d(TAG, "Received 'started' from baresip")
        val intent = Intent("service event")
        intent.putExtra("event", "started")
        intent.putExtra("params", arrayListOf(callActionUri))
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        callActionUri = ""
        Log.d(
            TAG, "Battery optimizations are ignored: " +
                    "${pm.isIgnoringBatteryOptimizations(applicationContext.packageName)}"
        )
        Log.d(
            TAG, "Partial wake lock/wifi lock is held: " +
                    "${partialWakeLock.isHeld}/${wifiLock.isHeld}"
        )

        SharedPrefManager.getSipAccount(this)?.let {
            SipUtils.registerAccount(this,it)
        }
    }
    @Keep
    fun stopped(error: String) {
        Log.d(TAG, "Received 'stopped' from baresip with param '$error'")
        isServiceRunning = false
        val intent = Intent("service event")
        intent.putExtra("event", "stopped")
        intent.putExtra("params", arrayListOf(error))
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
//        stopForeground(true)
//        stopSelf()
    }

    private fun createNotificationChannels() {
        val defaultChannel = NotificationChannel(
            DEFAULT_CHANNEL_ID, "Default",
            NotificationManager.IMPORTANCE_LOW
        )
        defaultChannel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        nm.createNotificationChannel(defaultChannel)
        val highChannel = NotificationChannel(
            HIGH_CHANNEL_ID, "High",
            NotificationManager.IMPORTANCE_HIGH
        )
        highChannel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        highChannel.enableVibration(true)
        nm.createNotificationChannel(highChannel)
    }

    private fun showStatusNotification() {
        // Ensure the notification channel exists
        val channel = NotificationChannel(
            DEFAULT_CHANNEL_ID,
            "SIP Service",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)

        val snb = NotificationCompat.Builder(this, DEFAULT_CHANNEL_ID)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("SIP Service")
            .setContentText("Running")
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        startForeground(
            STATUS_NOTIFICATION_ID,
            snb.build(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        )
    }


    private fun startRinging() {
        Log.i(TAG, "startRinging: isPostAlerRunning $isPostAlertRunning")
        if (!isPostAlertRunning) {
            rt.isLooping = true
            rt.play()
            vbTimer = Timer()
            vbTimer!!.schedule(object : TimerTask() {
                override fun run() {
                    vibrator.vibrate(
                        VibrationEffect.createOneShot(
                            1000,
                            VibrationEffect.DEFAULT_AMPLITUDE
                        )
                    )
                }
            }, 1000L, 1000L)
        }
    }

    @SuppressLint("DiscouragedApi")
    private fun playRingBack() {
        Log.e(TAG, "Start Ringback")
        if (mediaPlayer == null) {
            val name = "ringback"
            val resourceId = applicationContext.resources.getIdentifier(
                name,
                "raw",
                applicationContext.packageName)
            if (resourceId != 0) {
                mediaPlayer = MediaPlayer.create(this, resourceId)
                mediaPlayer?.isLooping = true
                mediaPlayer?.start()
            } else {
                Log.e(TAG, "Ringback tone $name.wav not found")
            }
        }
    }

    private fun stopRinging() {
        Log.d(TAG, "stopRinging() called - mode=${audioManager.mode}")
            abandonAudioFocus(applicationContext)
            if (vbTimer != null) {
                vbTimer!!.cancel()
                vbTimer = null
            }
        if (rt.isPlaying) {
            rt.stop()
        }
    }

    private fun stopMediaPlayer() {
        Log.d(TAG, "stopRingback")
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun setCallVolume() {
        audioManager.mode = MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = true

        val streamTypes = listOf(AudioManager.STREAM_VOICE_CALL, AudioManager.STREAM_MUSIC)
        for (streamType in streamTypes) {
            origVolume[streamType] = audioManager.getStreamVolume(streamType)
            val maxVolume = audioManager.getStreamMaxVolume(streamType)
            audioManager.setStreamVolume(streamType, maxVolume, 0)
            Log.d(TAG, "Orig/new/max volume for stream $streamType = " +
                    "${origVolume[streamType]}/${audioManager.getStreamVolume(streamType)}/$maxVolume")
        }
    }

    private fun resetCallVolume() {
        for ((streamType, streamVolume) in origVolume) {
            audioManager.setStreamVolume(streamType, streamVolume, 0)
            Log.d(TAG, "Reset $streamType volume to ${audioManager.getStreamVolume(streamType)}")
        }

        isSpeakerPhoneEnable = true
        isShowCallActivity = true
        isPostAlertRunning = false
    }

    @SuppressLint("WakelockTimeout")
    private fun proximitySensing(enable: Boolean) {
        if (enable) {
            if (!proximityWakeLock.isHeld) {
                Log.d(TAG, "Acquiring proximity wake lock")
                proximityWakeLock.acquire()
            } else {
                Log.d(TAG, "Proximity wake lock already acquired")
            }
        } else {
            if (proximityWakeLock.isHeld) {
                proximityWakeLock.release()
                Log.d(TAG, "Released proximity wake lock")
            } else {
                Log.d(TAG, "Proximity wake lock is not held")
            }
        }
    }
    fun isInternetAvailable(context: Context?): Boolean {
        if (context == null) return false
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun getDnsServersFromNetwork(network: Network): List<InetAddress> {
        val linkProps = cm.getLinkProperties(network) ?: return emptyList()
        return linkProps.dnsServers
    }
    private fun transportName(t: Int): String {
        return when (t) {
            NetworkCapabilities.TRANSPORT_WIFI -> "WIFI"
            NetworkCapabilities.TRANSPORT_CELLULAR -> "CELLULAR"
            else -> "UNKNOWN"
        }
    }
    private suspend fun ping1dot1dot1dot1(timeoutMs: Int = 1500): Boolean {
        return try {
            val proc = Runtime.getRuntime().exec(arrayOf("ping", "-c", "1", "-W", (timeoutMs / 1000).toString(), "1.1.1.1"))
            val exit = withContext(Dispatchers.IO) { proc.waitFor() }
            exit == 0
        } catch (e: Exception) {
            Log.e(TAG, "Ping failed: ${e.message}")
            false
        }
    }

    private val checkRegistrationHandler = Handler(Looper.getMainLooper())
    private val checkRegistrationRunnable = object : Runnable {
        override fun run() {
            if(isUpdatingNetwork)
                return
            isUpdatingNetwork = true
            // Check if registered
            if (!isSipRegistered) {
                Log.d(TAG, "Not registered, running")
                if(!Call.inCall()){
                    Log.d(TAG, "inCall = false restarting SiP")
                    stopsip()
                    CoroutineScope(Dispatchers.Main).launch {
                        delay(3000)
                        val canPing = ping1dot1dot1dot1()
                        Log.i(TAG, "Pingss to 1.1.1.1: $canPing")
                        if (canPing) {
                            Log.i(TAG, "Internet is reachabless")
                            // Force DNS injection
                            startsip()
                            CoroutineScope(Dispatchers.Main).launch {
                                delay(3000)
                                try{
                                    val firstUa2 = UserAgent.uas().firstOrNull()
                                    if (firstUa2 != null && isServiceRunning && firstUa2.uap != 0L) {
                                        Log.i(TAG, "Registering after baresip startup (delayed)...")
                                        Api.ua_register(firstUa2.uap)
                                    } else {
                                        Log.e(TAG, " (firstUa2 != null && Api.account_regint(firstUa2.account.accp) == 0) is false")
                                    }
                                } catch (e: Error) {
                                    Log.i(TAG, "UserAgent.register() crashed")
                                }
                            }
                        } else {
                            Log.w(TAG, "Ping to 1.1.1.1 failed skipping")
                            isSipRegistered = false
                        }
                    }
                }

            }else {
                Log.d(TAG, "registered, skipping....")
            }
            // Repeat every minute
            isUpdatingNetwork = false
            checkRegistrationHandler.postDelayed(this, 80_000)
        }
    }

    // Start the check somewhere, e.g. in onCreate or after service is started
    private fun startRegistrationCheck() {
        Log.d(TAG, "started registration check loop")
        checkRegistrationHandler.postDelayed(checkRegistrationRunnable, 80_000)
    }

    // Stop the check when service is destroyed
    private fun stopRegistrationCheck() {
  //      Log.d(TAG, "stopped registration check loop")
//        checkRegistrationHandler.removeCallbacks(checkRegistrationRunnable)
    }

    private fun startsip() {
        if(!isServiceRunning){
            updateDnsServers()
            hotSpotAddresses = Utils.hotSpotAddresses()
            var addresses = ""
            for (la in linkAddresses)
                addresses = "$addresses;${la.key};${la.value}"
            Log.i(TAG, "Link addresses: $addresses")
            activeNetwork = cm.activeNetwork
            val userAgent = Config.variable("user_agent")

            Thread {
                baresipStart(
                    path = filesPath,
                    addresses = addresses.removePrefix(";"),
                    logLevel = logLevel,
                    software = if (userAgent != "")
                        userAgent
                    else
                        "e-WG200 " +
                                "(Android ${VERSION.RELEASE}/${System.getProperty("os.arch") ?: "?"})"
                )
            }.start()

            val firstUa = UserAgent.uas().firstOrNull()
            if (firstUa != null && Api.account_regint(firstUa.account.accp) == 0) {
                Api.account_set_regint(firstUa.account.accp, 0)
            }
            isServiceRunning = true
        }

    }

    private fun stopsip(force: Boolean = false) {
        if(isServiceRunning){
            Log.i(TAG, "stopsip() called, isServiceRunning=$isServiceRunning, force=$force")
            try {
                val ua = UserAgent.uas()[0]
                val uaCalls = Call.uaCalls(ua, "")
                if (uaCalls.size > 0) {
                    val callp = uaCalls[uaCalls.size - 1].callp
                    Log.d(TAG, "CallActivity hanging up call ")
                    Api.ua_hangup(ua.uap, callp, 0, "")
                    CallActivity.sendHangupBroadcast(applicationContext)
                }
                // 1. Destroy all UAs
                for (ua in uas.toList()) {
                    try {
                        val uaPtr = ua.uap
                        if (ua.uap != 0L) {
                            Log.d(TAG, "Destroying UserAgent: uaPtr=${ua.uap}")
                            Api.ua_destroy(ua.uap)
                            ua.uap = 0L // mark as destroyed, prevents double free
                        } else {
                            Log.d(TAG, "UserAgent already destroyed or invalid, skipping uaPtr=${ua.uap}")
                        }
                        Log.d(TAG, "Destroying 2 UserAgent: uaPtr=$uaPtr")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to destroy UA: $e")
                    }
                }
                uas.clear()
                uasStatus.clear()

                // 2. Clean up service state
                if (!isServiceClean) {
                    try {
                        cleanService()
                    } catch (e: Exception) {
                        Log.w(TAG, "Error in cleanService(): $e")
                    }
                }

                // 3. Stop baresip if running or forced
                if (isServiceRunning || force) {
                    try {
                        baresipStop(force)
                        Log.d(TAG, "baresipStop($force) called")
                    } catch (e: Exception) {
                        Log.e(TAG, "baresipStop threw: $e")
                    }
                }

                // 4. Clear network state
                currentLinkAddresses.clear()
                Log.d(TAG, "Cleared all currentLinkAddresses")
                isSipRegistered = false
                isServiceRunning = false
                Log.i(TAG, "stopsip() complete, isServiceRunning now $isServiceRunning")
            } catch (e: Exception) {
                Log.e(TAG, "Exception in stopsip: $e")
            }
        }

}
    private fun restartsip(){
        stopsip()
        startsip()
    }

    @SuppressLint("SuspiciousIndentation")
    private fun updateNetwork() {
        if (isUpdatingNetwork) {
            Log.d(TAG, "updateNetwork() already running; skipping")
            return
        }
        isUpdatingNetwork = true
        Log.i(TAG, "UPDATE NETWORK")

        allNetworks = cm.allNetworks.toMutableSet()

        // Print network types for debug
        for (n in allNetworks) {
            val caps = cm.getNetworkCapabilities(n)
            val type = when {
                caps == null -> "UNKNOWN"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
                else -> "OTHER"
            }
            Log.i(TAG, "Network $n type: $type")
        }

        // Pick preferred network: Wi-Fi first, otherwise 4G
        val preferredNetwork = allNetworks.firstOrNull { n ->
            cm.getNetworkCapabilities(n)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        } ?: allNetworks.firstOrNull { n ->
            cm.getNetworkCapabilities(n)?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        }

        val caps = preferredNetwork?.let { cm.getNetworkCapabilities(it) }
        val transport = when {
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> NetworkCapabilities.TRANSPORT_WIFI
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> NetworkCapabilities.TRANSPORT_CELLULAR
            else -> -1
        }

        Log.i(TAG, "Preferred network: $preferredNetwork with transport: ${transportName(transport)}")

        val transportChanged = transport != lastTransport
        val networkChanged = preferredNetwork?.hashCode() != activeNetworkId
        preferredSipNetwork = preferredNetwork.toString()
        lastTransport = transport
        activeNetworkId = preferredNetwork?.hashCode()

        // Filter for IPv4 only!
        val addresses = linkAddresses.filterKeys { it.contains(".") }
        val ipChanged = addresses.keys != currentLinkAddresses.keys

        Log.d(TAG, "Old/new link addresses $currentLinkAddresses/$addresses")

        if (ipChanged) {
            for ((k, v) in addresses) {
                if (!currentLinkAddresses.containsKey(k)) {
                    if (Api.net_add_address_ifname(k, v) != 0)
                        Log.e(TAG, "Failed to add address: $k = $v")

                }
            }
            for ((k, _) in currentLinkAddresses) {
                if (!addresses.containsKey(k)) {
                    if (Api.net_rm_address(k) != 0)
                        Log.e(TAG, "Failed to remove address: $k")
                }
            }
            currentLinkAddresses = addresses.toMutableMap()
        }

        Log.d(TAG, "Added/Removed/TransportChanged/NetworkChanged/forcedSipNetworkUpdate = $ipChanged/$transportChanged/$networkChanged/$forcedSipNetworkUpdate")

        if (ipChanged || transportChanged || networkChanged || forcedSipNetworkUpdate) {
            forcedSipNetworkUpdate = false
//            cm.bindProcessToNetwork(null)
//            preferredNetwork?.let { cm.bindProcessToNetwork(it)}

            if(transportChanged && !Call.inCall())
                stopsip()
            Api.uag_reset_transp(register = true, reinvite = true)
            CoroutineScope(Dispatchers.Main).launch {
                delay(3000)
                val canPing = ping1dot1dot1dot1()
                Log.i(TAG, "Ping to 1.1.1.1: $canPing")
                if (canPing) {
                    Log.i(TAG, "Internet is reachable")
                    startsip()
                    CoroutineScope(Dispatchers.Main).launch {
                        delay(5000)
                        try{
                            val firstUa2 = UserAgent.uas().firstOrNull()
                            if (firstUa2 != null && isServiceRunning && firstUa2.uap != 0L) {
                                Log.i(TAG, "Registering after baresip startup (delayed)...")
                                Api.ua_register(firstUa2.uap)
                            } else {
                                Log.e(TAG, " (firstUa2 != null && Api.account_regint(firstUa2.account.accp) == 0) is false")
                            }
                        } catch (e: Error) {
                            Log.i(TAG, "UserAgent.register() crashed")
                        }
                    }
                } else {
                    Log.w(TAG, "Ping to 1.1.1.1 failed, stopping baresip")
                    stopsip()
                }
            }
        }
else {
            Log.d(TAG, "No significant change; skipping registration")
        }
        // Wi-Fi lock logic
        if (transport == NetworkCapabilities.TRANSPORT_WIFI) {
            Log.d(TAG, "Acquiring WiFi Lock")
            wifiLock.acquire()
        } else {
            Log.d(TAG, "Releasing WiFi Lock")
            wifiLock.release()
        }
        isUpdatingNetwork = false
    }

    private fun updateDnsServers() {
        Log.d(TAG, "Updating DNS servers $isServiceRunning - ${!dynDns}")
        if (isServiceRunning && !dynDns)
            return
        val servers = mutableListOf<InetAddress>()
        // Use DNS servers first from active network (if available)
        val activeNetwork = cm.activeNetwork
        if (activeNetwork != null) {
            val linkProps = cm.getLinkProperties(activeNetwork)
            if (linkProps != null)
                servers.addAll(linkProps.dnsServers)
        }
        // Then add DNS servers from the other networks
        for (n in allNetworks) {
            if (n == cm.activeNetwork) continue
            val linkProps = cm.getLinkProperties(n)
            if (linkProps != null)
                for (server in linkProps.dnsServers)
                    if (!servers.contains(server)) servers.add(server)
        }
        // Update if change
        if (servers != dnsServers) {
            if (isServiceRunning && Config.updateDnsServers(servers) != 0) {
                Log.w(TAG, "Failed to update DNS servers '${servers}'")
            } else {
                Log.d(TAG, "Updated DNS servers: '${servers}'")
                dnsServers = servers
            }
        }
    }

    private fun cleanService() {
        if (!isServiceClean) {
            audioManager.mode = MODE_NORMAL
            abandonAudioFocus(applicationContext)
            uas.clear()
            uasStatus.clear()
            messages = emptyList()
            if (this::nm.isInitialized)
                nm.cancelAll()
            if (this::partialWakeLock.isInitialized && partialWakeLock.isHeld)
                partialWakeLock.release()
            if (this::proximityWakeLock.isInitialized && proximityWakeLock.isHeld)
                proximityWakeLock.release()
            if (this::wifiLock.isInitialized)
                wifiLock.release()
            isServiceClean = true
        }
    }

    private external fun baresipStart(
        path: String,
        addresses: String,
        logLevel: Int,
        software: String
    )

    external fun baresipStop(force: Boolean)

    companion object {
        var isServiceRunning = false
        var isConfigInitialized = false
        var libraryLoaded = false
        var isServiceClean = false
        var callVolume = 0
        var speakerPhone = false
        var audioDelay = if (VERSION.SDK_INT < 31) 1500L else 500L
        var dynDns = false
        var netInterface = ""
        var filesPath = ""
        var logLevel = 0
        var sipTrace = false
        var callActionUri = ""
        var isRecOn = false
        var isMicMuted = false
        var isSpeakerPhoneEnable = true
        var isShowCallActivity = true
        var isPostAlertRunning = false
        var previousEvent = ""

        val uas = ArrayList<UserAgent>()
        val uasStatus = ArrayList<Int>()
        val calls = ArrayList<Call>()
        val aorPasswords = mutableMapOf<String, String>()
        var messages by mutableStateOf(emptyList<Message>())
        val chatTexts: MutableMap<String, String> = mutableMapOf()
        val activities = mutableListOf<String>()
        var contactsMode = "baresip"
        var addressFamily = ""

        var aecAvailable = false
        var agcAvailable = false
        var toneCountry = "fr"
        private val nsAvailable = NoiseSuppressor.isAvailable()
        var dnsServers = listOf<InetAddress>()
        val serviceEvent = MutableLiveData<Event<Long>>()
        val serviceEvents = mutableListOf<ServiceEvent>()

        private var audioFocusRequest: AudioFocusRequestCompat? = null
        private var aec: AcousticEchoCanceler? = null
        private var agc: AutomaticGainControl? = null
        private var ns: NoiseSuppressor? = null
        private var recorderSessionId = 0

        fun requestAudioFocus(ctx: Context): Boolean  {
            Log.d(TAG, "Requesting audio focus")
            if (audioFocusRequest != null) {
                Log.d(TAG, "Already focused")
                return true
            }
            val am = ctx.getSystemService(AUDIO_SERVICE) as AudioManager
            val attributes = AudioAttributesCompat.Builder()
                .setUsage(AudioAttributesCompat.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributesCompat.CONTENT_TYPE_SPEECH)
                .build()
            audioFocusRequest = AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener { }
                .build()
            if (AudioManagerCompat.requestAudioFocus(am, audioFocusRequest!!) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                Log.d(TAG, "requestAudioFocus granted")
            } else {
                Log.w(TAG, "requestAudioFocus denied")
                audioFocusRequest = null
            }
            return audioFocusRequest != null
        }

        fun abandonAudioFocus(ctx: Context) {
            val audioManager = ctx.getSystemService(AUDIO_SERVICE) as AudioManager
            if (audioFocusRequest != null) {
                Log.d(TAG, "Abandoning audio focus")
                if (AudioManagerCompat.abandonAudioFocusRequest(audioManager, audioFocusRequest!!) ==
                    AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    audioFocusRequest = null
                } else {
                    Log.e(TAG, "Failed to abandon audio focus")
                }
            }
            audioManager.mode = MODE_NORMAL
        }
    }

    init {
        if (!libraryLoaded) {
            Log.d(TAG, "Loading baresip library")
            System.loadLibrary("baresip")
            libraryLoaded = true
        }
    }
}
