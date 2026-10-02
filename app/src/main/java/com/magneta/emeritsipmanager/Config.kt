package com.magneta.emeritsipmanager

import android.content.Context
import androidx.annotation.OptIn
import java.net.Inet4Address
import java.net.InetAddress
import java.nio.charset.StandardCharsets

object Config {

    private val configPath = SipService.filesPath + "/config"
    private var config = String(Utils.getFileContents(configPath)!!, StandardCharsets.ISO_8859_1)

    fun initialize(ctx: Context) {

        Log.d(TAG, "Config is '$config'")

        if (!config.contains(Regex("ausrc_format s16"))) {
            config = "${config}ausrc_format s16\nauplay_format s16\nauenc_format s16\naudec_format s16\nmodule webrtc_aec.so\n"
        }

        if (config.contains(Regex("#module_app[ ]+mwi.so"))) {
            config = config.replace(Regex("#module_app[ ]+mwi.so"),
                    "module_app mwi.so")
        }

        if (!config.contains("opus_application")) {
            config = "${config}opus_application voip\n"
        }

        if (!config.contains("opus_samplerate")) {
            config = "${config}opus_samplerate 16000\n"
            val accountsPath = SipService.filesPath + "/accounts"
            var accounts = String(Utils.getFileContents(accountsPath)!!, StandardCharsets.ISO_8859_1)
            accounts = accounts.replace("opus/48000/1", "opus/16000/1")
            Utils.putFileContents(accountsPath, accounts.toByteArray())
        }

        SipUtils.aecAgcCheck()

        if (!config.contains("opus_stereo")) {
            config = "${config}opus_stereo no\n"
        }

        if (!config.contains("opus_sprop_stereo")) {
            config = "${config}opus_sprop_stereo no\n"
        }

        if (!config.contains("webrtc_aec_extended_filter")) {
            config = "${config}webrtc_aec_extended_filter yes\n"
        }

        if (!config.contains("log_level")) {
            config = "${config}log_level 2\n"
            Log.logLevel = Log.LogLevel.DEBUG
            SipService.logLevel = 0
        } else {
            val ll = variable("log_level")[0].toInt()
            replaceVariable("log_level", "$ll")
            Log.logLevelSet(ll)
            SipService.logLevel = ll
        }

        if (config.contains("net_interface")) {
           SipService.netInterface = variable("net_interface")
        }

        if (!config.contains("call_volume")) {
            config = "${config}call_volume 0\n"
        } else {
            SipService.callVolume = variable("call_volume")[0].toInt()
        }

        if (!config.contains("dyn_dns")) {
            config = "${config}dyn_dns no\n"
        } else {
            if (config.contains(Regex("dyn_dns[ ]+yes"))) {
                removeVariable("dns_server")
                for (dnsServer in SipService.dnsServers)
                    config = if (Utils.checkIpV4(dnsServer.hostAddress))
                        "${config}dns_server ${dnsServer.hostAddress}:53\n"
                    else
                        "${config}dns_server [${dnsServer.hostAddress}]:53\n"
                SipService.dynDns = true
            }
        }
//        removeVariable("dns_server")
//        config += "dns_server 1.1.1.1:53\n"
//        config += "dns_server 8.8.8.8:53\n"
//        config = config.replace("dyn_dns yes", "dyn_dns no") // just in case
//        if (!config.contains("jitter_buffer_type")) {
//            config = "${config}jitter_buffer_type adaptive\n"
//            config = "${config}jitter_buffer_wish 6\n"
//        }

        removeLine("module ilbc.so")

        Utils.putFileContents(configPath, config.toByteArray())
        SipService.isConfigInitialized = true
        Log.i(TAG, "Initialized config to '$config'")
    }


    fun variable(name: String): String {
        for (line in config.split("\n")) {
            val nameValue = line.split(" ", limit = 2)
            if (nameValue.size == 2 && nameValue[0] == name)
                return nameValue[1].trim()
        }
        return ""
    }

    fun variables(name: String): ArrayList<String> {
        val result = ArrayList<String>()
        for (line in config.split("\n")) {
            val nameValue = line.split(" ", limit = 2)
            if (nameValue.size == 2 && nameValue[0] == name)
                result.add(nameValue[1].trim())
        }
        return result
    }

    fun addLine(line: String) {
        config += "$line\n"
    }

    fun removeLine(line: String) {
        config = Utils.removeLinesStartingWithString(config, line)
    }

    fun addModuleLine(line: String) {
        // Make sure it goes before first 'module_tmp'
        config = config.replace("module opensles.so", "$line\nmodule opensles.so")
    }

    fun removeVariable(variable: String) {
        config = Utils.removeLinesStartingWithString(config, "$variable ")
    }

    fun replaceVariable(variable: String, value: String) {
        removeVariable(variable)
        addLine("$variable $value")
    }

    fun reset(ctx: Context) {
        Utils.copyAssetToFile(ctx, "config", configPath)
    }

    fun save() {
        var result = ""
        for (line in config.split("\n"))
            if (line.isNotEmpty())
                result = result + line + '\n'
        config = result
        Utils.putFileContents(configPath, config.toByteArray())
        Log.d(TAG, "Saved new config '$result'")
        // Api.reload_config()
    }

    fun updateDnsServers(dnsServers: List<InetAddress>): Int {
        val formatted = dnsServers.mapNotNull {
            val addr = it.hostAddress ?: return@mapNotNull null
            if (it is Inet4Address) "$addr:53" else "[$addr]:53"
        }.joinToString(",")

        Log.d(TAG, "Injecting DNS servers: $formatted")
        return Api.net_use_nameserver(formatted)
    }

}