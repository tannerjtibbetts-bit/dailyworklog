package com.dailyworklog.app

import android.os.Build
import android.telephony.SmsManager
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        // Sends a plain text straight from this phone (needs SEND_SMS). Replies false if it couldn't.
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "dailyworklog/sms").setMethodCallHandler { call, result ->
            if (call.method != "send") {
                result.notImplemented()
                return@setMethodCallHandler
            }
            val phone = (call.argument<String>("phone") ?: "").replace(Regex("[^0-9+]"), "")
            val body = call.argument<String>("body") ?: ""
            result.success(sendDirect(phone, body))
        }
    }

    private fun sendDirect(phone: String, body: String): Boolean {
        if (phone.isEmpty()) return false
        return try {
            val sms: SmsManager? = if (Build.VERSION.SDK_INT >= 31) {
                getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            if (sms == null) return false
            val parts = sms.divideMessage(body)
            sms.sendMultipartTextMessage(phone, null, parts, null, null)
            true
        } catch (e: Exception) {
            false
        }
    }
}
