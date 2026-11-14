package com.example.data.parser

import android.net.Uri
import android.util.Base64
import com.example.data.model.VpnConfig
import org.json.JSONObject
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

data class ParseResult(
    val isSuccess: Boolean,
    val config: VpnConfig? = null,
    val errorMessage: String? = null
)

object VpnConfigParser {

    private val linkRegex = Regex("""(?i)(vless://|vmess://|trojan://|ss://)[^\s"'<>]+""")

    fun parseMultiple(rawText: String, userId: String): List<ParseResult> {
        val matches = linkRegex.findAll(rawText).map { it.value.trim() }.toList()

        if (matches.isEmpty()) {
            val lines = rawText.lines().map { it.trim() }.filter { it.isNotBlank() }
            if (lines.isNotEmpty()) {
                val results = lines.map { parse(it, userId) }
                if (results.any { it.isSuccess }) return results
            }
            return listOf(ParseResult(false, null, "هیچ لینک یا کانفیگ معتبری در متن یافت نشد / No valid VPN links found in input"))
        }

        return matches.map { parse(it, userId) }
    }

    fun parse(rawUri: String, userId: String): ParseResult {
        var trimmed = rawUri.trim()
        if (trimmed.isBlank()) {
            return ParseResult(false, null, "پیکربندی خالی است / Configuration cannot be empty")
        }

        // If embedded in quotes or text, extract link
        val match = linkRegex.find(trimmed)
        if (match != null) {
            trimmed = match.value
        }

        return try {
            when {
                trimmed.startsWith("vless://", ignoreCase = true) -> parseVless(trimmed, userId)
                trimmed.startsWith("vmess://", ignoreCase = true) -> parseVmess(trimmed, userId)
                trimmed.startsWith("trojan://", ignoreCase = true) -> parseTrojan(trimmed, userId)
                trimmed.startsWith("ss://", ignoreCase = true) -> parseShadowsocks(trimmed, userId)
                trimmed.startsWith("{") && trimmed.endsWith("}") -> parseJsonConfig(trimmed, userId)
                else -> ParseResult(
                    false,
                    null,
                    "فرمت کانفیگ ناشناخته است. لطفاً لینک vless://, vmess://, trojan:// یا ss:// وارد نمایید"
                )
            }
        } catch (e: Exception) {
            ParseResult(false, null, "خطا در تجزیه پیکربندی: ${e.localizedMessage ?: "فرمت نامعتبر"} / Parse error")
        }
    }

    private fun parseJsonConfig(jsonStr: String, userId: String): ParseResult {
        val json = JSONObject(jsonStr)
        val server = json.optString("server", json.optString("server_address", json.optString("host", "")))
        val port = json.optInt("port", json.optInt("server_port", 443))
        val protocol = json.optString("protocol", json.optString("type", "vless")).lowercase()
        val name = json.optString("name", json.optString("tag", "$protocol-$server:$port"))
        val uuid = json.optString("uuid", json.optString("password", json.optString("id", "secret-uuid")))

        if (server.isBlank()) {
            return ParseResult(false, null, "فیلد server در ساختار JSON یافت نشد")
        }

        val config = VpnConfig(
            userId = userId,
            name = name,
            protocol = protocol,
            server = server,
            port = port,
            uuidOrPassword = uuid,
            transport = json.optString("transport", "tcp"),
            security = json.optString("security", "tls"),
            sni = json.optString("sni", server),
            rawConfig = jsonStr,
            status = "untested",
            pingMs = -1
        )
        return ParseResult(true, config)
    }

    private fun parseVless(uriStr: String, userId: String): ParseResult {
        val uri = Uri.parse(uriStr)
        val userInfo = uri.userInfo ?: uri.authority?.substringBefore("@") ?: ""
        if (userInfo.isBlank()) {
            return ParseResult(false, null, "شناسه UUID نامعتبر در VLESS / Missing UUID in VLESS")
        }

        val host = uri.host ?: uri.authority?.substringAfter("@")?.substringBefore(":") ?: ""
        val port = if (uri.port > 0) uri.port else uri.authority?.substringAfterLast(":")?.substringBefore("?")?.toIntOrNull() ?: 443
        if (host.isBlank() || port !in 1..65535) {
            return ParseResult(false, null, "آدرس هاست یا پورت نامعتبر است / Invalid host or port")
        }

        val name = if (!uri.fragment.isNullOrBlank()) {
            URLDecoder.decode(uri.fragment, StandardCharsets.UTF_8.name())
        } else {
            "VLESS-$host:$port"
        }

        val security = uri.getQueryParameter("security") ?: "tls"
        val transport = uri.getQueryParameter("type") ?: "tcp"
        val sni = uri.getQueryParameter("sni") ?: uri.getQueryParameter("peer") ?: host
        val path = uri.getQueryParameter("path") ?: uri.getQueryParameter("serviceName") ?: ""

        val config = VpnConfig(
            userId = userId,
            name = name,
            protocol = "vless",
            server = host,
            port = port,
            uuidOrPassword = userInfo,
            transport = transport,
            security = security,
            sni = sni,
            pathOrServiceName = path,
            rawConfig = uriStr,
            status = "untested",
            pingMs = -1
        )
        return ParseResult(true, config)
    }

