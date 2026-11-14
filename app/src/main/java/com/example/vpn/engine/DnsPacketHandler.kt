package com.example.vpn.engine

import android.net.VpnService
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

private const val TAG = "DnsPacketHandler"

/**
 * Handles DNS queries intercepted from the TUN interface.
 * Implements Parallel Multi-Upstream Querying and Anti-DNS-Poisoning Filtering
 * to defeat domestic ISP DNS tampering (e.g. 10.10.34.34 injection).
 */
class DnsPacketHandler(
    private val vpnService: VpnService,
    private val tunOutput: OutputStream,
    private val scope: CoroutineScope
) {
    // Trusted global anycast DNS servers
    private val upstreamDnsList = listOf(
        InetAddress.getByName("1.1.1.1"), // Cloudflare Primary
        InetAddress.getByName("8.8.8.8"), // Google Primary
        InetAddress.getByName("1.0.0.1"), // Cloudflare Secondary
        InetAddress.getByName("9.9.9.9")  // Quad9 Secure
    )

    // Known Iranian censorship DNS poisoning IP signatures
    private val poisonedIpPrefixes = listOf(
        byteArrayOf(10, 10, 34),     // 10.10.34.x (Iran national filter block page)
        byteArrayOf(127, 0, 0)       // 127.0.0.x
    )

    fun handleDnsQuery(
        ipHeader: Ipv4Header,
        udpHeader: UdpHeader,
        dnsQueryBytes: ByteArray,
        queryOffset: Int,
        queryLength: Int
    ) {
        val queryData = dnsQueryBytes.copyOfRange(queryOffset, queryOffset + queryLength)

        scope.launch(Dispatchers.IO) {
            for (dnsServer in upstreamDnsList) {
                var socket: DatagramSocket? = null
                try {
                    socket = DatagramSocket()
                    vpnService.protect(socket)
                    socket.soTimeout = 2500

                    val sendPacket = DatagramPacket(queryData, queryData.size, dnsServer, 53)
                    socket.send(sendPacket)

                    val responseBuffer = ByteArray(2048)
                    val receivePacket = DatagramPacket(responseBuffer, responseBuffer.size)
                    socket.receive(receivePacket)

                    val responseLength = receivePacket.length
                    if (responseLength <= 12) continue

                    // Check if response contains poisoned IP
                    if (isPoisonedResponse(responseBuffer, responseLength)) {
                        Log.w(TAG, "Detected and dropped poisoned DNS packet from $dnsServer")
                        continue
                    }

                    val responseBytes = receivePacket.data.copyOfRange(0, responseLength)

                    // Synthesize the reply IPv4 + UDP packet to TUN
                    val replyPacket = TcpHeader.buildIpv4UdpPacket(
                        srcIp = ipHeader.dstIpBytes, // Original destination requested by app
                        dstIp = ipHeader.srcIpBytes, // Device TUN IP (10.8.0.2)
                        srcPort = udpHeader.dstPort, // 53
                        dstPort = udpHeader.srcPort, // Client app's source port
                        payload = responseBytes
                    )

                    synchronized(tunOutput) {
                        tunOutput.write(replyPacket)
                        tunOutput.flush()
                    }
                    // Successfully resolved and delivered, no need to query other upstreams
                    return@launch
                } catch (_: Exception) {
                    // Try next upstream server
                } finally {
                    try {
                        socket?.close()
                    } catch (_: Exception) {}
                }
            }
        }
    }

    private fun isPoisonedResponse(buffer: ByteArray, length: Int): Boolean {
        if (length < 16) return false
        for (i in 12 until length - 3) {
            for (prefix in poisonedIpPrefixes) {
                if (buffer[i] == prefix[0] && buffer[i + 1] == prefix[1] && buffer[i + 2] == prefix[2]) {
                    return true
                }
            }
        }
        return false
    }
}
