package com.magneta.emeritsipmanager

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Bitmap.createScaledBitmap
import android.media.AudioManager
import android.net.LinkAddress
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.text.Editable
import android.text.TextWatcher
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException
import java.security.SecureRandom
import java.util.Enumeration
import java.util.Random
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object Utils {

    fun getNameValue(string: String, name: String): ArrayList<String> {
        val lines = string.split("\n")
        val result = ArrayList<String>()
        for (line in lines) {
            if (line.startsWith(name))
                result.add((line.substring(name.length).trim()).split(" \t")[0])
        }
        return result
    }

    fun checkPermissions(ctx: Context, permissions: Array<String>) : Boolean {
        for (p in permissions) {
            if (ContextCompat.checkSelfPermission(ctx, p) != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Permission $p is denied")
                return false
            } else {
                Log.d(TAG, "Permission $p is granted")
            }
        }
        return true
    }

    fun removeLinesStartingWithString(lines: String, string: String): String {
        var result = ""
        for (line in lines.split("\n"))
            if (!line.startsWith(string) && (line.isNotEmpty())) result += line + "\n"
        return result
    }

    fun uriHostPart(uri: String): String {
        return if (uri.contains("@")) {
            uri.substringAfter("@")
                    .substringBefore(":")
                    .substringBefore(";")
                    .substringBefore(">")
        } else {
            val parts = uri.split(":")
            when (parts.size) {
                2 -> parts[1].substringBefore(";")
                        .substringBefore(">")
                3 -> parts[1]
                else -> ""
            }
        }
    }

    fun uriUserPart(uri: String): String {
        return if (uri.contains("@"))
            uri.substringAfter(":").substringBefore("@")
        else
            ""
    }

    private fun uriParams(uri: String): List<String> {
        val params = uri.split(";")
        return if (params.size == 1) listOf() else params.subList(1, params.size)
    }

    fun friendlyUri(uri: String, domain: String): String {
        var u = uri
        if (uri.startsWith("<") && (uri.endsWith(">")))
            u = uri.substring(1).substringBeforeLast(">")
        return if (u.contains("@")) {
            val user = uriUserPart(u)
            val host = uriHostPart(u)
            if (isE164Number(user) || (host == domain))
                user
            else
                "$user@$host"
        } else {
            u
        }
    }

    fun uriComplete(uri: String, domain: String): String {
        val res = if (!uri.startsWith("sip:")) "sip:$uri" else uri
        return if (checkUriUser(uri)) "$res@$domain" else res
    }

    fun aorDomain(aor: String): String {
        return uriHostPart(aor)
    }

    fun plainAor(aor: String): String {
        return uriUserPart(aor) + "@" + uriHostPart(aor)
    }

    fun checkAor(aor: String): Boolean {
        if (!checkSipUri(aor)) return false
        val params = uriParams(aor)
        return params.isEmpty() ||
                ((params.size == 1) &&
                        params[0] in arrayOf("transport=udp", "transport=tcp", "transport=tls"))
    }

    private fun checkTransport(transport: String, transports: Set<String>): Boolean {
        return transport.split("=")[0] == "transport" &&
                transport.split("=")[1].lowercase() in transports
    }

    fun checkStunUri(uri: String): Boolean {
        if (uri.substringBefore(":").lowercase() !in setOf("stun", "stuns", "turn", "turns"))
            return false
        return checkHostPort(uri.substringAfter(":").substringBefore("?")) &&
                (uri.indexOf("?") == -1 ||
                checkTransport(uri.substringAfter("?"), setOf("udp", "tcp")))
    }

    fun isE164Number(no: String): Boolean {
        return Regex("^[+][1-9][0-9]{0,14}\$").matches(no)
    }

    fun checkIpV4(ip: String): Boolean {
        return Regex("^(([0-1]?[0-9]{1,2}\\.)|(2[0-4][0-9]\\.)|(25[0-5]\\.)){3}(([0-1]?[0-9]{1,2})|(2[0-4][0-9])|(25[0-5]))$").matches(ip)
    }

    private fun checkIpV6(ip: String): Boolean {
        return Regex("^(([0-9a-fA-F]{0,4}:){1,7}[0-9a-fA-F]{0,4})$").matches(ip)
    }

    private fun checkIpv6InBrackets(bracketedIp: String): Boolean {
        return bracketedIp.startsWith("[") && bracketedIp.endsWith("]") &&
                checkIpV6(bracketedIp.substring(1, bracketedIp.length - 2))
    }

    fun checkUriUser(user: String): Boolean {
        val escaped = """%(\d|A|B|C|D|E|F|a|b|c|d|e|f){2}""".toRegex()
        escaped.replace(user, "").forEach {
            if (!(it.isLetterOrDigit() || "-_.!~*\'()&=+\$,;?/".contains(it))) return false }
        return user.isNotEmpty() && !checkIpV4(user) && !checkIpV6(user)
    }

    fun checkDomain(domain: String): Boolean {
        val parts = domain.split(".")
        for (p in parts) {
            if (p.endsWith("-") || p.startsWith("-") ||
                    !Regex("^[-a-zA-Z0-9]+\$").matches(p))
                return false
        }
        return true
    }

    private fun checkPort(port: String): Boolean {
        val number = port.toIntOrNull() ?: return false
        return (number > 0) && (number < 65536)
    }

    fun checkIpPort(ipPort: String): Boolean {
        return if (ipPort.startsWith("["))
            checkIpv6InBrackets(ipPort.substringBeforeLast(":")) &&
                    checkPort(ipPort.substringAfterLast(":"))
        else
            checkIpV4(ipPort.substringBeforeLast(":")) &&
                    checkPort(ipPort.substringAfterLast(":"))
    }

    private fun checkDomainPort(domainPort: String): Boolean {
        return checkDomain(domainPort.substringBeforeLast(":")) &&
                checkPort(domainPort.substringAfterLast(":"))
    }

    private fun checkHostPort(hostPort: String): Boolean {
        return checkIpV4(hostPort) || checkIpv6InBrackets(hostPort) || checkDomain(hostPort) ||
                checkIpPort(hostPort) || checkDomainPort(hostPort)
    }

    private fun checkParams(params: String): Boolean {
        for (param in params.split(";"))
            if (!checkParam(param)) return false
        return true
    }

    private fun checkParam(param: String): Boolean {
        val nameValue = param.split("=")
        if (nameValue.size == 1)
            return checkParamChars(nameValue[0])
        if (nameValue.size == 2) {
            if (nameValue[0] == "transport")
                return setOf("udp", "tcp", "tls", "wss").contains(nameValue[1].lowercase())
            return checkParamChars(nameValue[1])
        }
        return false
    }

    private fun checkParamChars(s: String): Boolean {
        // Does not currently allow escaped characters
        val allowed = "[]/:&+$-_.!~*'()"
        for (c in s)
            if (!allowed.contains(c) && !c.isLetterOrDigit())
                return false
        return true
    }

    fun paramValue(params: String, name: String): String {
        if (params == "") return ""
        for (param in params.split(";"))
            if (param.substringBefore("=") == name) return param.substringAfter("=")
        return ""
    }

    fun checkHostPortParams(hpp: String) : Boolean {
        val restParams = hpp.split(";", limit = 2)
        return if (restParams.size == 1)
            checkHostPort(restParams[0])
        else
            checkHostPort(restParams[0]) && checkParams(restParams[1])
    }

    fun checkSipUri(uri: String): Boolean {
        if (!uri.startsWith("sip:")) return false
        val userRest = uri.substring(4).split("@")
        return when (userRest.size) {
            1 ->
                checkHostPortParams(userRest[0])
            2 ->
                checkUriUser(userRest[0]) && checkHostPortParams(userRest[1])
            else -> false
        }
    }

    fun checkName(name: String): Boolean {
        return name.isNotEmpty() && name == String(name.toByteArray(), Charsets.UTF_8) &&
                name.lines().size == 1 && !name.contains('"')
    }

    @Suppress("unused")
    fun checkIfName(name: String): Boolean {
        if ((name.length < 2) || !name.first().isLetter()) return false
        for (c in name)
            if (!c.isLetterOrDigit()) return false
        return true
    }

    fun findIpV6Address(list: List<LinkAddress>): String {
        for (la in list)
            if (la.scope == android.system.OsConstants.RT_SCOPE_UNIVERSE)
                if (checkIpV6(la.address.hostAddress))
                    return la.address.hostAddress
        return ""
    }

    fun findIpV4Address(list: List<LinkAddress>): String {
        for (la in list)
            if (la.scope == android.system.OsConstants.RT_SCOPE_UNIVERSE)
                if (checkIpV4(la.address.hostAddress))
                    return la.address.hostAddress
        return ""
    }

    fun findDnsServers(list: List<InetAddress>): String {
        var servers = ""
        for (dnsServer in list) {
            var address = dnsServer.hostAddress.removePrefix("/")
            address = if (checkIpV4(address))
                "${address}:53"
            else
                "[${address}]:53"
            servers = if (servers == "")
                address
            else
                "${servers},${address}"
        }
        return servers
    }

    fun hotSpotAddresses(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        try {
            val interfaces: Enumeration<NetworkInterface> = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface: NetworkInterface = interfaces.nextElement()
                val ifName = iface.name
                Log.d(TAG, "Found interface with name $ifName")
                if (ifName.startsWith("ap") || ifName.contains("wlan")) {
                    val addresses: Enumeration<InetAddress> = iface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val inetAddress: InetAddress = addresses.nextElement()
                        if (inetAddress.isSiteLocalAddress)
                            result[inetAddress.hostAddress!!] = ifName
                    }
                    if (result.isNotEmpty()) return result
                }
            }
        } catch (ex: SocketException) {
            Log.e(TAG, "hotSpotAddresses SocketException: $ex")
        } catch (ex: NullPointerException) {
            Log.e(TAG, "hotSpotAddresses NullPointerException: $ex")
        }
        return result
    }

    fun implode(list: List<String>, sep: String): String {
        var res = ""
        for (s in list) {
            res = if (res == "")
                s
            else
                res + sep + s
        }
        return res
    }

    fun normalizeAor(aor: String): String {
        return aor.trim().removePrefix("sip:").removeSurrounding("<", ">").lowercase()
    }


    fun isVisible(): Boolean {
        val appProcessInfo = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(appProcessInfo)
        return appProcessInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }

    fun dtmfWatcher(callp: Long): TextWatcher {
        return object : TextWatcher {
            override fun beforeTextChanged(sequence: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(sequence: CharSequence, start: Int, before: Int, count: Int) {
                val text = sequence.subSequence(start, start + count).toString()
                if (text.isNotEmpty()) {
                    val digit = text[0]
                    val call = Call.ofCallp(callp)
                    if (call == null) {
                        Log.w(TAG, "dtmfWatcher did not find call $callp")
                    } else {
                        Log.d(TAG, "Got DTMF digit '$digit'")
                        if (((digit >= '0') && (digit <= '9')) || (digit == '*') || (digit == '#'))
                            call.sendDigit(digit)
                    }
                }
            }
            override fun afterTextChanged(sequence: Editable) {
                // KEYCODE_REL
                // call_send_digit(callp, 4.toChar())
            }
        }
    }

    fun checkPermission(ctx: Context, permissions: String) : Boolean {
        for (p in permissions.split("|")) {
            if (ContextCompat.checkSelfPermission(ctx, p) != PackageManager.PERMISSION_GRANTED)
                return false
        }
        return true
    }

    fun requestPermission(ctx: Context, permissions: String, requestCode: Int) : Boolean {
        val pArray = permissions.split("|").toTypedArray()
        for (p in pArray)
            if (ContextCompat.checkSelfPermission(ctx, p) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(ctx as Activity, pArray, requestCode)
                return false
            }
        return true
    }

    fun copyAssetToFile(context: Context, asset: String, path: String) {
        try {
            val `is` = context.assets.open(asset)
            val os = FileOutputStream(path)
            val buffer = ByteArray(512)
            var byteRead: Int = `is`.read(buffer)
            while (byteRead  != -1) {
                os.write(buffer, 0, byteRead)
                byteRead = `is`.read(buffer)
            }
            os.close()
            `is`.close()
        } catch (e: IOException) {
            Log.e(TAG, "Failed to copy asset '$asset' to file: $e")
        }
    }

    fun deleteFile(file: File) {
        if (file.exists()) {
            try {
                file.delete()
            } catch (e: IOException) {
                Log.e(TAG, "Could not delete file ${file.absolutePath}")
            }
        }
    }

    fun getFileContents(filePath: String): ByteArray? {
        return try {
            File(filePath).readBytes()
        } catch (e: FileNotFoundException) {
            Log.e(TAG, "File '$filePath' not found: ${e.printStackTrace()}")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read file '$filePath': ${e.printStackTrace()}")
            null
        }
    }

    fun putFileContents(filePath: String, contents: ByteArray): Boolean {
        try {
            File(filePath).writeBytes(contents)
        }
        catch (e: IOException) {
            Log.e(TAG, "Failed to write file '$filePath': $e")
            return false
        }
        return true
    }

    fun File.copyInputStreamToFile(inputStream: InputStream): Boolean {
        try {
            this.outputStream().use { fileOut ->
                inputStream.copyTo(fileOut)
            }
            return true
        }
        catch (e: IOException) {
            Log.e(TAG, "Failed to write file '${this.absolutePath}': $e")
        }
        return false
    }

    @RequiresApi(29)
    fun selectInputFile(request: ActivityResultLauncher<Intent>) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, MediaStore.Downloads.EXTERNAL_CONTENT_URI)
        }
        request.launch(intent)
    }

    @RequiresApi(29)
    @Suppress("unused")
    fun selectOutputFile(title: String) {
        Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_TITLE, title)
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, MediaStore.Downloads.EXTERNAL_CONTENT_URI)
        }
    }

    @Suppress("DEPRECATION")
    fun downloadsPath(fileName: String): String {
        return Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS).path + "/$fileName"
    }

    fun fileNameOfUri(ctx: Context, uri: Uri): String {
        val cursor = ctx.contentResolver.query(uri, null, null, null, null)
        var name = ""
        if (cursor != null) {
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            cursor.moveToFirst()
            name = cursor.getString(index)
            cursor.close()
        }
        return name
    }

    fun saveBitmap(bitmap: Bitmap, file: File): Boolean {
        if (file.exists()) file.delete()
        try {
            val out = FileOutputStream(file)
            val scaledBitmap = createScaledBitmap(bitmap, 96, 96, true)
            scaledBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
            out.close()
            Log.d(TAG, "Saved bitmap to ${file.absolutePath} of length ${file.length()}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save bitmap to ${file.absolutePath}")
            return false
        }
        return true
    }

    class Crypto(val salt: ByteArray, val iter: Int, val iv: ByteArray, val data: ByteArray):
        Serializable {
        companion object {
            private const val serialVersionUID: Long = -29238082928391L
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun setCommunicationDevice(am: AudioManager, type: Int) {
        val current = am.communicationDevice!!.type
        Log.d(TAG, "Current com dev/mode $current/${am.mode}")
        for (device in am.availableCommunicationDevices)
            if (device.type == type) {
                am.setCommunicationDevice(device)
                break
            }
        Log.d(TAG, "New com dev/mode ${am.communicationDevice!!.type}/${am.mode}")
    }

    private fun clearCommunicationDevice(am: AudioManager) {
        if (Build.VERSION.SDK_INT >= 31) {
            am.clearCommunicationDevice()
        } else {
            @Suppress("DEPRECATION")
            if (am.isSpeakerphoneOn)
                am.isSpeakerphoneOn = false
        }
    }

    private fun encrypt(content: ByteArray, password: CharArray): Crypto? {
        var obj: Crypto? = null
        try {
            val sr = SecureRandom()
            val salt = ByteArray(128)
            sr.nextBytes(salt)
            val iterationCount = Random().nextInt(1024) + 512
            val pbKeySpec = PBEKeySpec(password, salt, iterationCount, 128)
            val secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
            val keyBytes = secretKeyFactory.generateSecret(pbKeySpec).encoded
            val keySpec = SecretKeySpec(keyBytes, "AES")
            val ivRandom = SecureRandom()
            val iv = ByteArray(16)
            ivRandom.nextBytes(iv)
            val ivSpec = IvParameterSpec(iv)
            val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
            val cipherData = cipher.doFinal(content)
            obj = Crypto(salt, iterationCount, iv, cipherData)
        } catch (e: Exception) {
            Log.e(TAG, "Encrypt failed: ${e.printStackTrace()}")
        }
        return obj

    }

    private fun decrypt(obj: Crypto, password: CharArray): ByteArray? {
        var plainData: ByteArray? = null
        try {
            val pbKeySpec = PBEKeySpec(password, obj.salt, obj.iter, 128)
            val secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
            val keyBytes = secretKeyFactory.generateSecret(pbKeySpec).encoded
            val keySpec = SecretKeySpec(keyBytes, "AES")
            val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
            val ivSpec = IvParameterSpec(obj.iv)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            plainData = cipher.doFinal(obj.data)
        } catch (e: Exception) {
            Log.e(TAG, "Decrypt failed: ${e.printStackTrace()}")
        }
        return plainData
    }

    fun encryptToStream(stream: FileOutputStream?, content: ByteArray, password: String): Boolean {
        val obj = encrypt(content, password.toCharArray())
        try {
            ObjectOutputStream(stream).use {
                it.writeObject(obj)
            }
        } catch (e: Exception) {
            Log.w(TAG, "encryptToStream failed: $e")
            return false
        }
        return true
    }

    fun decryptFromStream(stream: FileInputStream?, password: String): ByteArray? {
        var plainData: ByteArray? = null
        try {
            ObjectInputStream(stream).use {
                val obj = it.readObject() as Crypto
                plainData = decrypt(obj, password.toCharArray())
            }
        } catch (e: Exception) {
            Log.w(TAG, "decryptFromStream failed: $e")
        }
        return plainData
    }

    fun zip(fileNames: ArrayList<String>, zipFileName: String): Boolean {
        val zipFilePath = SipService.filesPath + "/" + zipFileName
        try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFilePath))).use { out ->
                val data = ByteArray(1024)
                for (file in fileNames) {
                    val filePath = SipService.filesPath + "/" + file
                    if (File(filePath).exists()) {
                        FileInputStream(filePath).use { fi ->
                            BufferedInputStream(fi).use { origin ->
                                val entry = ZipEntry(filePath)
                                out.putNextEntry(entry)
                                while (true) {
                                    val readBytes = origin.read(data)
                                    if (readBytes == -1) break
                                    out.write(data, 0, readBytes)
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to zip file '$zipFilePath': $e")
            return false
        }
        return true
    }

    @Suppress("unused")
    fun dumpIntent(intent: Intent) {
        val bundle: Bundle = intent.extras ?: return
        val keys = bundle.keySet()
        val it = keys.iterator()
        Log.d(TAG, "Dumping intent start")
        while (it.hasNext()) {
            val key = it.next()
            Log.d(TAG, "[" + key + "=" + bundle.get(key) + "]")
        }
        Log.d(TAG, "Dumping intent finish")
    }

    fun addActivity(activity: String) {
        if ((SipService.activities.size == 0) || (SipService.activities[0] != activity))
            SipService.activities.add(0, activity)
    }

    @Suppress("unused")
    fun darkTheme(ctx: Context): Boolean {
        return ctx.resources?.configuration?.uiMode?.and(Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
    }

    fun paramExists(params: String, name: String): Boolean {
        for (param in params.split(";"))
            if (param.substringBefore("=") == name) return true
        return false
    }

}