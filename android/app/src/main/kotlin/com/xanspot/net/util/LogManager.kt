package com.xanspot.net.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class LogManager(private val context: Context) {

    companion object {
        private const val LOG_FILE = "vpn_service.log"
        private const val MAX_LOG_FILE_SIZE = 5 * 1024 * 1024 // 5MB
        private const val TAG = "VpnLogger"
    }

    private var logFileOutput: FileOutputStream? = null

    init {
        initializeLogFile()
    }

    private fun initializeLogFile() {
        try {
            val logFile = File(context.filesDir, LOG_FILE)
            if (logFile.exists() && logFile.length() > MAX_LOG_FILE_SIZE) {
                // Limpa o arquivo se exceder o tamanho
                FileOutputStream(logFile).use { it.write("".toByteArray()) }
                Log.d(TAG, "Log file cleared due to size limit.")
            }
            // Abre o arquivo em modo append
            logFileOutput = FileOutputStream(logFile, true)
            Log.d(TAG, "Log file initialized.")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to initialize log file: ${e.message}")
        }
    }

    fun writeToLog(message: String) {
        val timestamp = System.currentTimeMillis()
        val logMessage = "[$timestamp] $message\n"
        Log.d(TAG, logMessage) // Log para Logcat também

        try {
            logFileOutput?.write(logMessage.toByteArray())
            logFileOutput?.flush() // Garante que os dados são gravados no disco
        } catch (e: IOException) {
            Log.e(TAG, "Failed to write to log file: ${e.message}")
        }
    }

    fun close() {
        try {
            logFileOutput?.close()
            logFileOutput = null
            Log.d(TAG, "Log file closed.")
        } catch (e: IOException) {
            Log.e(TAG, "Error closing log file: ${e.message}")
        }
    }
}