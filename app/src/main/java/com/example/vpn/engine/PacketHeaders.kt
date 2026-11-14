package com.example.vpn.engine

import java.net.InetAddress
import java.nio.ByteBuffer

/**
 * Parsed representation of an IPv4 Header (RFC 791).
 */
data class Ipv4Header(
    val version: Int,
    val ihl: Int, // Internet Header Length in 32-bit words (e.g. 5 = 20 bytes)
    val totalLength: Int,
    val identification: Int,
    val ttl: Int,
    val protocol: Int, // 1 = ICMP, 6 = TCP, 17 = UDP
    val srcIpBytes: ByteArray,
    val dstIpBytes: ByteArray
) {
    val headerLengthBytes: Int get() = ihl * 4

    val srcIpStr: String get() = InetAddress.getByAddress(srcIpBytes).hostAddress ?: "0.0.0.0"
    val dstIpStr: String get() = InetAddress.getByAddress(dstIpBytes).hostAddress ?: "0.0.0.0"

    companion object {
        fun parse(buffer: ByteArray, offset: Int = 0, length: Int): Ipv4Header? {
            if (length < 20) return null
            val verIhl = buffer[offset].toInt() and 0xFF
            val version = verIhl ushr 4
            if (version != 4) return null // Only IPv4 supported currently
            val ihl = verIhl and 0x0F
            val headerBytes = ihl * 4
            if (headerBytes > length) return null

            val totalLen = ((buffer[offset + 2].toInt() and 0xFF) shl 8) or (buffer[offset + 3].toInt() and 0xFF)
            val id = ((buffer[offset + 4].toInt() and 0xFF) shl 8) or (buffer[offset + 5].toInt() and 0xFF)
            val ttl = buffer[offset + 8].toInt() and 0xFF
            val protocol = buffer[offset + 9].toInt() and 0xFF

            val srcIp = buffer.copyOfRange(offset + 12, offset + 16)
            val dstIp = buffer.copyOfRange(offset + 16, offset + 20)

            return Ipv4Header(
                version = version,
                ihl = ihl,
                totalLength = totalLen,
                identification = id,
                ttl = ttl,
                protocol = protocol,
                srcIpBytes = srcIp,
                dstIpBytes = dstIp
            )
        }
    }
}

/**
 * Parsed representation of a UDP Header (RFC 768).
 */
data class UdpHeader(
    val srcPort: Int,
    val dstPort: Int,
    val length: Int,
    val checksum: Int
) {
    companion object {
        fun parse(buffer: ByteArray, offset: Int, length: Int): UdpHeader? {
            if (length < 8) return null
            val srcPort = ((buffer[offset].toInt() and 0xFF) shl 8) or (buffer[offset + 1].toInt() and 0xFF)
            val dstPort = ((buffer[offset + 2].toInt() and 0xFF) shl 8) or (buffer[offset + 3].toInt() and 0xFF)
            val len = ((buffer[offset + 4].toInt() and 0xFF) shl 8) or (buffer[offset + 5].toInt() and 0xFF)
            val chk = ((buffer[offset + 6].toInt() and 0xFF) shl 8) or (buffer[offset + 7].toInt() and 0xFF)

            return UdpHeader(srcPort, dstPort, len, chk)
        }
    }
}

/**
 * Parsed representation of a TCP Header (RFC 793).
 */
