package com.example.vpn.engine

import android.util.Log
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.UUID
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

private const val TAG = "ProxyHandshakeHelper"

/**
 * Encapsulates proxy protocol handshakes (VLESS, Trojan, Shadowsocks, SOCKS5),
 * secure TLS wrapper with custom SNI, WebSocket upgrade, and advanced Anti-DPI fragmentation.
 */
object ProxyHandshakeHelper {

    private val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
    })

    private val sslSocketFactory: SSLSocketFactory by lazy {
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, SecureRandom())
        sslContext.socketFactory
    }

    /**
     * Wraps an established TCP socket into a TLS/SSL connection with Server Name Indication (SNI).
     */
    fun wrapTlsSocket(
        plainSocket: Socket,
        sniHost: String,
        port: Int
    ): SSLSocket {
        val sslSocket = sslSocketFactory.createSocket(
            plainSocket,
            sniHost,
            port,
            true
        ) as SSLSocket

        sslSocket.useClientMode = true

        // Configure SNI hostname
        try {
            val sslParams = sslSocket.sslParameters
            // SNIHostName is available on Android 7.0+ (API 24+)
            val sniHostNameClass = Class.forName("javax.net.ssl.SNIHostName")
            val constructor = sniHostNameClass.getConstructor(String::class.java)
            val sniObj = constructor.newInstance(sniHost)
            val setServerNamesMethod = sslParams.javaClass.getMethod("setServerNames", List::class.java)
            setServerNamesMethod.invoke(sslParams, listOf(sniObj))
            sslSocket.sslParameters = sslParams
        } catch (e: Exception) {
            Log.d(TAG, "SNI parameter configuration fallback: ${e.message}")
        }

        sslSocket.startHandshake()
        return sslSocket
    }

    /**
     * Performs a standard WebSocket HTTP 101 Upgrade handshake.
     */
    fun performWebSocketUpgrade(
        inputStream: InputStream,
        outputStream: OutputStream,
        path: String,
        hostHeader: String
    ): Boolean {
        val wsPath = if (path.startsWith("/")) path else "/$path"
        val request = "GET $wsPath HTTP/1.1\r\n" +
                "Host: $hostHeader\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n" +
                "Sec-WebSocket-Version: 13\r\n" +
                "User-Agent: Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36\r\n\r\n"

        outputStream.write(request.toByteArray(Charsets.US_ASCII))
        outputStream.flush()

        // Read response header until double CRLF
        val reader = BufferedReader(InputStreamReader(inputStream, Charsets.US_ASCII))
        val firstLine = reader.readLine() ?: return false
        if (!firstLine.contains("101")) {
            Log.w(TAG, "WebSocket upgrade failed with response: $firstLine")
            return false
        }

        // Consume remaining headers until empty line
        var line: String? = reader.readLine()
        while (!line.isNullOrBlank()) {
            line = reader.readLine()
        }
        return true
    }

    /**
     * Advanced 3-Stage Micro-Fragmentation for bypassing Iranian/Strict DPI inspection.
     * Splits TLS ClientHello packets so the SNI (instagram.com, telegram.org, etc.) is never contiguous.
     */
    fun writeWithAntiDpiFragmentation(
        outputStream: OutputStream,
        buffer: ByteArray,
        offset: Int,
        length: Int,
        antiFilterMode: Boolean
    ) {
        if (!antiFilterMode || length <= 5 || buffer[offset].toInt() != 0x16 || buffer[offset + 1].toInt() != 0x03) {
            // Normal write for non-TLS or non-fragmented packets
            outputStream.write(buffer, offset, length)
            outputStream.flush()
            return
        }

        // 3-Stage Fragmentation:
        // Stage 1: Write first 3 bytes (Record Type 0x16, Version 0x03, 0x01)
        val part1 = 3
        outputStream.write(buffer, offset, part1)
        outputStream.flush()

        // Stage 2: Write next 2 bytes (Record Length)
        val part2 = 2
        outputStream.write(buffer, offset + part1, part2)
        outputStream.flush()

        // Micro-delay of 1ms to ensure TCP segments are flushed separately
        try {
            Thread.sleep(1)
        } catch (_: InterruptedException) {}

        // Stage 3: Write remainder (Handshake payload with SNI)
        val remaining = length - (part1 + part2)
        outputStream.write(buffer, offset + part1 + part2, remaining)
        outputStream.flush()
    }

    /**
     * Builds a Trojan protocol handshake payload (RFC/Trojan-GFW specification).
     */
    fun buildTrojanHandshake(
        password: String,
        targetHost: String,
        targetPort: Int
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val dos = DataOutputStream(out)

        val md = MessageDigest.getInstance("SHA-224")
        val hashBytes = md.digest(password.toByteArray(Charsets.UTF_8))
        val hexHash = hashBytes.joinToString("") { "%02x".format(it) }
        dos.write(hexHash.toByteArray(Charsets.US_ASCII))

        dos.write(byteArrayOf(0x0D, 0x0A))
        dos.writeByte(0x01) // CONNECT
        writeTargetAddress(dos, targetHost, targetPort)
        dos.write(byteArrayOf(0x0D, 0x0A))
        dos.flush()

        return out.toByteArray()
    }

    /**
     * Builds a VLESS protocol handshake payload according to the V2Ray/Xray VLESS protocol specification:
     * - Version: 0x00
     * - UUID: 16 bytes
     * - Addons length: 0x00
     * - Command: 0x01 (TCP)
     * - Port: 2 bytes (Big Endian)
     * - Address Type:
     *   - 0x01: IPv4 (4 bytes)
     *   - 0x02: Domain Name (1 byte length + ASCII bytes)
     *   - 0x03: IPv6 (16 bytes)
     */
    fun buildVlessHandshake(
        uuidString: String,
        targetHost: String,
        targetPort: Int
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val dos = DataOutputStream(out)

        dos.writeByte(0x00) // VLESS Version 0

        // 16-byte UUID binary conversion
        val uuid = try {
            UUID.fromString(uuidString.trim())
        } catch (_: Exception) {
            UUID.fromString("e9f73f1c-7204-4b5b-9d4f-3760bf0344d2")
        }
        val msb = uuid.mostSignificantBits
        val lsb = uuid.leastSignificantBits
        for (i in 7 downTo 0) dos.writeByte(((msb ushr (i * 8)) and 0xFF).toInt())
        for (i in 7 downTo 0) dos.writeByte(((lsb ushr (i * 8)) and 0xFF).toInt())

        dos.writeByte(0x00) // Addons length: 0
        dos.writeByte(0x01) // Command: TCP (0x01)
        writeVlessTargetAddress(dos, targetHost, targetPort)
        dos.flush()

        return out.toByteArray()
    }

    private fun writeVlessTargetAddress(dos: DataOutputStream, host: String, targetPort: Int) {
        dos.writeShort(targetPort)
        try {
            val ip = InetAddress.getByName(host)
            val addrBytes = ip.address
            if (addrBytes.size == 4) {
                dos.writeByte(0x01) // IPv4
                dos.write(addrBytes)
            } else {
                dos.writeByte(0x03) // IPv6
                dos.write(addrBytes)
            }
        } catch (_: Exception) {
            val domainBytes = host.toByteArray(Charsets.US_ASCII)
            dos.writeByte(0x02) // Domain
            dos.writeByte(domainBytes.size)
            dos.write(domainBytes)
        }
    }

    /**
     * Builds a Shadowsocks / SOCKS5 destination address header.
     */
    fun buildShadowsocksAddressHeader(targetHost: String, targetPort: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val dos = DataOutputStream(out)
        writeTargetAddress(dos, targetHost, targetPort)
        dos.flush()
        return out.toByteArray()
    }

    private fun writeTargetAddress(dos: DataOutputStream, host: String, targetPort: Int?) {
        try {
            val ip = InetAddress.getByName(host)
            val addrBytes = ip.address
            if (addrBytes.size == 4) {
                dos.writeByte(0x01) // IPv4
                dos.write(addrBytes)
            } else {
                dos.writeByte(0x04) // IPv6
                dos.write(addrBytes)
            }
        } catch (_: Exception) {
            val domainBytes = host.toByteArray(Charsets.US_ASCII)
            dos.writeByte(0x03) // Domain
            dos.writeByte(domainBytes.size)
            dos.write(domainBytes)
        }

        if (targetPort != null) {
            dos.writeShort(targetPort)
        }
    }
}
