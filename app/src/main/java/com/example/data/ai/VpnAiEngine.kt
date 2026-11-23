package com.example.data.ai

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.ThinkingLevel
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.thinkingConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "VpnAiEngine"

sealed interface AiResult {
    data class Success(val responseText: String) : AiResult
    data class Error(val message: String) : AiResult
}

sealed interface NexusStreamEvent {
    data class Partial(val accumulatedText: String) : NexusStreamEvent
    data class Completed(val fullText: String) : NexusStreamEvent
    data class Error(val message: String) : NexusStreamEvent
}

/**
 * TMS NEXUS AI Engine powered strictly by Firebase AI Logic (`Firebase.ai`) + App Check.
 * - Zero hardcoded API keys, zero mock/placeholder responses on failure.
 * - Strict separation between User Assistant (`USER_ASSISTANT`) and Admin Operations Copilot (`ADMIN_COPILOT`).
 * - Supports streaming responses (`generateContentStream`), structured JSON schemas (`Schema.obj`),
 *   and High-Thinking deep topological/anomaly reasoning (`gemini-4-argon`).
 */
class VpnAiEngine {

    // Ultra-fast model for quick audits & lightweight tasks (gemini-3.5-flash-lite)
    private val liteModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite"
        )
    }

    // Default versatile model for streaming NEXUS AI chat & radar adaptation (gemini-3.8-flash)
    private val userAssistantModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = content {
                text(
                    "شما «TMS NEXUS AI» دستیار هوشمند کاربران در اپلیکیشن TMS VPN هستید. " +
                        "فقط درباره وضعیت اتصال، کانفیگ، رادار ضد فیلتر، تست نشت، بهینه‌سازی اپراتورها و اشتراک همین کاربر راهنمایی بدهید. " +
                        "هرگز اطلاعات محرمانه سایر کاربران، لاگ‌های داخلی سرور یا دستورات مدیریتی ادمین را فاش یا شبیه‌سازی نکنید. " +
                        "پاسخ‌ها را دقیق، صادقانه، کاربردی، شفاف و به زبان خواسته‌شده (فارسی یا انگلیسی) ارائه دهید."
                )
            }
        )
    }

    private val adminCopilotModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = content {
                text(
                    "شما «TMS NEXUS AI Operations Copilot» دستیار عملیات مدیران زیرساخت TMS VPN هستید. " +
                        "وظیفه شما تحلیل سلامت استخر IPهای اختصاصی و مخزن ۱۵۰ IP تمیز، شناسایی ناهنجاری‌ها، " +
                        "بررسی هشدارهای کمبود ظرفیت، و پیشنهاد استراتژی Failover و چرخش روزانه بدون حدس زدن اعداد یا توکن‌های جعلی است."
                )
            }
        )
    }

    // Structured JSON model for VPN connection & config diagnostics
    private val structuredDiagnosticModel by lazy {
        val diagnosticSchema = Schema.obj(
            properties = mapOf(
                "summaryFa" to Schema.string("خلاصه دقیق عیب‌یابی به فارسی"),
                "severity" to Schema.enumeration(listOf("LOW", "MEDIUM", "HIGH", "CRITICAL"), "سطح شدت مشکل"),
                "rootCause" to Schema.string("علت احتمالی فنی مشکل بر اساس تله‌متری واقعی"),
                "recommendedActions" to Schema.array(
                    items = Schema.string(),
                    description = "لیست اقدامات عملی و گام‌به‌گام برای کاربر"
                ),
                "suggestedTransport" to Schema.string("پروتکل/ترابری پیشنهادی مثل vless+ws+tls یا reality"),
                "shouldTriggerIspTune" to Schema.boolean("آیا اجرای Auto-Tune اپراتور توصیه می‌شود؟"),
                "shouldTriggerFailover" to Schema.boolean("آیا سوییچ به نود سالم بعدی توصیه می‌شود؟"),
                "confidencePercent" to Schema.integer("درصد اطمینان تحلیل بین 0 تا 100")
            )
        )
        val config = generationConfig {
            responseMimeType = "application/json"
            responseSchema = diagnosticSchema
        }
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = config
        )
    }

    // Structured JSON model for Support Ticket auto-triage
    private val ticketTriageModel by lazy {
        val triageSchema = Schema.obj(
            properties = mapOf(
                "category" to Schema.enumeration(
                    listOf("CONNECTION", "SUBSCRIPTION", "DEDICATED_IP", "SPEED_LATENCY", "GENERAL"),
                    "دسته‌بندی تیکت"
                ),
                "priority" to Schema.enumeration(
                    listOf("LOW", "MEDIUM", "HIGH", "URGENT"),
                    "اولویت تیکت"
                ),
                "aiTriageSummary" to Schema.string("خلاصه فنی ۱ تا ۲ جمله‌ای برای تیم پشتیبانی"),
                "instantUserSteps" to Schema.array(
                    items = Schema.string(),
                    description = "۲ تا ۳ راهکار فوری برای کاربر"
                )
            )
        )
        val config = generationConfig {
            responseMimeType = "application/json"
            responseSchema = triageSchema
        }
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = config
        )
    }

    // High-Thinking reasoning model for complex multi-node topology & anomaly analysis (gemini-4-argon)
    private val reasoningModel by lazy {
        val config = generationConfig {
            thinkingConfig = thinkingConfig {
                thinkingLevel = ThinkingLevel.HIGH
            }
        }
        Firebase.ai.generativeModel(
            modelName = "gemini-4-argon",
            generationConfig = config
        )
    }

    /**
     * Streaming TMS NEXUS AI chat strictly scoped to USER_ASSISTANT or ADMIN_COPILOT.
     */
    fun streamNexusAssistant(
        scope: NexusAiScope,
        sanitizedTelemetryContext: String,
        userMessage: String,
        recentHistory: List<NexusChatMessage> = emptyList(),
        isPersian: Boolean = true
    ): Flow<NexusStreamEvent> = flow {
        val model = when (scope) {
            NexusAiScope.USER_ASSISTANT -> userAssistantModel
            NexusAiScope.ADMIN_COPILOT -> adminCopilotModel
        }
        val historyPrompt = recentHistory
            .filter { !it.isError && it.text.isNotBlank() }
            .takeLast(6)
            .joinToString("\n") { msg ->
                val speaker = if (msg.isFromUser) "User" else "NEXUS"
                "$speaker: ${msg.text.take(600)}"
            }

        val prompt = buildString {
            appendLine("Live Verified System Telemetry (never invent metrics outside this block):")
            appendLine(sanitizedTelemetryContext)
            if (historyPrompt.isNotBlank()) {
                appendLine()
                appendLine("Recent Conversation:")
                appendLine(historyPrompt)
            }
            appendLine()
            appendLine("Current Request (${if (isPersian) "Respond in clear Persian" else "Respond in clear English"}):")
            appendLine(userMessage.trim())
        }

        val accumulated = StringBuilder()
        try {
            withTimeout(60_000L) {
                model.generateContentStream(prompt).collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        accumulated.append(piece)
                        emit(NexusStreamEvent.Partial(accumulated.toString()))
                    }
                }
            }
            if (accumulated.isNotBlank()) {
                emit(NexusStreamEvent.Completed(accumulated.toString()))
            } else {
                emit(
                    NexusStreamEvent.Error(
                        if (isPersian) "پاسخی از سرویس TMS NEXUS AI دریافت نشد."
                        else "Empty response from TMS NEXUS AI."
                    )
                )
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "NEXUS streaming timed out", e)
            emit(
                NexusStreamEvent.Error(
                    if (isPersian) "مهلت پاسخ‌دهی NEXUS AI به پایان رسید (Timeout)."
                    else "NEXUS AI streaming timed out after 60s."
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "NEXUS streaming failed", e)
            emit(
                NexusStreamEvent.Error(
                    e.localizedMessage ?: if (isPersian) "خطا در ارتباط با سرویس هوش مصنوعی Firebase" else "Firebase AI request failed"
                )
            )
        }
    }

    /**
     * Structured JSON diagnostic report for User's active connection / config.
     */
    suspend fun diagnoseConnectionStructured(
        sanitizedUserContext: String,
        symptomDescription: String = ""
    ): Result<NexusDiagnosticReport> {
        return try {
            val prompt = """
                You are TMS NEXUS AI Diagnostic Engine. Analyze ONLY the real telemetry below and return structured JSON matching the schema.
                Telemetry:
                $sanitizedUserContext

                User-reported symptom (if any):
                ${symptomDescription.ifBlank { "General connection & anti-censorship health audit" }}
            """.trimIndent()

            val response = withTimeout(45_000L) {
                structuredDiagnosticModel.generateContent(prompt)
            }
            val jsonText = response.text ?: return Result.failure(
                IllegalStateException("مدل خروجی ساختاریافته برنگرداند.")
            )
            val json = JSONObject(jsonText)
            val actionsArr = json.optJSONArray("recommendedActions")
            val actions = buildList {
                if (actionsArr != null) {
                    for (i in 0 until actionsArr.length()) {
                        val step = actionsArr.optString(i).trim()
                        if (step.isNotBlank()) add(step)
                    }
                }
            }
            Result.success(
                NexusDiagnosticReport(
                    summaryFa = json.optString("summaryFa", ""),
                    severity = json.optString("severity", "MEDIUM"),
                    rootCause = json.optString("rootCause", ""),
                    recommendedActions = actions,
                    suggestedTransport = json.optString("suggestedTransport", "vless+ws+tls"),
                    shouldTriggerIspTune = json.optBoolean("shouldTriggerIspTune", false),
                    shouldTriggerFailover = json.optBoolean("shouldTriggerFailover", false),
                    confidencePercent = json.optInt("confidencePercent", 80).coerceIn(0, 100)
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Structured diagnostic timed out", e)
            Result.failure(Exception("مهلت عیب‌یابی هوشمند به پایان رسید (Timeout)"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Structured diagnostic failed", e)
            Result.failure(e)
        }
    }

    /**
     * Structured JSON triage when submitting or analyzing a support ticket.
     */
    suspend fun triageSupportTicket(
        subject: String,
        description: String,
        sanitizedUserContext: String
    ): Result<NexusTicketTriage> {
        return try {
            val prompt = """
                Classify and triage this TMS VPN support ticket using the JSON schema.
                Subject: $subject
                Description: $description
                Context:
                $sanitizedUserContext
            """.trimIndent()

            val response = withTimeout(35_000L) {
                ticketTriageModel.generateContent(prompt)
            }
            val jsonText = response.text ?: return Result.failure(IllegalStateException("Empty triage JSON"))
            val json = JSONObject(jsonText)
            val stepsArr = json.optJSONArray("instantUserSteps")
            val steps = buildList {
                if (stepsArr != null) {
                    for (i in 0 until stepsArr.length()) {
                        val s = stepsArr.optString(i).trim()
                        if (s.isNotBlank()) add(s)
                    }
                }
            }
            Result.success(
                NexusTicketTriage(
                    category = json.optString("category", "GENERAL"),
                    priority = json.optString("priority", "MEDIUM"),
                    aiTriageSummary = json.optString("aiTriageSummary", ""),
                    instantUserSteps = steps
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Ticket triage timed out", e)
            Result.failure(e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Ticket triage failed", e)
            Result.failure(e)
        }
    }

    /**
     * Fast config security & anti-censorship audit (gemini-3.5-flash-lite).
     */
    suspend fun quickAuditConfig(
        protocol: String,
        server: String,
        port: Int,
        security: String,
        transport: String
    ): AiResult {
        return try {
            val prompt = """
                شما مهندس امنیت و شبکه ارشد در سیستم TMS NEXUS AI هستید.
                لطفاً این مشخصات کانفیگ VPN را سریعاً از نظر امنیت، پروتکل و آسیب‌پذیری DPI بررسی کنید (بدون درخواست یا حدس رمز خصوصی):
                پروتکل: $protocol
                سرور: $server
                پورت: $port
                لایه امنیتی: $security
                نوع انتقال (Transport): $transport

                پاسخ کوتاه، فنی، دقیق و به زبان فارسی همراه با نکات عملی ارائه دهید.
            """.trimIndent()

            val response = withTimeout(45_000L) {
                liteModel.generateContent(prompt)
            }
            val text = response.text
            if (!text.isNullOrBlank()) {
                AiResult.Success(text)
            } else {
                AiResult.Error("پاسخی از مدل هوش مصنوعی دریافت نشد / Empty response from AI")
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI quickAuditConfig timed out", e)
            AiResult.Error("مهلت درخواست به پایان رسید (Timeout) / Request timed out")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI quickAuditConfig failed", e)
            AiResult.Error(e.localizedMessage ?: "خطا در برقراری ارتباط با مدل هوش مصنوعی")
        }
    }

    /**
     * Deep topological, inventory anomaly, and failover reasoning (gemini-4-argon with High Thinking).
     */
    suspend fun optimizeRoutingAndFailover(
        serversSummary: String,
        activeLatency: String,
        userQuery: String,
        adminOperationsSnapshot: String = ""
    ): AiResult {
        return try {
            val prompt = """
                شما معمار ارشد زیرساخت و هوش مصنوعی عملیاتی (TMS NEXUS AI Admin Copilot) هستید.
                فقط بر اساس داده‌های واقعی زیر، تحلیل ناهنجاری، سیاست Failover و بهینه‌سازی استخر IPها را ارائه دهید.

                وضعیت سرورها و زیرساخت کنونی:
                $serversSummary

                تاخیرها و پینگ‌های زنده:
                $activeLatency

                ${if (adminOperationsSnapshot.isNotBlank()) "گزارش جامع عملیات و مخزن IPهای تمیز:\n$adminOperationsSnapshot" else ""}

                درخواست مدیر یا چالش شبکه:
                $userQuery

                لطفاً تحلیل مهندسی دقیق، اولویت‌بندی نودها، ریسک‌های ظرفیت و پیشنهادهای عملی را گام‌به‌گام به فارسی بنویسید.
            """.trimIndent()

            val response = withTimeout(60_000L) {
                reasoningModel.generateContent(prompt)
            }
            val text = response.text
            if (!text.isNullOrBlank()) {
                AiResult.Success(text)
            } else {
                AiResult.Error("پاسخی از مدل هوش مصنوعی دریافت نشد / Empty response from AI")
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Reasoning request timed out", e)
            AiResult.Error("مهلت پردازش استدلال به پایان رسید / Reasoning timed out")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Reasoning generation failed", e)
            AiResult.Error(e.localizedMessage ?: "خطا در پردازش استدلال عمیق")
        }
    }

    /**
     * Live Anti-Filter Radar AI analysis (transparent real errors — zero mock fallback!).
     */
    suspend fun analyzeRadarAndAdaptNetwork(
        radarResultsSummary: String,
        activeConfig: String,
        ispName: String = "اپراتورهای همراه ایران (همراه اول / ایرانسل / رایتل)"
    ): AiResult {
        return try {
            val prompt = """
                شما مهندس ارشد تطبیق شبکه در سیستم TMS NEXUS AI هستید.
                نتایج پایش زنده رادار ضد فیلتر:
                $radarResultsSummary

                کانفیگ فعال: $activeConfig
                بستر شبکه: $ispName

                تحلیل تخصصی و کاربردی به زبان فارسی در ۳ بخش کوتاه بنویسید:
                ۱. وضعیت واقعی DPI و عبور تلگرام/اینستاگرام/وب آزاد بر اساس نتایج فوق.
                ۲. تنظیمات دقیق پیشنهادی (WebSocket/Reality، SNI، Fragment و DNS).
                ۳. توصیه عملیاتی برای پایداری اتصال روی اپراتور کاربر.
            """.trimIndent()

            val response = withTimeout(45_000L) {
                userAssistantModel.generateContent(prompt)
            }
            val text = response.text
            if (!text.isNullOrBlank()) {
                AiResult.Success(text)
            } else {
                AiResult.Error("پاسخ مدل هوش مصنوعی خالی است / Empty response from Firebase AI.")
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Radar AI analysis timed out", e)
            AiResult.Error("مهلت درخواست به پایان رسید (Timeout) — اتصال به Firebase AI بررسی شود.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Radar AI analysis failed", e)
            AiResult.Error(
                "خطا در تحلیل هوش مصنوعی: ${e.localizedMessage ?: "اتصال یا تاییدیه App Check در دسترس نیست"}"
            )
        }
    }
}
