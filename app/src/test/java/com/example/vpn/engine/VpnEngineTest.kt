package com.example.vpn.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetAddress

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VpnEngineTest {

    @Test
    fun testChecksumCalculation() {
        val testBytes = byteArrayOf(0x45, 0x00, 0x00, 0x3c, 0x1c, 0x46, 0x40, 0x00, 0x40, 0x06)
        val checksum = ChecksumUtils.computeChecksum(testBytes, 0, testBytes.size)
        // Checksum should be non-zero
        assertTrue(checksum.toInt() != 0)
    }

    @Test
    fun testIpv4TcpPacketBuildAndParse() {
        val srcIp = byteArrayOf(10, 8, 0, 2)
        val dstIp = byteArrayOf(1, 1, 1, 1)
        val srcPort = 54321
        val dstPort = 443
        val seqNum = 1000L
        val ackNum = 2000L
        val payload = "GET / HTTP/1.1\r\nHost: example.com\r\n\r\n".toByteArray(Charsets.UTF_8)

        val packet = TcpHeader.buildIpv4TcpPacket(
            srcIp = srcIp,
            dstIp = dstIp,
            srcPort = srcPort,
            dstPort = dstPort,
            seqNum = seqNum,
            ackNum = ackNum,
            flags = TcpHeader.FLAG_PSH or TcpHeader.FLAG_ACK,
            windowSize = 65535,
            payload = payload
        )

        assertNotNull(packet)
        assertTrue(packet.size >= 40 + payload.size)

        // Parse back the IPv4 header
        val parsedIp = Ipv4Header.parse(packet, 0, packet.size)
        assertNotNull(parsedIp)
        assertEquals(4, parsedIp?.version)
        assertEquals(6, parsedIp?.protocol) // TCP
        assertEquals("10.8.0.2", parsedIp?.srcIpStr)
        assertEquals("1.1.1.1", parsedIp?.dstIpStr)

        // Parse back the TCP header
        val tcpHeaderOffset = parsedIp!!.headerLengthBytes
        val parsedTcp = TcpHeader.parse(packet, tcpHeaderOffset, packet.size - tcpHeaderOffset)
        assertNotNull(parsedTcp)
        assertEquals(54321, parsedTcp?.srcPort)
        assertEquals(443, parsedTcp?.dstPort)
        assertEquals(1000L, parsedTcp?.seqNum)
        assertEquals(2000L, parsedTcp?.ackNum)
        assertTrue(parsedTcp?.isPsh == true)
        assertTrue(parsedTcp?.isAck == true)
    }

    @Test
    fun testIpv4UdpDnsPacketBuildAndParse() {
        val srcIp = byteArrayOf(10, 8, 0, 2)
        val dstIp = byteArrayOf(8, 8, 8, 8)
        val srcPort = 49152
        val dstPort = 53
        val dnsQuery = byteArrayOf(0x12, 0x34, 0x01, 0x00, 0x00, 0x01, 0x00, 0x00)

        val packet = TcpHeader.buildIpv4UdpPacket(
            srcIp = srcIp,
            dstIp = dstIp,
            srcPort = srcPort,
            dstPort = dstPort,
            payload = dnsQuery
        )

        assertNotNull(packet)
        val parsedIp = Ipv4Header.parse(packet, 0, packet.size)
        assertNotNull(parsedIp)
        assertEquals(17, parsedIp?.protocol) // UDP

        val udpOffset = parsedIp!!.headerLengthBytes
        val parsedUdp = UdpHeader.parse(packet, udpOffset, packet.size - udpOffset)
        assertNotNull(parsedUdp)
        assertEquals(49152, parsedUdp?.srcPort)
        assertEquals(53, parsedUdp?.dstPort)
        assertEquals(8 + dnsQuery.size, parsedUdp?.length)
    }

    @Test
    fun testTrojanHandshakeGeneration() {
        val password = "my_trojan_secret"
        val handshake = ProxyHandshakeHelper.buildTrojanHandshake(
            password = password,
            targetHost = "instagram.com",
            targetPort = 443
        )

        assertTrue(handshake.isNotEmpty())
        // Trojan starts with 56-character SHA-224 hex string
        val hexPrefix = String(handshake.copyOfRange(0, 56), Charsets.US_ASCII)
        assertEquals(56, hexPrefix.length)
        assertTrue(hexPrefix.all { it in "0123456789abcdef" })

        // Followed by CRLF (0x0D, 0x0A)
        assertEquals(0x0D.toByte(), handshake[56])
        assertEquals(0x0A.toByte(), handshake[57])

        // Command byte: 0x01 (TCP Connect)
        assertEquals(0x01.toByte(), handshake[58])
    }

    @Test
    fun testVlessHandshakeGeneration() {
        val uuid = "12345678-1234-1234-1234-123456789abc"
        val handshake = ProxyHandshakeHelper.buildVlessHandshake(
            uuidString = uuid,
            targetHost = "telegram.org",
            targetPort = 443
        )

        assertTrue(handshake.isNotEmpty())
        // VLESS version 0x00
        assertEquals(0x00.toByte(), handshake[0])
        // UUID is 16 bytes: indices 1..16
        // Command 0x01 (TCP)
        assertEquals(0x01.toByte(), handshake[18])
    }

    @Test
    fun testParsedVpnConfigWithSniAndTransport() {
        val vlessUrl = "vless://a1b2c3d4-e5f6-7890-abcd-ef1234567890@de.server.net:443?security=reality&sni=yahoo.com&type=ws&path=/live#Germany-Node"
        val parsed = ParsedVpnConfig.from(vlessUrl)

        assertEquals("de.server.net", parsed.server)
        assertEquals(443, parsed.port)
        assertEquals("vless", parsed.protocol)
        assertEquals("a1b2c3d4-e5f6-7890-abcd-ef1234567890", parsed.uuidOrPassword)
        assertEquals("reality", parsed.security)
        assertEquals("yahoo.com", parsed.sni)
        assertEquals("ws", parsed.transport)
        assertEquals("/live", parsed.path)
    }

    @Test
    fun testAntiDpiFragmentationWrites() {
        val out = java.io.ByteArrayOutputStream()
        val tlsClientHello = byteArrayOf(
            0x16, 0x03, 0x01, 0x00, 0x10, // TLS 1.2 record header (5 bytes)
            0x01, 0x00, 0x00, 0x0C, // Handshake ClientHello
            0x03, 0x03, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        )

        ProxyHandshakeHelper.writeWithAntiDpiFragmentation(
            outputStream = out,
            buffer = tlsClientHello,
            offset = 0,
            length = tlsClientHello.size,
            antiFilterMode = true
        )

        val written = out.toByteArray()
        // Total bytes written must match original length
        assertEquals(tlsClientHello.size, written.size)
        // First byte is 0x16
        assertEquals(0x16.toByte(), written[0])
    }

    @Test
    fun testNationalIntranetPiercerTransformation() {
        val foreignConfig = ParsedVpnConfig(
            server = "de.foreign-server.com",
            port = 443,
            protocol = "vless",
            uuidOrPassword = "my-secret-uuid",
            sni = "de.foreign-server.com"
        )

        // Transform using default emergency domestic relays
        val transformed = NationalIntranetPiercer.transformForIntranetPiercing(foreignConfig)
        assertTrue(transformed.server.isNotBlank())
        assertEquals("aparat.com", transformed.sni)
        assertEquals("aparat.com", transformed.hostHeader)
        assertEquals("ws", transformed.transport)
        assertEquals("tls", transformed.security)

        // Transform using custom Iran domestic relay
        val customTransformed = NationalIntranetPiercer.transformForIntranetPiercing(
            originalConfig = foreignConfig,
            customIranRelay = "185.143.232.10"
        )
        assertEquals("185.143.232.10", customTransformed.server)
        assertEquals("aparat.com", customTransformed.sni)
    }
}
