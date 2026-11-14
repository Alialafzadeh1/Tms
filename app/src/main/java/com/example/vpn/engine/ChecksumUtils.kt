package com.example.vpn.engine

/**
 * Internet Checksum (RFC 1071) calculation utilities for IPv4, UDP, and TCP headers.
 */
object ChecksumUtils {

    /**
     * Calculates the standard 16-bit one's complement Internet Checksum.
     */
    fun computeChecksum(buffer: ByteArray, offset: Int, length: Int): Short {
        var sum = 0L
        var idx = offset
        var remaining = length

        while (remaining > 1) {
            sum += ((buffer[idx].toInt() and 0xFF) shl 8) or (buffer[idx + 1].toInt() and 0xFF)
            idx += 2
            remaining -= 2
        }

        if (remaining == 1) {
            sum += (buffer[idx].toInt() and 0xFF) shl 8
        }

        while ((sum ushr 16) > 0) {
            sum = (sum and 0xFFFFL) + (sum ushr 16)
        }

        return (sum.inv() and 0xFFFFL).toShort()
    }

    /**
     * Calculates the TCP checksum using IPv4 Pseudo-Header.
     */
    fun computeTcpChecksumV4(
        srcIp: ByteArray,
        dstIp: ByteArray,
        tcpSegment: ByteArray,
        segmentOffset: Int,
        segmentLength: Int
    ): Short {
        var sum = 0L

        // Pseudo-Header: Source IP (4 bytes)
        sum += ((srcIp[0].toInt() and 0xFF) shl 8) or (srcIp[1].toInt() and 0xFF)
        sum += ((srcIp[2].toInt() and 0xFF) shl 8) or (srcIp[3].toInt() and 0xFF)

        // Pseudo-Header: Destination IP (4 bytes)
        sum += ((dstIp[0].toInt() and 0xFF) shl 8) or (dstIp[1].toInt() and 0xFF)
        sum += ((dstIp[2].toInt() and 0xFF) shl 8) or (dstIp[3].toInt() and 0xFF)

        // Pseudo-Header: Protocol (6 for TCP)
        sum += 6

        // Pseudo-Header: TCP Segment Length
        sum += segmentLength

        // TCP Segment Data (Checksum field must be zeroed out)
        var idx = segmentOffset
        var remaining = segmentLength

        while (remaining > 1) {
            sum += ((tcpSegment[idx].toInt() and 0xFF) shl 8) or (tcpSegment[idx + 1].toInt() and 0xFF)
            idx += 2
            remaining -= 2
        }

        if (remaining == 1) {
            sum += (tcpSegment[idx].toInt() and 0xFF) shl 8
        }

        while ((sum ushr 16) > 0) {
            sum = (sum and 0xFFFFL) + (sum ushr 16)
        }

        return (sum.inv() and 0xFFFFL).toShort()
    }

    /**
     * Calculates the UDP checksum using IPv4 Pseudo-Header.
     */
    fun computeUdpChecksumV4(
        srcIp: ByteArray,
        dstIp: ByteArray,
        udpSegment: ByteArray,
        segmentOffset: Int,
        segmentLength: Int
    ): Short {
        var sum = 0L

        // Pseudo-Header: Source IP (4 bytes)
        sum += ((srcIp[0].toInt() and 0xFF) shl 8) or (srcIp[1].toInt() and 0xFF)
        sum += ((srcIp[2].toInt() and 0xFF) shl 8) or (srcIp[3].toInt() and 0xFF)

        // Pseudo-Header: Destination IP (4 bytes)
        sum += ((dstIp[0].toInt() and 0xFF) shl 8) or (dstIp[1].toInt() and 0xFF)
        sum += ((dstIp[2].toInt() and 0xFF) shl 8) or (dstIp[3].toInt() and 0xFF)

        // Pseudo-Header: Protocol (17 for UDP)
        sum += 17

        // Pseudo-Header: UDP Length
        sum += segmentLength

        // UDP Segment Data
        var idx = segmentOffset
        var remaining = segmentLength

        while (remaining > 1) {
            sum += ((udpSegment[idx].toInt() and 0xFF) shl 8) or (udpSegment[idx + 1].toInt() and 0xFF)
            idx += 2
            remaining -= 2
        }

        if (remaining == 1) {
            sum += (udpSegment[idx].toInt() and 0xFF) shl 8
        }

        while ((sum ushr 16) > 0) {
            sum = (sum and 0xFFFFL) + (sum ushr 16)
        }

        val res = (sum.inv() and 0xFFFFL).toShort()
        return if (res == 0.toShort()) 0xFFFF.toShort() else res
    }
}
