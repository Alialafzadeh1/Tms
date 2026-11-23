package com.example.data.ai

import com.example.data.local.CleanIpEntity
import com.example.data.local.CleanIpHealthStatus
import com.example.data.model.AllocatedEndpointRecord
import com.example.data.model.InventoryShortageAlert
import com.example.data.model.ManagedEndpoint
import com.example.data.model.RotationAuditLog
import com.example.data.model.UserSubscription
import com.example.data.model.VpnConfig
import com.example.data.model.VpnConnectionState
import com.example.data.model.VpnServer
import com.example.data.model.VpnSessionStats
import com.example.vpn.engine.IspTuningReport
import com.example.vpn.engine.LeakCheckReport
import com.example.vpn.engine.TunnelHealthReport

enum class NexusAiScope {
    USER_ASSISTANT,
    ADMIN_COPILOT
}

sealed interface NexusAiUiState {
    data object Idle : NexusAiUiState
    data object Loading : NexusAiUiState
    data class Streaming(val partialText: String) : NexusAiUiState
    data class Success(val text: String) : NexusAiUiState
    data class Error(val message: String) : NexusAiUiState
}

data class NexusDiagnosticReport(
    val summaryFa: String,
    val severity: String, // LOW, MEDIUM, HIGH, CRITICAL
    val rootCause: String,
    val recommendedActions: List<String>,
    val suggestedTransport: String,
    val shouldTriggerIspTune: Boolean,
    val shouldTriggerFailover: Boolean,
    val confidencePercent: Int
)

data class NexusTicketTriage(
    val category: String, // CONNECTION, SUBSCRIPTION, DEDICATED_IP, SPEED_LATENCY, GENERAL
    val priority: String, // LOW, MEDIUM, HIGH, URGENT
    val aiTriageSummary: String,
    val instantUserSteps: List<String>
)

data class NexusChatMessage(
    val id: String,
    val isFromUser: Boolean,
    val text: String,
    val timestampMs: Long = System.currentTimeMillis(),
    val scope: NexusAiScope = NexusAiScope.USER_ASSISTANT,
    val isStreaming: Boolean = false,
    val isError: Boolean = false
)

data class KnowledgeBaseArticle(
    val id: String,
    val titleFa: String,
    val titleEn: String,
    val category: String,
    val summaryFa: String,
    val summaryEn: String,
    val stepsFa: List<String>,
    val stepsEn: List<String>
)

/**
 * Strict data-isolation & privacy sanitizer for TMS NEXUS AI prompts.
 * Never transmits raw UUIDs, Trojan passwords, or private credential tokens to the LLM.
 * Enforces strict separation between User scope and Admin Operations scope.
 */
object NexusContextSanitizer {

    fun buildUserSafeContext(
        vpnState: VpnConnectionState,
        selectedConfig: VpnConfig?,
        userConfigCount: Int,
        onlineConfigCount: Int,
        sessionStats: VpnSessionStats,
        healthReport: TunnelHealthReport?,
        leakCheckReport: LeakCheckReport?,
        ispTuningReport: IspTuningReport?,
        userSubscription: UserSubscription?,
        allocatedEndpointCount: Int
    ): String = buildString {
        appendLine("scope: USER_ASSISTANT (strictly limited to current user's own session)")
        appendLine("vpn_state: ${vpnState.name}")
        appendLine("engine_mode: ${sessionStats.engineMode}")
        if (!sessionStats.errorMessage.isNullOrBlank()) {
            appendLine("last_tunnel_error: ${sessionStats.errorMessage.take(200)}")
        }
        appendLine("user_configs_total: $userConfigCount (online: $onlineConfigCount)")
        if (selectedConfig != null) {
            appendLine(
                "active_config: name=${selectedConfig.name}, protocol=${selectedConfig.protocol}, " +
                    "host=${selectedConfig.server}, port=${selectedConfig.port}, " +
                    "transport=${selectedConfig.transport}, security=${selectedConfig.security}, " +
                    "sni=${selectedConfig.sni.ifBlank { "auto" }}, pingMs=${selectedConfig.pingMs}, " +
                    "origin=${selectedConfig.origin}"
            )
        } else {
            appendLine("active_config: none")
        }
        if (healthReport != null) {
            appendLine(
                "tunnel_health: healthy=${healthReport.isHealthy}, socks5Verified=${healthReport.checkedThroughProxy}, " +
                    "global204=${healthReport.global204Reachable}(${healthReport.global204LatencyMs}ms), " +
                    "telegram=${healthReport.telegramReachable}, instagram=${healthReport.instagramReachable}"
            )
        }
        if (leakCheckReport != null) {
            appendLine(
                "leak_check: protected=${leakCheckReport.isLeakProtected}, country=${leakCheckReport.countryCode}, " +
                    "dns=${leakCheckReport.dnsStatusLabel}, err=${leakCheckReport.errorMessage ?: "none"}"
            )
        }
        if (ispTuningReport != null) {
            appendLine(
                "isp_profile: operator=${ispTuningReport.operatorLabel}, network=${ispTuningReport.networkType.name}, " +
                    "bestCleanIp=${ispTuningReport.bestCleanIp}(${ispTuningReport.bestCleanIpLatencyMs}ms), " +
                    "fragment=${ispTuningReport.fragmentProfile.length}/${ispTuningReport.fragmentProfile.interval}"
            )
        }
        if (userSubscription != null) {
            appendLine(
                "subscription: plan=${userSubscription.planId}, status=${userSubscription.status}, " +
                    "payment=${userSubscription.paymentStatus}, quota=${userSubscription.endpointAllocationLimit}, " +
                    "allocatedEndpointsActive=$allocatedEndpointCount"
            )
        } else {
            appendLine("subscription: none")
        }
    }

