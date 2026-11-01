package com.dipak.hearclear

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.net.URL
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.net.ssl.HttpsURLConnection

/** Authentication network work is called only from worker threads. Audio never leaves the phone. */
object AccountClient {
    private val lease = AccessLease()
    @Volatile var summary = "Sign in for your 48-hour trial or lifetime access."
        private set
    @Volatile var account = ""
        private set
    @Volatile var paid = false
        private set
    fun configured() = BuildConfig.ACCOUNT_API_URL.startsWith("https://")
    fun canListen() = lease.valid(SystemClock.elapsedRealtime())
    fun clearLease() { lease.clear() }
    private fun prefs(context: Context) = context.getSharedPreferences("account", Context.MODE_PRIVATE)
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey("HearClearSession", null) as? SecretKey) ?: KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("HearClearSession", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun saveToken(context: Context, token: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val raw = cipher.iv + cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        check(prefs(context).edit().putString("token", Base64.encodeToString(raw, Base64.NO_WRAP)).commit())
    }
    private fun token(context: Context): String = runCatching {
        val raw = Base64.decode(prefs(context).getString("token", ""), Base64.NO_WRAP)
        if (raw.size <= 12) return@runCatching ""
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw.copyOfRange(0, 12)))
        String(cipher.doFinal(raw.copyOfRange(12, raw.size)), Charsets.UTF_8)
    }.getOrDefault("")
    fun hasSession(context: Context) = prefs(context).contains("token")
    private fun device(context: Context): String {
        val id = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        check(!id.isNullOrBlank()) { "Device identity is unavailable." }
        return MessageDigest.getInstance("SHA-256").digest(("HearClear:" + id).toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 255) }
    }
    class ApiException(val code: String, message: String) : Exception(message)
    private fun request(context: Context, path: String, body: JSONObject, authenticated: Boolean = true): JSONObject {
        check(configured()) { "Accounts are not live yet. The app owner must configure the secure server." }
        val connection = URL(BuildConfig.ACCOUNT_API_URL.trimEnd('/') + "/v1/" + path).openConnection() as HttpsURLConnection
        try {
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.instanceFollowRedirects = false
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            if (authenticated) connection.setRequestProperty("Authorization", "Bearer " + token(context))
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader()?.use { it.readText() } ?: "{}"
            val data = JSONObject(raw)
            if (code !in 200..299) {
                if (code == 401) { lease.clear(); prefs(context).edit().remove("token").apply() }
                throw ApiException(data.optString("error"), data.optString("message", "Server request failed ($code)."))
            }
            return data
        } finally { connection.disconnect() }
    }
    @Synchronized fun authenticate(context: Context, mode: String, id: String, password: String, recovery: String, consent: Boolean): String {
        val result = request(context, mode, JSONObject().put("account", id).put("password", password)
            .put("device", device(context)).put("recovery_code", recovery).put("consent", consent), false)
        lease.clear()
        saveToken(context, result.getString("token"))
        account = id
        runCatching { refresh(context) } // Never lose the one-time recovery code on a status network failure.
        return result.optString("recovery_code")
    }
    @Synchronized fun refresh(context: Context) {
        val start = SystemClock.elapsedRealtime()
        try {
            val data = request(context, "status", JSONObject())
            val seconds = data.getInt("lease_seconds")
            lease.grant(start, SystemClock.elapsedRealtime(), seconds)
            account = data.getString("account")
            paid = data.getBoolean("paid")
            val left = data.getLong("trial_remaining_seconds")
            summary = if (paid) "$account · Lifetime access · updates free" else if (left > 0)
                "$account · Trial: ${(left + 3599) / 3600} hours left" else "$account · Trial ended. One-time payment required."
        } catch (e: Exception) {
            // A network failure never extends an existing lease. A rejected session clears it immediately.
            summary = e.message ?: "Connect to verify account access."
            throw e
        }
    }
    @Synchronized fun quote(context: Context) = request(context, "quote", JSONObject())
    @Synchronized fun receipt(context: Context, order: String, reference: String) =
        request(context, "receipt", JSONObject().put("order", order).put("reference", reference))
    @Synchronized fun logout(context: Context) {
        runCatching { request(context, "logout", JSONObject()) }
        prefs(context).edit().clear().commit()
        lease.clear(); account = ""; paid = false
        summary = "Signed out. Sign in to continue."
    }
}
