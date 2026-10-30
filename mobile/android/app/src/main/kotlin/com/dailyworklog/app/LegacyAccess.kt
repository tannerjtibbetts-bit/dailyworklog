package com.dailyworklog.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Sign-in state left by the earlier (Java) version of the app, so an update keeps everyone
 * signed in: the "access" preferences, a handed-over owner key (locked with the
 * "dailyworklog_wrap" key) and the original owner key "dailyworklog_owner", which never leaves
 * the phone's key store and can only sign.
 */
object LegacyAccess {
    private const val ALIAS = "dailyworklog_owner"
    private const val WRAP = "dailyworklog_wrap"
    private const val PREFS = "access"

    fun register(engine: FlutterEngine, context: Context) {
        val app = context.applicationContext
        val main = Handler(Looper.getMainLooper())
        MethodChannel(engine.dartExecutor.binaryMessenger, "dailyworklog/legacy_access").setMethodCallHandler { call, result ->
            Thread {
                try {
                    val out: Any? = when (call.method) {
                        "readPrefs" -> readPrefs(app)
                        "hasKeystoreOwnerKey" -> ownerEntry() != null
                        "keystoreOwnerPublicKey" -> ownerEntry()?.certificate?.publicKey?.encoded?.let {
                            Base64.encodeToString(it, Base64.NO_WRAP)
                        }
                        "keystoreSign" -> sign(call.argument<ByteArray>("data") ?: throw IllegalArgumentException("no data"))
                        else -> {
                            main.post { result.notImplemented() }
                            return@Thread
                        }
                    }
                    main.post { result.success(out) }
                } catch (e: Exception) {
                    main.post { result.error("legacy_access", e.message, null) }
                }
            }.start()
        }
    }

    private fun readPrefs(c: Context): Map<String, Any?> {
        val m = HashMap<String, Any?>()
        for ((k, v) in c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all) {
            if (v is String || v is Boolean || v is Long || v is Int) m[k] = v
        }
        val wrapped = m["ownerKey"] as? String
        if (wrapped != null) {
            try {
                m["unwrappedOwnerKey"] = Base64.encodeToString(unwrap(wrapped), Base64.NO_WRAP)
            } catch (e: Exception) {
                // Couldn't unlock it: this phone just isn't a handed-over owner any more
            }
        }
        return m
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun ownerEntry(): KeyStore.PrivateKeyEntry? = try {
        val ks = keyStore()
        if (ks.containsAlias(ALIAS)) ks.getEntry(ALIAS, null) as? KeyStore.PrivateKeyEntry else null
    } catch (e: Exception) {
        null
    }

    private fun sign(data: ByteArray): ByteArray {
        val key = keyStore().getKey(ALIAS, null) as PrivateKey
        val s = Signature.getInstance("SHA256withECDSA")
        s.initSign(key)
        s.update(data)
        return s.sign()
    }

    private fun unwrap(wrapped: String): ByteArray {
        val p = wrapped.split(":")
        val key = keyStore().getKey(WRAP, null) as SecretKey
        val ci = Cipher.getInstance("AES/GCM/NoPadding")
        ci.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, java.util.Base64.getDecoder().decode(p[0])))
        return ci.doFinal(java.util.Base64.getDecoder().decode(p[1]))
    }
}