data class TcpHeader(
    val srcPort: Int,
    val dstPort: Int,
    val seqNum: Long,
    val ackNum: Long,
    val dataOffsetBytes: Int,
    val flags: Int,
    val windowSize: Int
) {
    val isSyn: Boolean get() = (flags and 0x02) != 0
    val isAck: Boolean get() = (flags and 0x10) != 0
    val isPsh: Boolean get() = (flags and 0x08) != 0
    val isRst: Boolean get() = (flags and 0x04) != 0
    val isFin: Boolean get() = (flags and 0x01) != 0

    companion object {
        const val FLAG_FIN = 0x01
        const val FLAG_SYN = 0x02
        const val FLAG_RST = 0x04
        const val FLAG_PSH = 0x08
        const val FLAG_ACK = 0x10
        const val FLAG_URG = 0x20

        fun parse(buffer: ByteArray, offset: Int, length: Int): TcpHeader? {
            if (length < 20) return null
            val srcPort = ((buffer[offset].toInt() and 0xFF) shl 8) or (buffer[offset + 1].toInt() and 0xFF)
            val dstPort = ((buffer[offset + 2].toInt() and 0xFF) shl 8) or (buffer[offset + 3].toInt() and 0xFF)

            val seq = (((buffer[offset + 4].toLong() and 0xFF) shl 24) or
                    ((buffer[offset + 5].toLong() and 0xFF) shl 16) or
                    ((buffer[offset + 6].toLong() and 0xFF) shl 8) or
                    (buffer[offset + 7].toLong() and 0xFF))

            val ack = (((buffer[offset + 8].toLong() and 0xFF) shl 24) or
                    ((buffer[offset + 9].toLong() and 0xFF) shl 16) or
                    ((buffer[offset + 10].toLong() and 0xFF) shl 8) or
                    (buffer[offset + 11].toLong() and 0xFF))

            val dataOffset = (buffer[offset + 12].toInt() and 0xF0) ushr 4
            val flags = buffer[offset + 13].toInt() and 0xFF
            val win = ((buffer[offset + 14].toInt() and 0xFF) shl 8) or (buffer[offset + 15].toInt() and 0xFF)

            return TcpHeader(
                srcPort = srcPort,
                dstPort = dstPort,
                seqNum = seq,
                ackNum = ack,
                dataOffsetBytes = dataOffset * 4,
                flags = flags,
                windowSize = win
            )
        }

        /**
         * Builds an IPv4 + TCP packet byte array with calculated checksums.
         */
        fun buildIpv4TcpPacket(
            srcIp: ByteArray,
            dstIp: ByteArray,
            srcPort: Int,
            dstPort: Int,
            seqNum: Long,
            ackNum: Long,
            flags: Int,
            windowSize: Int = 65535,
            payload: ByteArray? = null,
            payloadOffset: Int = 0,
            payloadLength: Int = payload?.size ?: 0
        ): ByteArray {
            val tcpHeaderLen = 20
            val totalTcpLen = tcpHeaderLen + payloadLength
            val ipHeaderLen = 20
            val totalIpLen = ipHeaderLen + totalTcpLen

            val packet = ByteArray(totalIpLen)
            val bb = ByteBuffer.wrap(packet)

            // 1. Construct IPv4 Header
            bb.put(0x45.toByte()) // Version 4, IHL 5
            bb.put(0x00.toByte()) // DSCP/ECN
            bb.putShort(totalIpLen.toShort()) // Total Length
            bb.putShort((System.currentTimeMillis() and 0xFFFF).toShort()) // Identification
            bb.putShort(0x4000.toShort()) // Don't Fragment flag
            bb.put(64.toByte()) // TTL
            bb.put(6.toByte()) // Protocol TCP
            bb.putShort(0.toShort()) // Checksum placeholder
            bb.put(srcIp)
            bb.put(dstIp)

            // Calculate and set IP checksum
            val ipChecksum = ChecksumUtils.computeChecksum(packet, 0, ipHeaderLen)
            packet[10] = ((ipChecksum.toInt() ushr 8) and 0xFF).toByte()
            packet[11] = (ipChecksum.toInt() and 0xFF).toByte()

            // 2. Construct TCP Header
            bb.position(ipHeaderLen)
            bb.putShort(srcPort.toShort())
            bb.putShort(dstPort.toShort())
            bb.putInt(seqNum.toInt())
            bb.putInt(ackNum.toInt())
            bb.put((0x50).toByte()) // Data Offset (5 32-bit words = 20 bytes)
            bb.put(flags.toByte())
            bb.putShort(windowSize.toShort())
            bb.putShort(0.toShort()) // Checksum placeholder
            bb.putShort(0.toShort()) // Urgent pointer

            // Copy payload if any
            if (payload != null && payloadLength > 0) {
                bb.put(payload, payloadOffset, payloadLength)
            }

            // Calculate and set TCP checksum
            val tcpChecksum = ChecksumUtils.computeTcpChecksumV4(
                srcIp = srcIp,
                dstIp = dstIp,
                tcpSegment = packet,
                segmentOffset = ipHeaderLen,
                segmentLength = totalTcpLen
            )
            packet[ipHeaderLen + 16] = ((tcpChecksum.toInt() ushr 8) and 0xFF).toByte()
            packet[ipHeaderLen + 17] = (tcpChecksum.toInt() and 0xFF).toByte()

            return packet
        }

        /**
         * Builds an IPv4 + UDP packet byte array with calculated checksums.
         */
        fun buildIpv4UdpPacket(
            srcIp: ByteArray,
            dstIp: ByteArray,
            srcPort: Int,
            dstPort: Int,
            payload: ByteArray
        ): ByteArray {
            val udpHeaderLen = 8
            val totalUdpLen = udpHeaderLen + payload.size
            val ipHeaderLen = 20
            val totalIpLen = ipHeaderLen + totalUdpLen

            val packet = ByteArray(totalIpLen)
            val bb = ByteBuffer.wrap(packet)

            // IPv4 Header
            bb.put(0x45.toByte())
            bb.put(0x00.toByte())
            bb.putShort(totalIpLen.toShort())
            bb.putShort((System.currentTimeMillis() and 0xFFFF).toShort())
            bb.putShort(0x0000.toShort())
            bb.put(64.toByte())
            bb.put(17.toByte()) // Protocol UDP
            bb.putShort(0.toShort())
            bb.put(srcIp)
            bb.put(dstIp)

            val ipChecksum = ChecksumUtils.computeChecksum(packet, 0, ipHeaderLen)
            packet[10] = ((ipChecksum.toInt() ushr 8) and 0xFF).toByte()
            packet[11] = (ipChecksum.toInt() and 0xFF).toByte()

            // UDP Header
            bb.position(ipHeaderLen)
            bb.putShort(srcPort.toShort())
            bb.putShort(dstPort.toShort())
            bb.putShort(totalUdpLen.toShort())
            bb.putShort(0.toShort()) // Checksum placeholder
            bb.put(payload)

            val udpChecksum = ChecksumUtils.computeUdpChecksumV4(
                srcIp = srcIp,
                dstIp = dstIp,
                udpSegment = packet,
                segmentOffset = ipHeaderLen,
                segmentLength = totalUdpLen
            )
            packet[ipHeaderLen + 6] = ((udpChecksum.toInt() ushr 8) and 0xFF).toByte()
            packet[ipHeaderLen + 7] = (udpChecksum.toInt() and 0xFF).toByte()

            return packet
        }
    }
}
