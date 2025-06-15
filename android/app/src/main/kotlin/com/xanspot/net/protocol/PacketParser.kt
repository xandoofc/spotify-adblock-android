package com.xanspot.net.protocol

import com.xanspot.net.util.LogManager
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class IPPacket(
    val version: Int,
    val protocol: Int,
    val sourceAddress: InetAddress,
    val destinationAddress: InetAddress,
    val sourcePort: Int,
    val destinationPort: Int,
    val payload: ByteBuffer // Payload do TCP/UDP
)

class PacketParser(private val logManager: LogManager) {

    companion object {
        const val MIN_IP_HEADER_LENGTH = 20
        const val PROTOCOL_TCP = 6
        const val PROTOCOL_UDP = 17
        const val DNS_PORT = 53
        const val HTTPS_PORT = 443
        const val HTTP_PORT = 80
        private const val MAX_URL_LENGTH = 2048 // Para evitar leitura excessiva

        // TLS Handshake types
        private const val TLS_HANDSHAKE_TYPE = 0x16.toByte()
        private const val TLS_CLIENT_HELLO_TYPE = 0x01.toByte()
        private const val TLS_EXTENSION_SNI = 0x00
        private const val TLS_SNI_HOSTNAME_TYPE = 0x00
    }

    /**
     * Parses an IP packet from a ByteBuffer.
     * The ByteBuffer's position will be reset to its original state after parsing attempt.
     */
    fun parsePacket(buffer: ByteBuffer): IPPacket? {
        val originalPosition = buffer.position()
        buffer.order(ByteOrder.BIG_ENDIAN) // Network byte order is big-endian

        try {
            if (buffer.remaining() < MIN_IP_HEADER_LENGTH) return null

            val versionAndIhl = buffer.get() // Read 1 byte
            val version = (versionAndIhl.toInt() and 0xF0) shr 4
            if (version != 4) return null // Only support IPv4

            val ihl = (versionAndIhl.toInt() and 0x0F) * 4 // IP Header Length in bytes
            if (buffer.remaining() < ihl - 1) return null // Not enough data for full IP header

            buffer.position(originalPosition + 9) // Move to protocol field
            val protocol = buffer.get().toInt() and 0xFF

            buffer.position(originalPosition + 12) // Move to source address
            val sourceAddressBytes = ByteArray(4)
            buffer.get(sourceAddressBytes)
            val destinationAddressBytes = ByteArray(4)
            buffer.get(destinationAddressBytes)

            val sourceAddress = InetAddress.getByAddress(sourceAddressBytes)
            val destinationAddress = InetAddress.getByAddress(destinationAddressBytes)

            var sourcePort = 0
            var destinationPort = 0
            val payloadBuffer: ByteBuffer

            when (protocol) {
                PROTOCOL_TCP -> {
                    // Check for minimum TCP header length (20 bytes)
                    if (buffer.remaining() < (ihl - (buffer.position() - originalPosition)) + 20) return null
                    buffer.position(originalPosition + ihl) // Move to start of TCP header
                    sourcePort = buffer.short.toInt() and 0xFFFF
                    destinationPort = buffer.short.toInt() and 0xFFFF
                    payloadBuffer = buffer.slice() // Payload starts after TCP header
                }
                PROTOCOL_UDP -> {
                    // Check for minimum UDP header length (8 bytes)
                    if (buffer.remaining() < (ihl - (buffer.position() - originalPosition)) + 8) return null
                    buffer.position(originalPosition + ihl) // Move to start of UDP header
                    sourcePort = buffer.short.toInt() and 0xFFFF
                    destinationPort = buffer.short.toInt() and 0xFFFF
                    payloadBuffer = buffer.slice() // Payload starts after UDP header
                }
                else -> {
                    // For other protocols, payload starts right after IP header
                    buffer.position(originalPosition + ihl)
                    payloadBuffer = buffer.slice()
                }
            }

            return IPPacket(
                version, protocol,
                sourceAddress, destinationAddress,
                sourcePort, destinationPort,
                payloadBuffer
            )
        } catch (e: Exception) {
            logManager.writeToLog("Packet parsing error: ${e.message}")
            return null
        } finally {
            buffer.position(originalPosition) // Always reset position
        }
    }

