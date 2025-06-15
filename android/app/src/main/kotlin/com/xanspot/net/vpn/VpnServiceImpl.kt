package com.xanspot.net.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.xanspot.net.protocol.PacketParser
import com.xanspot.net.protocol.IPPacket
import com.xanspot.net.util.LogManager
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.regex.Pattern

// --- ADICIONE ESTE IMPORT FALTANDO ---
import android.content.Intent // <--- ESSA LINHA RESOLVE OS ERROS DE 'Intent' e 'action'

class VpnServiceImpl : VpnService() {

    companion object {
        const val ACTION_CONNECT = "com.xanspot.net.CONNECT"
        const val ACTION_DISCONNECT = "com.xanspot.net.DISCONNECT"
        // Removidas ACTION_START_BLOCKING e ACTION_STOP_BLOCKING

        private const val NOTIFICATION_CHANNEL_ID = "VpnBlockerChannel"
        private const val NOTIFICATION_ID = 1
        private const val TAG = "VpnServiceImpl"
        private const val MTU_SIZE = 1420
        private const val SPOTIFY_PACKAGE_URN = "com.spotify.music"
        private const val BUFFER_SIZE = 32767
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var packetProcessor: ExecutorService? = null
    private var isBlockingActive: Boolean = true

    private val logManager: LogManager by lazy { LogManager(applicationContext) }
    private val packetParser: PacketParser by lazy { PacketParser(logManager) }

    private val adDenylist = listOf(
        "doubleclick.net", "googleads.g.doubleclick.net", "adswizz.com", "g.doubleclick.net",
        "flashtalking.com", "ad.crwdcntrl.net", "creative.adx.io", "adservice.google.com",
        "adsrvr.org", "cdn.ad.mp.mydas.mobi", "static.doubleclick.net", "px.moatads.com",
        "v.moatads.com",
        """audio-ak-spotify-com\.akamaized\.net/ad_.*""",
        """audio4-ak-spotify-com\.akamaized\.net/ad_.*""",
        """audio-fa\.scdn\.co/ad_.*""",
        """audio-sp-.*\.pscdn\.co/ad_.*""",
        """video\.spotify\.com/ad_.*""",
        """https?://[^/]+\.spotify\.com/ad-logic/.*""",
        """https?://[^/]+\.spotify\.com/ads/.*""",
        """https?://[^/]+\.spotify\.com/gabo-receiver-service/.*/events(?!.*discord.*)""",
    ).map { Pattern.compile(it, Pattern.CASE_INSENSITIVE) }

    override fun onCreate() {
        super.onCreate()
        logManager.writeToLog("VpnService created. Blocking is permanently active.")
    }

    override fun onDestroy() {
        logManager.writeToLog("VpnService destroyed.")
        disconnect()
        super.onDestroy()
    }

    // A assinatura aqui já parece correta. O problema era o import da classe Intent.
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ACTION_DISCONNECT -> {
                logManager.writeToLog("Received DISCONNECT action.")
                disconnect()
                START_NOT_STICKY
            }
            else -> {
                logManager.writeToLog("Received CONNECT action or no action (defaulting to connect).")
                connect()
                START_STICKY
            }
        }
    }

    private fun updateNotificationContent() {
        val contentText = "VPN active and blocking ads."
        val notification: Notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Spotify Ad Blocker")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun connect() {
        if (vpnInterface != null) {
            logManager.writeToLog("VPN already connected. Skipping reconnection.")
            return
        }

        createNotificationChannel()
        updateNotificationContent()

        startForeground(NOTIFICATION_ID, NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID).build())

        val builder = Builder()
            .setSession("SpotifyAdBlocker")
            .addAddress("10.0.0.2", 32)
            .addDnsServer("1.1.1.1")
            .addDnsServer("9.9.9.9")
            .addDnsServer("8.8.8.8")
            .addRoute("0.0.0.0", 0)
            .addRoute("192.168.0.0", 16)
            .setMtu(MTU_SIZE)

        try {
            builder.addAllowedApplication(SPOTIFY_PACKAGE_URN)
            logManager.writeToLog("Allowed application: $SPOTIFY_PACKAGE_URN")
        } catch (e: Exception) {
            logManager.writeToLog("Failed to allow Spotify app ($SPOTIFY_PACKAGE_URN): ${e.message}")
        }

        try {
            vpnInterface = builder.establish()
            if (vpnInterface != null) {
                logManager.writeToLog("VPN interface established successfully.")
                startPacketProcessing()
            } else {
                logManager.writeToLog("Failed to establish VPN interface. vpnInterface is null.")
                disconnect()
            }
        } catch (e: Exception) {
            logManager.writeToLog("Error establishing VPN: ${e.message}")
            disconnect()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "VPN Blocker Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notification channel for the Spotify Ad Blocker VPN service."
            }
            val notificationManager: NotificationManager =
                getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun startPacketProcessing() {
        packetProcessor = Executors.newSingleThreadExecutor()
        packetProcessor?.execute {
            vpnInterface?.fileDescriptor?.let { fd ->
                FileInputStream(fd).use { input ->
                    FileOutputStream(fd).use { output ->
                        val buffer = ByteBuffer.allocate(BUFFER_SIZE)
                        val rawBufferArray = buffer.array()

                        while (!Thread.currentThread().isInterrupted) {
                            buffer.clear()
                            val length = try {
                                input.read(rawBufferArray, 0, buffer.capacity())
                            } catch (e: IOException) {
                                logManager.writeToLog("Read from VPN failed: ${e.message}")
                                break
                            }

                            if (length <= 0) {
                                Thread.sleep(10)
                                continue
                            }

                            buffer.limit(length)
                            val originalBufferSlice = buffer.slice()

                            val packet = packetParser.parsePacket(buffer)
                            if (packet != null) {
                                // O bloqueio está sempre ativo agora
                                processPacketBlockingLogic(packet, output, originalBufferSlice)
                            } else {
                                forwardPacket(output, originalBufferSlice)
                            }
                        }
                    }
                }
            } ?: logManager.writeToLog("VPN interface file descriptor is null. Cannot start packet processing.")
            disconnect()
        }
    }

    private fun processPacketBlockingLogic(packet: IPPacket, output: FileOutputStream, originalBuffer: ByteBuffer) {
        val host = packet.destinationAddress.hostAddress
        var url: String? = null
        var isBlocked = false

        when (packet.protocol) {
            PacketParser.PROTOCOL_UDP -> {
                if (packet.destinationPort == PacketParser.DNS_PORT) {
                    // Passa DNS sempre
                }
            }
            PacketParser.PROTOCOL_TCP -> {
                if (packet.destinationPort == PacketParser.HTTPS_PORT || packet.destinationPort == PacketParser.HTTP_PORT) {
                    url = packetParser.extractHttpsHost(packet.payload) ?: packet.destinationAddress.hostName
                    if (url != null) {
                        isBlocked = adDenylist.any { it.matcher(url).matches() }
                        if (isBlocked) {
                            logManager.writeToLog("BLOCKED AD: ${if (packet.destinationPort == PacketParser.HTTPS_PORT) "HTTPS" else "HTTP"} URL=$url (Dest IP: $host)")
                        }
                    }
                }
            }
        }

        if (!isBlocked) {
            forwardPacket(output, originalBuffer)
        }
    }

    private fun forwardPacket(output: FileOutputStream, buffer: ByteBuffer) {
        try {
            output.write(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining())
        } catch (e: IOException) {
            logManager.writeToLog("Failed to forward packet: ${e.message}")
        }
    }

    private fun disconnect() {
        logManager.writeToLog("Disconnecting VPN.")
        packetProcessor?.shutdownNow()
        packetProcessor = null
        try {
            vpnInterface?.close()
        } catch (e: IOException) {
            logManager.writeToLog("Error closing VPN interface: ${e.message}")
        } finally {
            vpnInterface = null
        }
        stopForeground(true)
        stopSelf()
    }
}