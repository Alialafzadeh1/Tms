package com.example.data.ai

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.ThinkingLevel
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.thinkingConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

private const val TAG = "VpnAiEngine"

sealed interface AiResult {
    data class Success(val responseText: String) : AiResult
    data class Error(val message: String) : AiResult
}

class VpnAiEngine {

    // Fast Low-Latency model for instant diagnostics (gemini-3.1-flash-lite)
    private val fastModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.1-flash-lite"
        )
    }

    // High Thinking model for complex multi-node architecture & circumvention (gemini-4-argon)
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
     * Fast diagnostic analysis using low-latency gemini-3.1-flash-lite
     */
    suspend fun quickAuditConfig(protocol: String, server: String, port: Int, security: String, transport: String): AiResult {
        return try {
            val prompt = """
                شما مهندس امنیت و شبکه ارشد در پلتفرم TMS VPN هستید.
                لطفاً این مشخصات کانفیگ VPN را سریعاً از نظر امنیت، پروتکل و آسیب‌پذیری فیلترینگ بررسی کنید:
                پروتکل: $protocol
                سرور: $server
                پورت: $port
                لایه امنیتی: $security
                نوع انتقال (Transport): $transport
                
                پاسخ کوتاه، فنی، تخصصی و به زبان فارسی همراه با نکات کلیدی ارائه دهید.
            """.trimIndent()

            val response = withTimeout(45_000L) {
                fastModel.generateContent(prompt)
            }
            val text = response.text
            if (!text.isNullOrBlank()) {
                AiResult.Success(text)
            } else {
                AiResult.Error("پاسخی از مدل هوش مصنوعی دریافت نشد / Empty response from AI")
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI request timed out", e)
            AiResult.Error("مهلت درخواست به پایان رسید (Timeout) / Request timed out")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI generation failed", e)
            AiResult.Error(e.localizedMessage ?: "خطا در برقراری ارتباط با مدل هوش مصنوعی")
        }
    }

    /**
     * Complex topological and failover reasoning using gemini-4-argon with High Thinking
     */
    suspend fun optimizeRoutingAndFailover(
        serversSummary: String,
        activeLatency: String,
        userQuery: String
    ): AiResult {
        return try {
            val prompt = """
                شما معمار ارشد زیرساخت شبکه و سیستم‌های فیلترشکن در پلتفرم سازمانی TMS VPN هستید.
                ما به یک تحلیل عمیق، استراتژی مسیریابی هوشمند و سیاست شکست‌ناپذیر Failover نیاز داریم.
                
                وضعیت سرورها و زیرساخت کنونی:
                $serversSummary
                
                تاخیرها و پینگ‌های زنده:
                $activeLatency
                
                درخواست کاربر یا چالش شبکه:
                $userQuery
                
                لطفاً با استدلال عمیق مهندسی، معماری توزیع ترافیک، اولویت‌بندی بر اساس پینگ و ثبات سرورها، و استراتژی عبور از اختلالات شدید اینترنت را به صورت حرفه‌ای، گام‌به‌گام و به فارسی بنویسید.
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
}
