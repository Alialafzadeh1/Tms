package com.example.data.network

import android.util.Base64
import com.example.data.model.VpnConfig
import com.example.data.parser.VpnConfigParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class SubscriptionFetchResult(
    val isSuccess: Boolean,
    val configs: List<VpnConfig> = emptyList(),
    val errorMessage: String? = null
)

/**
 * Fetches and decodes Xray / V2Ray HTTP(S) subscription URLs (plain URI lists or Base64 blocks)
 * into parsed VpnConfig entities.
 */
object SubscriptionImporter {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun fetchSubscription(url: String, userId: String): SubscriptionFetchResult =
        withContext(Dispatchers.IO) {
            val trimmedUrl = url.trim()
            if (!trimmedUrl.startsWith("http://", ignoreCase = true) &&
                !trimmedUrl.startsWith("https://", ignoreCase = true)
            ) {
                return@withContext SubscriptionFetchResult(
                    isSuccess = false,
                    errorMessage = "لینک سابسکریپشن باید با http:// یا https:// شروع شود"
                )
            }

            try {
                val request = Request.Builder()
                    .url(trimmedUrl)
                    .header("User-Agent", "TMS-VPN-Android/1.0")
                    .get()
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext SubscriptionFetchResult(
                            isSuccess = false,
                            errorMessage = "خطای سرور سابسکریپشن (کد ${response.code})"
                        )
                    }
                    val body = response.body?.string().orEmpty()
                    val configs = parseSubscriptionPayload(body, userId)
                    if (configs.isEmpty()) {
                        SubscriptionFetchResult(
                            isSuccess = false,
                            errorMessage = "هیچ کانفیگ معتبری در پاسخ سابسکریپشن یافت نشد"
                        )
                    } else {
                        SubscriptionFetchResult(isSuccess = true, configs = configs)
                    }
                }
            } catch (e: Exception) {
                SubscriptionFetchResult(
                    isSuccess = false,
                    errorMessage = "خطا در دریافت سابسکریپشن: ${e.localizedMessage ?: "اتصال برقرار نشد"}"
                )
            }
        }

    fun parseSubscriptionPayload(rawPayload: String, userId: String): List<VpnConfig> {
        val trimmed = rawPayload.trim()
        if (trimmed.isBlank()) return emptyList()

        val decodedText = if (
            trimmed.contains("vless://", ignoreCase = true) ||
            trimmed.contains("vmess://", ignoreCase = true) ||
            trimmed.contains("trojan://", ignoreCase = true) ||
            trimmed.contains("ss://", ignoreCase = true)
        ) {
            trimmed
        } else {
            tryDecodeBase64(trimmed) ?: trimmed
        }

        return VpnConfigParser.parseMultiple(decodedText, userId)
            .filter { it.isSuccess && it.config != null }
            .mapNotNull { it.config }
    }

    private fun tryDecodeBase64(input: String): String? {
        return try {
            val normalized = input.replace("\\s".toRegex(), "")
            val flags = if (normalized.contains('-') || normalized.contains('_')) {
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            } else {
                Base64.DEFAULT
            }
            val bytes = Base64.decode(normalized, flags)
            String(bytes, Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }
}
