package com.example.vpn.engine

import android.content.Context
import android.net.VpnService
import android.os.Build
import android.system.Os
import android.system.OsConstants
import android.util.Base64
import android.util.Log
import com.example.data.model.VpnConfig
import go.Seq
import libXray.DialerController
import libXray.LibXray
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "XrayCoreEngine"

enum class XrayEngineMode(val labelFa: String, val labelEn: String) {
    NATIVE_LIBXRAY("هسته بومی Xray (TCP + UDP)", "Xray Core Native (TCP + UDP)"),
    KOTLIN_ROUTER("روتر تونل کاتلین (TCP + DNS)", "Kotlin TUN Router (TCP + DNS)")
}

data class XrayStartResult(
    val startedNative: Boolean,
    val mode: XrayEngineMode,
    val xrayVersion: String = "",
    val socksPort: Int = XrayCoreEngine.LOCAL_SOCKS_PORT,
    val message: String = ""
)

/**
 * Generates full Xray-Core JSON configurations (VLESS Reality/TLS/WS/gRPC/XHTTP,
 * VMess, Trojan, Shadowsocks) and manages the native libXray runtime lifecycle.
 */
object XrayCoreEngine {

    const val LOCAL_SOCKS_PORT = 10808
    const val LOCAL_HTTP_PORT = 10809

    @Volatile
    private var nativeSupported: Boolean = true

    @Volatile
    var activeMode: XrayEngineMode = XrayEngineMode.KOTLIN_ROUTER
        private set

    private val assetsPrepared = AtomicBoolean(false)
    private val preloadLock = Any()
    private val nativeJniLock = Any()
    private val nativeStdIoRestored = AtomicBoolean(false)

    @Volatile
    private var preloadTask: Deferred<Boolean>? = null

    fun isAssetsPrepared(): Boolean = assetsPrepared.get()

    internal fun ensureNativeRuntimeInitialized(context: Context): Boolean {
        if (!nativeSupported) return false
        return synchronized(nativeJniLock) {
            if (!nativeSupported) return@synchronized false
            try {
                Seq.setContext(context.applicationContext)
                LibXray.touch()
                restoreNativeStdIoToDevNullIfAndroid()
                true
            } catch (_: UnsatisfiedLinkError) {
                nativeSupported = false
                false
            } catch (_: Throwable) {
                false
            }
        }
    }

    private fun restoreNativeStdIoToDevNullIfAndroid() {
        if (!nativeStdIoRestored.compareAndSet(false, true)) return
        if ("robolectric".equals(Build.FINGERPRINT, ignoreCase = true)) return
        try {
            val devNull = Os.open("/dev/null", OsConstants.O_WRONLY, 0)
            try {
                Os.dup2(devNull, OsConstants.STDOUT_FILENO)
                Os.dup2(devNull, OsConstants.STDERR_FILENO)
            } finally {
                Os.close(devNull)
            }
        } catch (t: Throwable) {
            Log.d(TAG, "Native stdio restore skipped: ${t.message}")
        }
    }

    /**
     * Non-blocking background initialization of Xray native runtime & asset directory
     * (creates xray_assets, extracts any bundled .dat geo files, and warms up LibXray).
     */
    fun preloadNativeAssetsAsync(
        context: Context,
        scope: CoroutineScope
    ): Deferred<Boolean> {
        if (assetsPrepared.get()) {
            return CompletableDeferred(nativeSupported)
        }
        synchronized(preloadLock) {
            preloadTask?.let { existing ->
                if (!existing.isCancelled) return existing
            }
            val appCtx = context.applicationContext
            val job = scope.async(Dispatchers.IO) {
                prepareNativeAssetsInternal(appCtx)
            }
            preloadTask = job
            return job
        }
    }