    private fun parseVmess(uriStr: String, userId: String): ParseResult {
        val base64Content = uriStr.substringAfter("vmess://").trim()
        val decoded = try {
            String(Base64.decode(base64Content, Base64.DEFAULT), StandardCharsets.UTF_8)
        } catch (e: Exception) {
            return ParseResult(false, null, "فرمت Base64 در VMess معتبر نیست / Invalid VMess base64 payload")
        }

        val json = try {
            JSONObject(decoded)
        } catch (e: Exception) {
            return ParseResult(false, null, "داده JSON در VMess معتبر نیست / Invalid VMess JSON")
        }

        val host = json.optString("add", "")
        val portStr = json.optString("port", "")
        val port = portStr.toIntOrNull() ?: json.optInt("port", 443)
        val uuid = json.optString("id", "")
        val name = json.optString("ps", "VMess-$host:$port")
        val net = json.optString("net", "tcp")
        val tls = json.optString("tls", "none")
        val sni = json.optString("sni", host)
        val path = json.optString("path", "")

        if (host.isBlank() || port !in 1..65535 || uuid.isBlank()) {
            return ParseResult(false, null, "اطلاعات سرور یا UUID در VMess ناقص است / Missing server or UUID in VMess")
        }

        val config = VpnConfig(
            userId = userId,
            name = name,
            protocol = "vmess",
            server = host,
            port = port,
            uuidOrPassword = uuid,
            transport = net,
            security = if (tls == "tls") "tls" else "none",
            sni = sni,
            pathOrServiceName = path,
            rawConfig = uriStr,
            status = "untested",
            pingMs = -1
        )
        return ParseResult(true, config)
    }

    private fun parseTrojan(uriStr: String, userId: String): ParseResult {
        val uri = Uri.parse(uriStr)
        val password = uri.userInfo ?: uri.authority?.substringBefore("@") ?: ""
        val host = uri.host ?: uri.authority?.substringAfter("@")?.substringBefore(":") ?: ""
        val port = if (uri.port > 0) uri.port else uri.authority?.substringAfterLast(":")?.substringBefore("?")?.toIntOrNull() ?: 443

        if (password.isBlank() || host.isBlank() || port !in 1..65535) {
            return ParseResult(false, null, "رمز عبور یا سرور Trojan نامعتبر است / Invalid Trojan password or server")
        }

        val name = if (!uri.fragment.isNullOrBlank()) {
            URLDecoder.decode(uri.fragment, StandardCharsets.UTF_8.name())
        } else {
            "Trojan-$host:$port"
        }

        val sni = uri.getQueryParameter("sni") ?: host
        val transport = uri.getQueryParameter("type") ?: "tcp"
        val security = uri.getQueryParameter("security") ?: "tls"

        val config = VpnConfig(
            userId = userId,
            name = name,
            protocol = "trojan",
            server = host,
            port = port,
            uuidOrPassword = password,
            transport = transport,
            security = security,
            sni = sni,
            pathOrServiceName = uri.getQueryParameter("path") ?: "",
            rawConfig = uriStr,
            status = "untested",
            pingMs = -1
        )
        return ParseResult(true, config)
    }

    private fun parseShadowsocks(uriStr: String, userId: String): ParseResult {
        val afterScheme = uriStr.substringAfter("ss://").trim()
        val fragment = afterScheme.substringAfter("#", "")
        val mainPart = afterScheme.substringBefore("#")

        val name = if (fragment.isNotBlank()) {
            try {
                URLDecoder.decode(fragment, StandardCharsets.UTF_8.name())
            } catch (e: Exception) {
                "Shadowsocks Server"
            }
        } else {
            "Shadowsocks Server"
        }

        val (userInfo, server, port) = if (mainPart.contains("@")) {
            val userPart = mainPart.substringBefore("@")
            val hostPart = mainPart.substringAfter("@")
            val decodedUser = try {
                String(Base64.decode(userPart, Base64.URL_SAFE or Base64.NO_PADDING or Base64.DEFAULT), StandardCharsets.UTF_8)
            } catch (e: Exception) {
                userPart
            }
            val h = hostPart.substringBefore(":")
            val p = hostPart.substringAfter(":").toIntOrNull() ?: 8388
            Triple(decodedUser, h, p)
        } else {
            val decoded = try {
                String(Base64.decode(mainPart, Base64.URL_SAFE or Base64.NO_PADDING or Base64.DEFAULT), StandardCharsets.UTF_8)
            } catch (e: Exception) {
                return ParseResult(false, null, "کدگذاری نامعتبر Shadowsocks / Invalid Shadowsocks base64")
            }
            val u = decoded.substringBefore("@")
            val h = decoded.substringAfter("@").substringBefore(":")
            val p = decoded.substringAfterLast(":").toIntOrNull() ?: 8388
            Triple(u, h, p)
        }

        if (server.isBlank() || port !in 1..65535) {
            return ParseResult(false, null, "مشخصات هاست و پورت Shadowsocks نامعتبر است / Invalid Shadowsocks address")
        }

        val config = VpnConfig(
            userId = userId,
            name = name,
            protocol = "shadowsocks",
            server = server,
            port = port,
            uuidOrPassword = userInfo,
            transport = "tcp",
            security = "none",
            rawConfig = uriStr,
            status = "untested",
            pingMs = -1
        )
        return ParseResult(true, config)
    }

    fun isDuplicate(newConfig: VpnConfig, existingConfigs: List<VpnConfig>): Boolean {
        return existingConfigs.any { existing ->
            existing.protocol.equals(newConfig.protocol, ignoreCase = true) &&
            existing.server.equals(newConfig.server, ignoreCase = true) &&
            existing.port == newConfig.port &&
            existing.uuidOrPassword == newConfig.uuidOrPassword
        }
    }
}
