package com.xanspot.net

import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.util.Log
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

import com.xanspot.net.vpn.VpnServiceImpl // <--- ESTE IMPORT É CRUCIAL E DEVE PERMANECER

class MainActivity : FlutterActivity() {
    private val CHANNEL = "com.xanspot.net/vpn"
    private val TAG = "MainActivity"
    private var pendingResult: MethodChannel.Result? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "startVpn" -> {
                    Log.d(TAG, "Starting VPN request")
                    pendingResult = result
                    try {
                        val intent = VpnService.prepare(this)
                        if (intent != null) {
                            Log.d(TAG, "VPN permission required, launching prompt")
                            startActivityForResult(intent, 100)
                        } else {
                            Log.d(TAG, "VPN permission already granted, starting service")
                            startVpnService()
                            result.success(true)
                            pendingResult = null
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to prepare VPN: ${e.message}", e)
                        result.error("VPN_PREPARE_FAILED", "Failed to prepare VPN: ${e.message}", null)
                        pendingResult = null
                    }
                }
                "stopVpn" -> {
                    Log.d(TAG, "Stopping VPN")
                    try {
                        stopVpnService()
                        result.success(true)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to stop VPN: ${e.message}", e)
                        result.error("VPN_STOP_FAILED", "Failed to stop VPN: ${e.message}", null)
                    }
                }
                else -> {
                    Log.w(TAG, "Method not implemented: ${call.method}")
                    result.notImplemented()
                }
            }
        }
    }

    private fun startVpnService() {
        val intent = Intent(this, VpnServiceImpl::class.java).apply {
            action = VpnServiceImpl.ACTION_CONNECT
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Log.d(TAG, "Starting VPN service as foreground (API ${Build.VERSION.SDK_INT})")
                startForegroundService(intent)
            } else {
                Log.d(TAG, "Starting VPN service (API ${Build.VERSION.SDK_INT})")
                startService(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start VPN service: ${e.message}", e)
            pendingResult?.error("VPN_START_FAILED", "Failed to start VPN service: ${e.message}", null)
            pendingResult = null
        }
    }

    private fun stopVpnService() {
        val intent = Intent(this, VpnServiceImpl::class.java).apply {
            action = VpnServiceImpl.ACTION_DISCONNECT
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                stopService(intent)
            } else {
                stopService(intent)
            }
            Log.d(TAG, "VPN service stop requested")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop VPN service: ${e.message}", e)
            throw e
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100) {
            if (resultCode == RESULT_OK) {
                Log.d(TAG, "VPN permission granted, starting service")
                startVpnService()
                pendingResult?.success(true)
            } else {
                Log.w(TAG, "VPN permission denied by user")
                pendingResult?.success(false)
            }
            pendingResult = null
        }
    }
}