    fun buildAdminOperationsContext(
        servers: List<VpnServer>,
        configs: List<VpnConfig>,
        endpointInventory: List<ManagedEndpoint>,
        cleanIpPool: List<CleanIpEntity>,
        subscriptions: List<UserSubscription>,
        shortageAlerts: List<InventoryShortageAlert>,
        rotationLogs: List<RotationAuditLog>
    ): String = buildString {
        appendLine("scope: ADMIN_COPILOT (aggregated enterprise operations telemetry; private tokens redacted)")
        val onlineServers = servers.count { it.status == "online" }
        appendLine("vpn_servers: total=${servers.size}, online=$onlineServers")
        servers.take(12).forEach { s ->
            appendLine("  - server=${s.name} (${s.countryCode}) ${s.host}:${s.port} proto=${s.protocol} status=${s.status} ping=${s.latencyMs}ms stability=${s.stabilityScore}%")
        }

        val invHealthyFree = endpointInventory.count { it.enabled && it.healthy && it.assignedUserId.isBlank() }
        val invAssigned = endpointInventory.count { it.assignedUserId.isNotBlank() }
        val invUnhealthy = endpointInventory.count { !it.healthy || !it.enabled }
        appendLine("dedicated_inventory: total=${endpointInventory.size}, freeHealthy=$invHealthyFree, assigned=$invAssigned, unhealthyOrDisabled=$invUnhealthy")

        val ipv4Count = cleanIpPool.count { it.addressType == "IPV4" }
        val ipv6Count = cleanIpPool.count { it.addressType == "IPV6" }
        val domainCount = cleanIpPool.count { it.addressType == "DOMAIN" }
        val cleanHealthy = cleanIpPool.count { it.healthStatus == CleanIpHealthStatus.HEALTHY.name }
        val cleanQuarantined = cleanIpPool.count { it.healthStatus == CleanIpHealthStatus.QUARANTINED.name }
        val cleanUntested = cleanIpPool.count { it.healthStatus == CleanIpHealthStatus.UNTESTED.name }
        appendLine(
            "local_clean_ip_pool: total=${cleanIpPool.size} (v4=$ipv4Count, v6=$ipv6Count, domain=$domainCount), " +
                "healthy=$cleanHealthy, quarantined=$cleanQuarantined, untested=$cleanUntested"
        )
        cleanIpPool.filter { it.healthStatus == CleanIpHealthStatus.HEALTHY.name }
            .sortedBy { if (it.latencyMs > 0) it.latencyMs else Int.MAX_VALUE }
            .take(5)
            .forEach { ep ->
                appendLine("  - top_clean_host=${ep.host} (${ep.addressType}) latency=${ep.latencyMs}ms stability=${ep.stabilityScore}%")
            }

        val activeSubs = subscriptions.count { it.status == "ACTIVE" }
        val pendingSubs = subscriptions.count { it.status == "PENDING_PAYMENT" }
        appendLine("subscriptions: total=${subscriptions.size}, active=$activeSubs, pendingPayment=$pendingSubs")

        val openShortages = shortageAlerts.filter { !it.resolved }
        appendLine("unresolved_shortage_alerts: ${openShortages.size}")
        openShortages.take(6).forEach { alert ->
            appendLine("  - shortage: plan=${alert.planId}, requested=${alert.requestedCount}, allocated=${alert.allocatedCount}, shortfall=${alert.shortfallCount}")
        }

        appendLine("recent_rotations: ${rotationLogs.size}, total_configs_known: ${configs.size}")
        rotationLogs.take(4).forEach { log ->
            appendLine("  - rotation: mode=${log.triggerMode}, cycle=${log.cycleKey}, rotated=${log.rotatedUsersCount}/${log.targetUserCount}, shortages=${log.shortageCount}, status=${log.status}")
        }
    }
}

