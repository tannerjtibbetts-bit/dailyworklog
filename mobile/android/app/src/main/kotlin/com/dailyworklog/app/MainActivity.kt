package com.dailyworklog.app

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
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
        // Copies a photo taken in the app into the phone's gallery (Pictures/Daily Work Log).
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "dailyworklog/gallery").setMethodCallHandler { call, result ->
            if (call.method != "save") {
                result.notImplemented()
                return@setMethodCallHandler
            }
            val path = call.argument<String>("path") ?: ""
            val album = call.argument<String>("album") ?: "Daily Work Log"
            Thread {
                val ok = saveToGallery(File(path), album)
                runOnUiThread { result.success(ok) }
            }.start()
        }
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

    /** A separate copy: deleting it from the gallery doesn't touch the app's photo, and the other way round. */
    private fun saveToGallery(f: File, album: String): Boolean {
        if (!f.isFile) return false
        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, f.name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + album)
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
                try {
                    contentResolver.openOutputStream(uri)?.use { out -> f.inputStream().use { it.copyTo(out) } }
                        ?: throw java.io.IOException("no stream")
                } catch (e: Exception) {
                    contentResolver.delete(uri, null, null)
                    return false
                }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                contentResolver.update(uri, values, null, null)
                true
            } else {
                // Android 9 and older need the storage permission; without it, skip.
                if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    return false
                }
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), album)
                if (!dir.isDirectory && !dir.mkdirs()) return false
                val dest = File(dir, f.name)
                f.inputStream().use { input -> FileOutputStream(dest).use { input.copyTo(it) } }
                MediaScannerConnection.scanFile(this, arrayOf(dest.absolutePath), arrayOf("image/jpeg"), null)
                true
            }
        } catch (e: Exception) {
            false
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

    companion object {
        private const val SMS_REQUEST = 41
    }
}