    private fun prepareNativeAssetsInternal(appContext: Context): Boolean {
        val datDir = File(appContext.filesDir, "xray_assets").apply { mkdirs() }
        // Copy any geoip/geosite .dat files from APK assets if bundled, without blocking main thread
        runCatching {
            val assetList = appContext.assets.list("") ?: emptyArray()
            for (name in assetList) {
                if (name.endsWith(".dat", ignoreCase = true)) {
                    val targetFile = File(datDir, name)
                    if (!targetFile.exists() || targetFile.length() == 0L) {
                        PacketBufferPool.useBuffer(PacketBufferPool.MEDIUM_CAPACITY) { copyBuf ->
                            appContext.assets.open(name).use { input ->
                                FileOutputStream(targetFile).use { output ->
                                    while (true) {
                                        val read = input.read(copyBuf)
                                        if (read <= 0) break
                                        output.write(copyBuf, 0, read)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        val jniLoaded = ensureNativeRuntimeInitialized(appContext)

        assetsPrepared.set(true)
        return jniLoaded
    }

    fun buildXrayJson(
        parsed: ParsedVpnConfig,
        rawConfig: VpnConfig? = null,
        tunFd: Int = -1,
        enableFragment: Boolean = true,
        socksPort: Int = LOCAL_SOCKS_PORT,
        httpPort: Int = LOCAL_HTTP_PORT,
        fragmentProfile: FragmentProfile = FragmentProfile(),
        cleanIpOverride: String? = null,
        enableDoH: Boolean = false,
        enableFakeDns: Boolean = false,
        alpnOverride: List<String>? = null
    ): String {
        val root = JSONObject()

        if (tunFd > 0) {
            root.put("env", JSONObject().apply {
                put("xray.tun.fd", tunFd.toString())
            })
        }

        root.put("log", JSONObject().apply {
            put("loglevel", "warning")
        })

        if (enableFakeDns) {
            root.put("fakedns", JSONArray().apply {
                put(JSONObject().apply {
                    put("ipPool", "198.18.0.0/15")
                    put("poolSize", 65535)
                })
            })
        }

        // DNS configuration: DoH + FakeDNS in Combo mode, standard resolvers otherwise
        root.put("dns", JSONObject().apply {
            put("servers", JSONArray().apply {
                if (enableFakeDns) {
                    put("fakedns")
                }
                if (enableDoH) {
                    put("https+local://1.1.1.1/dns-query")
                    put("https+local://8.8.8.8/dns-query")
                }
                put("1.1.1.1")
                put("8.8.8.8")
                put("9.9.9.9")
            })
            put("queryStrategy", "UseIP")
        })

        // Inbounds: TUN (when fd provided) + local SOCKS5 & HTTP proxies for probing/health checks
        val inbounds = JSONArray()
        if (tunFd > 0) {
            inbounds.put(JSONObject().apply {
                put("tag", "tun-in")
                put("port", 0)
                put("protocol", "tun")
                put("settings", JSONObject().apply {
                    put("name", "xray0")
                    put("MTU", 1500)
                })
                put("sniffing", JSONObject().apply {
                    put("enabled", true)
                    put("destOverride", JSONArray().apply {
                        put("http")
                        put("tls")
                        put("quic")
                        if (enableFakeDns) put("fakedns")
                    })
                    put("routeOnly", false)
                })
            })
        }

        inbounds.put(JSONObject().apply {
            put("tag", "socks-in")
            put("listen", "127.0.0.1")
            put("port", socksPort)
            put("protocol", "socks")
            put("settings", JSONObject().apply {
                put("auth", "noauth")
                put("udp", true)
            })
            put("sniffing", JSONObject().apply {
                put("enabled", true)
                put("destOverride", JSONArray().apply {
                    put("http")
                    put("tls")
                    put("quic")
                    if (enableFakeDns) put("fakedns")
                })
            })
        })

        inbounds.put(JSONObject().apply {
            put("tag", "http-in")
            put("listen", "127.0.0.1")
            put("port", httpPort)
            put("protocol", "http")
        })
        root.put("inbounds", inbounds)

        // Primary Proxy Outbound
        val outbounds = JSONArray()
        val proxyOutbound = buildProxyOutbound(
            parsed = parsed,
            rawConfig = rawConfig,
            enableFragment = enableFragment,
            fragmentProfile = fragmentProfile,
            cleanIpOverride = cleanIpOverride,
            alpnOverride = alpnOverride
        )
        outbounds.put(proxyOutbound)

        if (enableFragment) {
            outbounds.put(JSONObject().apply {
                put("tag", "fragment-out")
                put("protocol", "freedom")
                put("settings", JSONObject().apply {
                    put("fragment", JSONObject().apply {
                        put("packets", fragmentProfile.packets)
                        put("length", fragmentProfile.length)
                        put("interval", fragmentProfile.interval)
                    })
                })
                put("streamSettings", JSONObject().apply {
                    put("sockopt", JSONObject().apply {
                        put("TcpNoDelay", true)
                        put("tcpFastOpen", true)
                    })
                })
            })
        }

        outbounds.put(JSONObject().apply {
            put("tag", "direct")
            put("protocol", "freedom")
        })
        outbounds.put(JSONObject().apply {
            put("tag", "dns-out")
            put("protocol", "dns")
        })
        outbounds.put(JSONObject().apply {
            put("tag", "block")
            put("protocol", "blackhole")
        })
        root.put("outbounds", outbounds)

        // Routing: bypass local private subnets, route DNS port 53 through proxy/dns-out, & tunnel internet through proxy
        root.put("routing", JSONObject().apply {
            put("domainStrategy", "IPIfNonMatch")
            put("rules", JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "field")
                    put("port", "53")
                    put("outboundTag", "proxy")
                })
                put(JSONObject().apply {
                    put("type", "field")
                    put("ip", JSONArray().apply {
                        put("127.0.0.0/8")
                        put("10.0.0.0/8")
                        put("172.16.0.0/12")
                        put("192.168.0.0/16")
                    })
                    put("outboundTag", "direct")
                })
                put(JSONObject().apply {
                    put("type", "field")
                    put("port", "0-65535")
                    put("outboundTag", "proxy")
                })
            })
        })

        return root.toString()
    }

    private fun buildProxyOutbound(
        parsed: ParsedVpnConfig,
        rawConfig: VpnConfig?,
        enableFragment: Boolean,
        fragmentProfile: FragmentProfile = FragmentProfile(),
        cleanIpOverride: String? = null,
        alpnOverride: List<String>? = null
    ): JSONObject {
        val proto = parsed.protocol.lowercase()
        val uuidOrPass = parsed.uuidOrPassword.ifBlank { rawConfig?.uuidOrPassword.orEmpty() }
        val security = parsed.security.ifBlank { rawConfig?.security ?: "tls" }.lowercase()
        val transport = parsed.transport.ifBlank { rawConfig?.transport ?: "tcp" }.lowercase()
        // Preserve original TLS SNI & Host header even when dialing a Clean IP override!
        val originalSni = parsed.sni.ifBlank { rawConfig?.sni ?: parsed.server }
        val path = parsed.path.ifBlank { rawConfig?.pathOrServiceName ?: "/" }
        val dialAddress = cleanIpOverride?.trim()?.takeIf { it.isNotBlank() } ?: parsed.server

        // Parse extra URI query parameters (e.g. flow, pbk, sid, fp, allowInsecure) if present
        val rawUri = rawConfig?.rawConfig.orEmpty().ifBlank { parsed.rawConfig }
        val flow = extractQueryParam(rawUri, "flow").orEmpty()
        val pbk = extractQueryParam(rawUri, "pbk").orEmpty()
        val sid = extractQueryParam(rawUri, "sid").orEmpty()
        val fp = if (fragmentProfile.profileIndex > 0) {
            fragmentProfile.fingerprint
        } else {
            extractQueryParam(rawUri, "fp").orEmpty().ifBlank { fragmentProfile.fingerprint }
        }
        val spx = extractQueryParam(rawUri, "spx").orEmpty().ifBlank { "/" }
        val allowInsecureParam = extractQueryParam(rawUri, "allowInsecure").orEmpty()
        val allowInsecure = allowInsecureParam == "1" || allowInsecureParam.equals("true", ignoreCase = true)
        val hostHeader = extractQueryParam(rawUri, "host").orEmpty().ifBlank { parsed.hostHeader.ifBlank { originalSni } }

        val outbound = JSONObject().apply {
            put("tag", "proxy")
        }

        when (proto) {
            "vless" -> {
                outbound.put("protocol", "vless")
                outbound.put("settings", JSONObject().apply {
                    put("vnext", JSONArray().apply {
                        put(JSONObject().apply {
                            put("address", dialAddress)
                            put("port", parsed.port)
                            put("users", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("id", uuidOrPass)
                                    put("encryption", "none")
                                    if (flow.isNotBlank()) put("flow", flow)
                                })
                            })
                        })
                    })
                })
            }
            "trojan" -> {
                outbound.put("protocol", "trojan")
                outbound.put("settings", JSONObject().apply {
                    put("servers", JSONArray().apply {
                        put(JSONObject().apply {
                            put("address", dialAddress)
                            put("port", parsed.port)
                            put("password", uuidOrPass)
                        })
                    })
                })
            }
            "vmess" -> {
                outbound.put("protocol", "vmess")
                outbound.put("settings", JSONObject().apply {
                    put("vnext", JSONArray().apply {
                        put(JSONObject().apply {
                            put("address", dialAddress)
                            put("port", parsed.port)
                            put("users", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("id", uuidOrPass)
                                    put("alterId", 0)
                                    put("security", "auto")
                                })
                            })
                        })
                    })
                })
            }
            "shadowsocks", "ss" -> {
                val method = uuidOrPass.substringBefore(":", "aes-256-gcm").ifBlank { "aes-256-gcm" }
                val pass = uuidOrPass.substringAfter(":", uuidOrPass)
                outbound.put("protocol", "shadowsocks")
                outbound.put("settings", JSONObject().apply {
                    put("servers", JSONArray().apply {
                        put(JSONObject().apply {
                            put("address", dialAddress)
                            put("port", parsed.port)
                            put("method", method)
                            put("password", pass)
                        })
                    })
                })
            }
            else -> {
                outbound.put("protocol", "vless")
                outbound.put("settings", JSONObject().apply {
                    put("vnext", JSONArray().apply {
                        put(JSONObject().apply {
                            put("address", dialAddress)
                            put("port", parsed.port)
                            put("users", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("id", uuidOrPass)
                                    put("encryption", "none")
                                })
                            })
                        })
                    })
                })
            }
        }

        val streamSettings = JSONObject().apply {
            put("network", if (transport == "h2") "http" else transport)
            put("security", security)

            when (security) {
                "reality" -> {
                    put("realitySettings", JSONObject().apply {
                        put("show", false)
                        put("fingerprint", fp)
                        put("serverName", originalSni)
                        put("publicKey", pbk)
                        put("shortId", sid)
                        put("spiderX", spx)
                    })
                }
                "tls" -> {
                    put("tlsSettings", JSONObject().apply {
                        put("serverName", originalSni)
                        put("allowInsecure", allowInsecure)
                        put("fingerprint", fp)
                        put("alpn", JSONArray().apply {
                            val effectiveAlpn = alpnOverride?.takeIf { it.isNotEmpty() } ?: listOf("h2", "http/1.1")
                            effectiveAlpn.forEach { put(it) }
                        })
                    })
                }
            }

            when (transport) {
                "ws" -> {
                    put("wsSettings", JSONObject().apply {
                        put("path", path.ifBlank { "/" })
                        put("headers", JSONObject().apply {
                            put("Host", hostHeader)
                        })
                    })
                }
                "grpc" -> {
                    put("grpcSettings", JSONObject().apply {
                        put("serviceName", path.trimStart('/'))
                        put("multiMode", false)
                    })
                }
                "xhttp", "splithttp" -> {
                    put("xhttpSettings", JSONObject().apply {
                        put("path", path.ifBlank { "/" })
                        put("host", hostHeader)
                    })
                }
            }

            put("sockopt", JSONObject().apply {
                if (enableFragment && (security == "tls" || security == "reality")) {
                    put("dialerProxy", "fragment-out")
                }
                put("tcpKeepAliveInterval", 30)
                put("TcpNoDelay", true)
                put("tcpFastOpen", true)
            })
        }

        outbound.put("streamSettings", streamSettings)
        return outbound
    }

    private fun extractQueryParam(uri: String, key: String): String? {
        if (!uri.contains("?")) return null
        val query = uri.substringAfter("?").substringBefore("#")
        return query.split("&")
            .firstOrNull { it.startsWith("$key=", ignoreCase = true) }
            ?.substringAfter("=")
            ?.takeIf { it.isNotBlank() }
    }

    suspend fun startWithTunAsync(
        context: Context,
        vpnService: VpnService,
        tunFd: Int,
        parsed: ParsedVpnConfig,
        rawConfig: VpnConfig? = null,
        enableFragment: Boolean = true,
        fragmentProfile: FragmentProfile = FragmentProfile(),
        preloadScope: CoroutineScope? = null,
        cleanIpOverride: String? = null,
        enableDoH: Boolean = false,
        enableFakeDns: Boolean = false,
        alpnOverride: List<String>? = null
    ): XrayStartResult = withContext(Dispatchers.IO) {
        if (!assetsPrepared.get()) {
            preloadNativeAssetsAsync(context, preloadScope ?: this).await()
        }
        startWithTun(
            context = context,
            vpnService = vpnService,
            tunFd = tunFd,
            parsed = parsed,
            rawConfig = rawConfig,
            enableFragment = enableFragment,
            fragmentProfile = fragmentProfile,
            cleanIpOverride = cleanIpOverride,
            enableDoH = enableDoH,
            enableFakeDns = enableFakeDns,
            alpnOverride = alpnOverride
        )
    }

    /**
     * Attempts to launch Xray Core using native libXray with Android TUN FD protection.
     * Gracefully reports startedNative = false in JVM/Robolectric tests or if native init fails.
     */
    fun startWithTun(
        context: Context,
        vpnService: VpnService,
        tunFd: Int,
        parsed: ParsedVpnConfig,
        rawConfig: VpnConfig? = null,
        enableFragment: Boolean = true,
        fragmentProfile: FragmentProfile = FragmentProfile(),
        cleanIpOverride: String? = null,
        enableDoH: Boolean = false,
        enableFakeDns: Boolean = false,
        alpnOverride: List<String>? = null
    ): XrayStartResult {
        if (!assetsPrepared.get()) {
            prepareNativeAssetsInternal(context.applicationContext)
        }
        if (!nativeSupported) {
            activeMode = XrayEngineMode.KOTLIN_ROUTER
            return XrayStartResult(
                startedNative = false,
                mode = XrayEngineMode.KOTLIN_ROUTER,
                message = "Native libXray disabled in current runtime"
            )
        }

        return try {
            if (!ensureNativeRuntimeInitialized(context)) {
                activeMode = XrayEngineMode.KOTLIN_ROUTER
                return XrayStartResult(
                    startedNative = false,
                    mode = XrayEngineMode.KOTLIN_ROUTER,
                    message = "Native libXray unavailable in current runtime"
                )
            }

            val protector = DialerController { fd ->
                try {
                    vpnService.protect(fd.toInt())
                } catch (_: Exception) {
                    false
                }
            }
            LibXray.registerDialerController(protector)
            LibXray.registerListenerController(protector)

            runCatching { LibXray.stopXray() }
            LibXray.setTunFd(tunFd)

            val datDir = File(context.filesDir, "xray_assets").apply { mkdirs() }.absolutePath
            val configJson = buildXrayJson(
                parsed = parsed,
                rawConfig = rawConfig,
                tunFd = tunFd,
                enableFragment = enableFragment,
                fragmentProfile = fragmentProfile,
                cleanIpOverride = cleanIpOverride,
                enableDoH = enableDoH,
                enableFakeDns = enableFakeDns,
                alpnOverride = alpnOverride
            )
            val requestB64 = LibXray.newXrayRunFromJSONRequest(datDir, configJson)
            val responseB64 = LibXray.runXrayFromJSON(requestB64)
            val responseJson = decodeBase64Envelope(responseB64)
            val success = responseJson?.optBoolean("success", false) ?: false
            val version = runCatching { decodeVersionEnvelope(LibXray.xrayVersion()) }.getOrDefault("26.6")

            if (success && isXrayRunning()) {
                activeMode = XrayEngineMode.NATIVE_LIBXRAY
                Log.i(TAG, "Native libXray started (version=$version, tunFd=$tunFd)")
                XrayStartResult(
                    startedNative = true,
                    mode = XrayEngineMode.NATIVE_LIBXRAY,
                    xrayVersion = version,
                    socksPort = LOCAL_SOCKS_PORT,
                    message = "Xray Core v$version active"
                )
            } else {
                val err = responseJson?.optString("error").orEmpty()
                Log.w(TAG, "Native libXray did not stay active ($err); falling back to Kotlin TUN router")
                runCatching { LibXray.stopXray() }
                activeMode = XrayEngineMode.KOTLIN_ROUTER
                XrayStartResult(
                    startedNative = false,
                    mode = XrayEngineMode.KOTLIN_ROUTER,
                    xrayVersion = version,
                    message = err
                )
            }
        } catch (ule: UnsatisfiedLinkError) {
            nativeSupported = false
            activeMode = XrayEngineMode.KOTLIN_ROUTER
            Log.d(TAG, "Native libgojni unavailable (${ule.message}); using Kotlin TUN router")
            XrayStartResult(
                startedNative = false,
                mode = XrayEngineMode.KOTLIN_ROUTER,
                message = "JVM / non-native runtime"
            )
        } catch (t: Throwable) {
            activeMode = XrayEngineMode.KOTLIN_ROUTER
            Log.w(TAG, "Native libXray start error: ${t.message}")
            runCatching { LibXray.stopXray() }
            XrayStartResult(
                startedNative = false,
                mode = XrayEngineMode.KOTLIN_ROUTER,
                message = t.localizedMessage ?: "Xray fallback"
            )
        }
    }

    fun stop() {
        activeMode = XrayEngineMode.KOTLIN_ROUTER
        if (!nativeSupported) return
        try {
            LibXray.stopXray()
        } catch (_: UnsatisfiedLinkError) {
            nativeSupported = false
        } catch (t: Throwable) {
            Log.w(TAG, "Error stopping libXray: ${t.message}")
        }
    }

    fun isXrayRunning(): Boolean {
        if (!nativeSupported) return false
        return try {
            LibXray.getXrayState()
        } catch (_: UnsatisfiedLinkError) {
            nativeSupported = false
            false
        } catch (_: Throwable) {
            false
        }
    }

    fun isLocalSocksListening(): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", LOCAL_SOCKS_PORT), 200)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    fun convertShareLinkToXrayJson(shareUri: String): String? {
        if (!nativeSupported) return null
        return try {
            val encoded = Base64.encodeToString(shareUri.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            val resultB64 = LibXray.convertShareLinksToXrayJson(encoded)
            val json = decodeBase64Envelope(resultB64)
            if (json?.optBoolean("success", false) == true) {
                json.opt("data")?.toString()
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun decodeBase64Envelope(raw: String?): JSONObject? {
        if (raw.isNullOrBlank()) return null
        val text = raw.trim()
        return try {
            if (text.startsWith("{")) {
                JSONObject(text)
            } else {
                val decoded = String(Base64.decode(text, Base64.DEFAULT), Charsets.UTF_8)
                JSONObject(decoded)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeVersionEnvelope(raw: String?): String {
        val obj = decodeBase64Envelope(raw)
        return obj?.optString("data")?.takeIf { it.isNotBlank() } ?: "26.6"
    }
}