    /**
     * Extracts the hostname (SNI) from an HTTPS (TLS Client Hello) payload.
     * The payload ByteBuffer's position will be reset to its original state after parsing attempt.
     */
    fun extractHttpsHost(payload: ByteBuffer): String? {
        val originalPayloadPosition = payload.position()
        payload.order(ByteOrder.BIG_ENDIAN)

        try {
            payload.position(0) // Start from the beginning of the payload
            // Check for TLS Handshake record type (0x16)
            if (payload.remaining() < 5 || payload.get() != TLS_HANDSHAKE_TYPE) return null

            // Skip TLS version (2 bytes), TLS record length (2 bytes)
            payload.getShort()
            payload.getShort()

            // Check for Client Hello handshake type (0x01)
            if (payload.remaining() < 1 || payload.get() != TLS_CLIENT_HELLO_TYPE) return null

            // Skip handshake length (3 bytes), protocol version (2 bytes), random (32 bytes)
            payload.get(ByteArray(3))
            payload.getShort()
            payload.get(ByteArray(32))

            // Skip session ID length and session ID
            val sessionIdLength = payload.get().toInt() and 0xFF
            payload.position(payload.position() + sessionIdLength)

            // Skip cipher suites length and cipher suites
            val cipherSuitesLength = payload.short.toInt() and 0xFFFF
            payload.position(payload.position() + cipherSuitesLength)

            // Skip compression methods length and compression methods
            val compressionMethodsLength = payload.get().toInt() and 0xFF
            payload.position(payload.position() + compressionMethodsLength)

            // Ensure we have remaining data for extensions
            if (!payload.hasRemaining()) return null

            // Read extensions length
            val extensionsLength = payload.short.toInt() and 0xFFFF
            val extensionsEnd = payload.position() + extensionsLength

            // Iterate through extensions
            while (payload.position() < extensionsEnd && payload.hasRemaining()) {
                val type = payload.short.toInt() and 0xFFFF
                val length = payload.short.toInt() and 0xFFFF

                if (type == TLS_EXTENSION_SNI) { // Server Name Indication (SNI) extension type
                    if (payload.remaining() < length) return null // Not enough data for SNI extension

                    val sniListLength = payload.short.toInt() and 0xFFFF
                    if (payload.remaining() < sniListLength) return null // Not enough data for SNI list

                    // Read SNI entries (assuming the first is the hostname)
                    val nameType = payload.get().toInt() and 0xFF
                    if (nameType == TLS_SNI_HOSTNAME_TYPE) { // Hostname type
                        val hostnameLength = payload.short.toInt() and 0xFFFF
                        if (payload.remaining() < hostnameLength) return null // Not enough data for hostname

                        val hostnameBytes = ByteArray(hostnameLength)
                        payload.get(hostnameBytes)
                        return String(hostnameBytes).take(MAX_URL_LENGTH) // Return the extracted hostname, bounded
                    }
                }
                // Skip to the next extension if not SNI or not the hostname type
                payload.position(payload.position() + length)
            }
        } catch (e: Exception) {
            logManager.writeToLog("Failed to extract HTTPS host (SNI): ${e.message}")
        } finally {
            payload.position(originalPayloadPosition) // Always reset buffer position
        }
        return null
    }

    /**
     * Extracts a DNS query from a UDP payload.
     * The payload ByteBuffer's position will be reset to its original state after parsing attempt.
     */
    fun extractDnsQuery(payload: ByteBuffer): String? {
        val originalPayloadPosition = payload.position()
        payload.order(ByteOrder.BIG_ENDIAN)
        try {
            payload.position(12) // Skip DNS header
            val queryBuilder = StringBuilder()
            while (payload.hasRemaining()) {
                val length = payload.get().toInt() and 0xFF
                if (length == 0) break // End of domain name
                repeat(length) {
                    if (payload.hasRemaining()) {
                        queryBuilder.append(payload.get().toChar())
                    }
                }
                queryBuilder.append('.')
            }
            val query = queryBuilder.toString().trimEnd('.')
            return if (query.isNotEmpty()) query else null
        } catch (e: Exception) {
            logManager.writeToLog("Failed to extract DNS query: ${e.message}")
            return null
        } finally {
            payload.position(originalPayloadPosition)
        }
    }
}