object NexusKnowledgeBase {
    val articles: List<KnowledgeBaseArticle> = listOf(
        KnowledgeBaseArticle(
            id = "kb_dpi_fragment",
            titleFa = "رفع اختلال و تایم‌اوت روی همراه اول و ایرانسل",
            titleEn = "Fixing timeouts on MCI / Irancell (Anti-DPI)",
            category = "CONNECTION",
            summaryFa = "در شبکه‌های همراه ایران، فایروال DPI بسته‌های اولیه TLS ClientHello را مسدود می‌کند.",
            summaryEn = "Iranian mobile ISPs frequently inspect initial TLS ClientHello packets.",
            stepsFa = listOf(
                "از صفحه خانه گزینه «تنظیم خودکار اپراتور» (ISP Auto-Tune) را اجرا کنید.",
                "پروفایل Fragment روی حالت Anti-DPI فعال و بهترین IP تمیز تست شده انتخاب می‌شود.",
                "در صورت ادامه اختلال، دکمه «اتصال مسابقه‌ای هوشمند» را بزنید تا سریع‌ترین نود انتخاب شود."
            ),
            stepsEn = listOf(
                "Run ISP Auto-Tune from the Home screen.",
                "Apply the detected clean Anycast IP and TLS Fragment profile.",
                "Use Smart Racing Connect to connect to the lowest-latency reachable node."
            )
        ),
        KnowledgeBaseArticle(
            id = "kb_dedicated_ip_rotation",
            titleFa = "نحوه عملکرد IP اختصاصی و چرخش روزانه اشتراک",
            titleEn = "Dedicated IP Allocation & Daily Rotation",
            category = "SUBSCRIPTION",
            summaryFa = "کاربران اشتراکی بسته به پلن (۵، ۱۵ یا ۵۰ IP) نودهای انحصاری با چرخش روزانه دریافت می‌کنند.",
            summaryEn = "Subscribers receive exclusive endpoints (5, 15, or 50 per plan) with daily rotation.",
            stepsFa = listOf(
                "درخواست اشتراک پس از ثبت تا تایید سرور/مدیر در وضعیت PENDING_PAYMENT می‌ماند.",
                "پس از تایید، آی‌پی‌ها به‌صورت انحصاری فقط به حساب شما تخصیص داده می‌شوند.",
                "در زمان اتصال فعال، نود در حال استفاده حین چرخش قطع نمی‌شود و از نوبت بعدی جایگزین می‌شود."
            ),
            stepsEn = listOf(
                "Payment requests stay PENDING_PAYMENT until verified.",
                "Once confirmed, endpoints are exclusively assigned to your account.",
                "Active connected tunnels are preserved during scheduled rotations."
            )
        ),
        KnowledgeBaseArticle(
            id = "kb_split_tunnel",
            titleFa = "باز نشدن همراه‌بانک و سایت‌های ایرانی با VPN روشن",
            titleEn = "Iranian Banking Apps & Split Tunneling",
            category = "GENERAL",
            summaryFa = "برای استفاده هم‌زمان از اپ‌های بانکی و اینترنت آزاد، تفکیک برنامه‌ها را فعال کنید.",
            summaryEn = "Enable Per-App Split Tunneling to route Iranian banking apps directly.",
            stepsFa = listOf(
                "وارد تب پروفایل و تنظیمات شوید.",
                "گزینه «مستثنی کردن اپ‌های بانکی و ایرانی» را روشن کنید.",
                "در صورت نیاز، سایر اپ‌های دلخواه را از لیست اضافه کنید و دوباره متصل شوید."
            ),
            stepsEn = listOf(
                "Open Profile & Settings tab.",
                "Enable Bypass Iranian & Banking Apps.",
                "Reconnect the VPN tunnel to apply per-app routing."
            )
        )
    )
}
