package com.example.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class UbuntuNodeHealthResult(
    val isReachable: Boolean,
    val xrayRunning: Boolean = false,
    val activeUsers: Int = 0,
    val uptimeSeconds: Long = 0L,
    val publicHost: String = "",
    val errorMessage: String? = null
)

data class ProvisionedUserConfigResult(
    val isSuccess: Boolean,
    val clientUuid: String = "",
    val vlessUri: String = "",
    val serverHost: String = "",
    val port: Int = 443,
    val errorMessage: String? = null
)

/**
 * Client for the Ubuntu 24.04 TMS Xray Node Agent REST API (deploy/ubuntu24_tms_xray_setup.sh).
 * Enables admins to check real node health and provision private per-user VLESS Reality UUIDs.
 */
object NodeControlApiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun checkNodeHealth(baseUrl: String, apiToken: String): UbuntuNodeHealthResult =
        withContext(Dispatchers.IO) {
            val cleanBase = baseUrl.trim().trimEnd('/')
            if (!cleanBase.startsWith("http://") && !cleanBase.startsWith("https://")) {
                return@withContext UbuntuNodeHealthResult(
                    isReachable = false,
                    errorMessage = "آدرس API سرور باید با http:// یا https:// شروع شود"
                )
            }
            try {
                val request = Request.Builder()
                    .url("$cleanBase/api/v1/health")
                    .header("Authorization", "Bearer ${apiToken.trim()}")
                    .get()
                    .build()
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        return@withContext UbuntuNodeHealthResult(
                            isReachable = false,
                            errorMessage = "پاسخ نامعتبر از نود Ubuntu (کد ${response.code})"
                        )
                    }
                    val json = JSONObject(body)
                    UbuntuNodeHealthResult(
                        isReachable = true,
                        xrayRunning = json.optBoolean("xray_running", false),
                        activeUsers = json.optInt("active_users", 0),
                        uptimeSeconds = json.optLong("uptime_seconds", 0L),
                        publicHost = json.optString("public_host", "")
                    )
                }
            } catch (e: Exception) {
                UbuntuNodeHealthResult(
                    isReachable = false,
                    errorMessage = e.localizedMessage ?: "ارتباط با نود Ubuntu برقرار نشد"
                )
            }
        }

    suspend fun provisionPrivateVlessUser(
        baseUrl: String,
        apiToken: String,
        userId: String,
        userEmail: String,
        quotaGb: Int = 100
    ): ProvisionedUserConfigResult = withContext(Dispatchers.IO) {
        val cleanBase = baseUrl.trim().trimEnd('/')
        if (!cleanBase.startsWith("http://") && !cleanBase.startsWith("https://")) {
            return@withContext ProvisionedUserConfigResult(
                isSuccess = false,
                errorMessage = "آدرس API سرور نامعتبر است"
            )
        }
        try {
            val payload = JSONObject().apply {
                put("user_id", userId)
                put("email", userEmail.ifBlank { "$userId@tmsvpn.local" })
                put("quota_gb", quotaGb)
            }
            val request = Request.Builder()
                .url("$cleanBase/api/v1/users/provision")
                .header("Authorization", "Bearer ${apiToken.trim()}")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext ProvisionedUserConfigResult(
                        isSuccess = false,
                        errorMessage = "خطا در تولید اکانت اختصاصی (کد ${response.code})"
                    )
                }
                val json = JSONObject(body)
                val vlessUri = json.optString("vless_uri", "")
                if (vlessUri.isBlank()) {
                    return@withContext ProvisionedUserConfigResult(
                        isSuccess = false,
                        errorMessage = "پاسخ سرور فاقد لینک vless_uri است"
                    )
                }
                ProvisionedUserConfigResult(
                    isSuccess = true,
                    clientUuid = json.optString("uuid", ""),
                    vlessUri = vlessUri,
                    serverHost = json.optString("host", ""),
                    port = json.optInt("port", 443)
                )
            }
        } catch (e: Exception) {
            ProvisionedUserConfigResult(
                isSuccess = false,
                errorMessage = e.localizedMessage ?: "خطا در ارتباط با کنترل پنل Ubuntu"
            )
        }
    }
}
