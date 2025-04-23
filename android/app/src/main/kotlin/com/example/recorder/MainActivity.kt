package com.example.recorder

import android.app.Activity
import android.content.Intent
import android.media.MediaScannerConnection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import androidx.annotation.NonNull
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import com.hbisoft.hbrecorder.HBRecorder
import com.hbisoft.hbrecorder.HBRecorderListener
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : FlutterActivity(), HBRecorderListener {

    companion object {
        private const val CHANNEL = "ÆX Recorder"
        private const val SCREEN_RECORD_REQUEST_CODE = 777
        private const val STORAGE_PERM_REQUEST_CODE = 888
    }

    private lateinit var hbRecorder: HBRecorder
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private lateinit var methodChannel: MethodChannel

    // Track legacy path for media scan
    private var outputDir: File? = null
    private var outputFileName: String? = null
    private var pendingStartResult: MethodChannel.Result? = null

    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        // Initialize HBRecorder
        hbRecorder = HBRecorder(this, this)
        mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        // Setup MethodChannel
        methodChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
        methodChannel.setMethodCallHandler { call, result ->
            when (call.method) {
                "startRecording" -> handleStartRecording(result)
                "stopRecording"  -> handleStopRecording(result)
                else -> result.notImplemented()
            }
        }
    }

    private fun handleStartRecording(result: MethodChannel.Result) {
        if (hbRecorder.isBusyRecording()) {
            result.error("ALREADY_RECORDING", "Already recording", null)
            return
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                pendingStartResult = result
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    STORAGE_PERM_REQUEST_CODE
                )
                return
            }
            configureLegacyPath()
        }
        startScreenCapture()
        result.success(null)
    }

    private fun configureLegacyPath() {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        outputFileName = "recording_$ts.mp4"
        val publicMovies = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        outputDir = File(publicMovies, "EAXRecordings").apply { if (!exists()) mkdirs() }
        hbRecorder.setFileName(outputFileName)
        hbRecorder.setOutputPath(outputDir!!.absolutePath)
    }

    private fun handleStopRecording(result: MethodChannel.Result) {
        if (!hbRecorder.isBusyRecording()) {
            result.error("NOT_RECORDING", "No recording in progress", null)
        } else {
            result.success(stopRecordingSafely())
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERM_REQUEST_CODE) {
            pendingStartResult?.let { result ->
                pendingStartResult = null
                if (grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    configureLegacyPath()
                    startScreenCapture()
                    result.success(null)
                } else {
                    result.error("STORAGE_DENIED", "Storage permission denied", null)
                }
            }
        }
    }

    private fun startScreenCapture() {
        val intent = mediaProjectionManager.createScreenCaptureIntent()
        startActivityForResult(intent, SCREEN_RECORD_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SCREEN_RECORD_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                hbRecorder.startScreenRecording(data, resultCode)
            } else {
                notifyFlutter(
                    "ÆX RecorderOnError",
                    mapOf("code" to -2, "message" to "Screen recording permission denied")
                )
            }
        }
    }

    private fun stopRecordingSafely(): Boolean = try {
        hbRecorder.stopScreenRecording()
        true
    } catch (ex: Exception) {
        notifyFlutter("ÆX RecorderOnError", mapOf("code" to -1, "message" to ex.message))
        false
    }

    // HBRecorderListener callbacks
    override fun HBRecorderOnStart() {
        notifyFlutter("ÆX RecorderOnStart")
    }

    override fun HBRecorderOnComplete() {
        notifyFlutter("ÆX RecorderOnComplete")
        // Scan saved file for gallery
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P && outputDir != null && outputFileName != null) {
            val path = File(outputDir, outputFileName).absolutePath
            MediaScannerConnection.scanFile(this, arrayOf(path), arrayOf("video/mp4"), null)
        }
    }

    override fun HBRecorderOnError(errorCode: Int, reason: String?) {
        notifyFlutter("ÆX RecorderOnError", mapOf("code" to errorCode, "message" to reason))
    }

    override fun HBRecorderOnPause() {
        notifyFlutter("ÆX RecorderOnPause")
    }

    override fun HBRecorderOnResume() {
        notifyFlutter("ÆX RecorderOnResume")
    }

    private fun notifyFlutter(method: String, args: Any? = null) {
        runOnUiThread { methodChannel.invokeMethod(method, args) }
    }
}
