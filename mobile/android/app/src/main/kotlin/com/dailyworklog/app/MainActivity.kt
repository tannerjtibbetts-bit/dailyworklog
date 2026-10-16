package com.dailyworklog.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    /** Waiting for the "allow sending texts" answer. */
    private var permissionResult: MethodChannel.Result? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        // Sign-in state and owner key from the earlier Android app, moved over on first run.
        LegacyAccess.register(flutterEngine, applicationContext)
        // Sends a plain text straight from this phone (needs SEND_SMS). Replies false if it couldn't.
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "dailyworklog/sms").setMethodCallHandler { call, result ->
            when (call.method) {
                "send" -> {
                    val phone = (call.argument<String>("phone") ?: "").replace(Regex("[^0-9+]"), "")
                    val body = call.argument<String>("body") ?: ""
                    result.success(sendDirect(phone, body))
                }
                "hasPermission" -> result.success(hasSmsPermission())
                "requestPermission" -> {
                    if (hasSmsPermission()) {
                        result.success(true)
                    } else {
                        permissionResult?.success(false)
                        permissionResult = result
                        requestPermissions(arrayOf(Manifest.permission.SEND_SMS), SMS_REQUEST)
                    }
                }
                else -> result.notImplemented()
            }
        }
    }

    private fun hasSmsPermission() =
        checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != SMS_REQUEST) return
        permissionResult?.success(grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)
        permissionResult = null
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

    companion object {
        private const val SMS_REQUEST = 41
    }
}
