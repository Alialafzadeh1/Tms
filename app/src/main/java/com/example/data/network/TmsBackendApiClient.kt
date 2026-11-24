package com.example.data.network

import com.example.data.model.AppReleaseInfo
import com.example.data.model.BackendResult
import com.example.data.model.BackendSessionSync
import com.example.data.model.CryptoAsset
import com.example.data.model.CryptoInvoiceStatus
import com.example.data.model.CryptoPaymentInvoice
import com.example.data.model.FreeTrialStatus
import com.example.data.model.ReferralDashboardData
import com.example.data.model.ReferralItem
import com.example.data.model.ReferralRewardRequest
import com.example.data.model.ReferralRewardStatus
import com.example.data.subscription.ReferralAndCryptoCalculator
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.util.concurrent.TimeUnit

object ApkDownloadUrlValidator {
    const val APPROVED_DOWNLOAD_HOST = "tms-vpn-bot-core.24z1qne0u9xs.workers.dev"

    sealed interface ValidationResult {
        data class Valid(val normalizedUrl: String) : ValidationResult
        data class Invalid(val reasonFa: String, val reasonEn: String) : ValidationResult
    }

    fun validate(rawUrl: String?): ValidationResult {
        val trimmed = rawUrl?.trim().orEmpty()
        if (trimmed.isEmpty()) {
            return ValidationResult.Invalid(
                reasonFa = "آدرس دانلود فایل APK نامعتبر یا خالی است.",
                reasonEn = "APK download URL is missing or empty."
            )
        }
        if (trimmed.any { it.isWhitespace() || it.code < 0x20 || it.code == 0x7F }) {
            return ValidationResult.Invalid(
                reasonFa = "آدرس دانلود فایل APK دارای کاراکترهای نامعتبر است.",
                reasonEn = "APK download URL contains invalid characters."
            )
        }
        val uri = try {
            URI(trimmed)
        } catch (_: Exception) {
            return ValidationResult.Invalid(
                reasonFa = "ساختار آدرس دانلود فایل APK نامعتبر است.",
                reasonEn = "Malformed APK download URL."
            )
        }

        val scheme = uri.scheme?.lowercase()
        if (scheme != "https") {
            return ValidationResult.Invalid(
                reasonFa = "فقط لینک‌های امن HTTPS برای دانلود APK مجاز هستند.",
                reasonEn = "Only HTTPS URLs are permitted for APK downloads."
            )
        }

        if (!uri.rawUserInfo.isNullOrEmpty() || uri.rawAuthority?.contains("@") == true) {
            return ValidationResult.Invalid(
                reasonFa = "آدرس دانلود نامعتبر است (اطلاعات کاربری در URL مجاز نیست).",
                reasonEn = "Untrusted APK download URL format."
            )
        }

        val host = uri.host?.lowercase()
        if (host != APPROVED_DOWNLOAD_HOST) {
            return ValidationResult.Invalid(
                reasonFa = "دامنه لینک دانلود معتبر نیست. فقط سرور رسمی برنامه مجاز است.",
                reasonEn = "Untrusted APK download host. Only the official backend host is permitted."
            )
        }

        if (uri.port != -1 && uri.port != 443) {
            return ValidationResult.Invalid(
                reasonFa = "پورت آدرس دانلود مجاز نیست.",
                reasonEn = "Non-standard port is not permitted for APK download URLs."
            )
        }

        val path = uri.rawPath
        if (path.isNullOrBlank() || !path.startsWith("/")) {
            return ValidationResult.Invalid(
                reasonFa = "مسیر فایل در لینک دانلود نامعتبر است.",
                reasonEn = "APK download URL path is invalid."
            )
        }

        return try {
            uri.toURL()
            ValidationResult.Valid(uri.toASCIIString())
        } catch (_: Exception) {
            ValidationResult.Invalid(
                reasonFa = "ساختار آدرس دانلود فایل APK نامعتبر است.",
                reasonEn = "Malformed APK download URL."
            )
        }
    }

    fun isValid(rawUrl: String?): Boolean = validate(rawUrl) is ValidationResult.Valid
}

/**
 * Production HTTP client communicating with TMS Cloudflare Worker (`tms-vpn-bot-core`)
 * backed by D1 (`tms-vpn-database`) and R2 APK releases.
 *
 * Security:
 * - Zero embedded bot tokens, admin secrets, or Cloudflare API keys.
 * - Attaches fresh Firebase ID Token (`Authorization: Bearer <token>`) on protected routes.
 * - Retries once with forced ID token refresh if the backend replies HTTP 401.
 * - Never fabricates success payloads when offline or when a route returns 4xx/5xx.
 */
class TmsBackendApiClient(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val httpClient: OkHttpClient = defaultOkHttpClient(),
    private val tokenProvider: suspend (forceRefresh: Boolean) -> String? = defaultFirebaseTokenProvider()
) {

    companion object {
        const val DEFAULT_BASE_URL = "https://tms-vpn-bot-core.24z1qne0u9xs.workers.dev"
        const val MAX_APK_UPLOAD_BYTES = 100L * 1024L * 1024L
        private const val UPLOAD_CHUNK_BYTES = 64 * 1024
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val APK_MEDIA_TYPE = "application/vnd.android.package-archive".toMediaType()

        fun defaultOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(180, TimeUnit.SECONDS)
            .build()

        fun defaultFirebaseTokenProvider(): suspend (Boolean) -> String? = { forceRefresh ->
            val user = runCatching { Firebase.auth.currentUser }.getOrNull()
            if (user != null) {
                runCatching { user.getIdToken(forceRefresh).await().token }.getOrNull()
            } else {
                null
            }
        }
    }

    private fun normalizedBase(): String = baseUrl.trimEnd('/')

    // --- SESSION SYNC & AUTHORITATIVE ROLE CHECK ---

    suspend fun syncSession(): BackendResult<BackendSessionSync> {
        return executeJsonRequest(method = "GET", path = "/api/app/session", body = null) { json ->
            val data = json.optJSONObject("data") ?: json
            val subObj = data.optJSONObject("subscription")
            val trialObj = data.optJSONObject("trial")
            BackendSessionSync(
                uid = data.optString("uid", ""),
                email = data.optString("email", ""),
                isAdmin = data.optBoolean("isAdmin", false) ||
                    data.optString("role", "").equals("SUPER_ADMIN", ignoreCase = true),
                verifiedRole = data.optString("role", "").ifBlank { null },
                telegramLinked = data.optBoolean("telegramLinked", false),
                telegramId = data.optString("telegramId", "").ifBlank { null },
                telegramUsername = data.optString("telegramUsername", "").ifBlank { null },
                referralCode = data.optString("referralCode", ""),
                referralShareUrl = data.optString("referralShareUrl", ""),
                serverTimeMs = data.optLong("serverTimeMs", System.currentTimeMillis()),
                activePlanId = subObj?.optString("planId", "")?.ifBlank { null },
                activeStatus = subObj?.optString("status", "")?.ifBlank { null },
                activeExpiresAtMs = subObj?.takeIf { it.has("expiresAtMs") && !it.isNull("expiresAtMs") }
                    ?.optLong("expiresAtMs"),
                trialStatus = trialObj?.let(::parseTrialStatus)
            )
        }
    }

    // --- CRYPTO PAYMENTS (USDT TRC20 / TRX) ---

    suspend fun createCryptoInvoice(
        planId: String,
        asset: CryptoAsset
    ): BackendResult<CryptoPaymentInvoice> {
        val payload = JSONObject().apply {
            put("planId", planId)
            put("asset", asset.id)
        }
        return executeJsonRequest(
            method = "POST",
            path = "/api/app/payments/crypto/invoices",
            body = payload
        ) { json ->
            parseCryptoInvoice(json.optJSONObject("invoice") ?: json.optJSONObject("data") ?: json)
        }
    }

    suspend fun listCryptoInvoices(isAdminScope: Boolean = false): BackendResult<List<CryptoPaymentInvoice>> {
        val path = if (isAdminScope) "/api/admin/payments/crypto/invoices" else "/api/app/payments/crypto/invoices"
        return executeJsonRequest(method = "GET", path = path, body = null) { json ->
            val arr = json.optJSONArray("invoices") ?: json.optJSONArray("data") ?: JSONArray()
            buildList {
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { add(parseCryptoInvoice(it)) }
                }
            }
        }
    }

    suspend fun verifyCryptoInvoice(
        invoiceId: String,
        txHash: String
    ): BackendResult<CryptoPaymentInvoice> {
        val payload = JSONObject().apply {
            put("txHash", txHash.trim())
        }
        return executeJsonRequest(
            method = "POST",
            path = "/api/app/payments/crypto/invoices/${invoiceId.trim()}/verify",
            body = payload
        ) { json ->
            parseCryptoInvoice(json.optJSONObject("invoice") ?: json.optJSONObject("data") ?: json)
        }
    }

    // --- REFERRAL PROGRAM ---

    suspend fun qualifyReferralOnLogin(referralCode: String? = null): BackendResult<Boolean> {
        val payload = JSONObject().apply {
            if (!referralCode.isNullOrBlank()) {
                put("referralCode", referralCode.trim())
            }
        }
        return executeJsonRequest(
            method = "POST",
            path = "/api/app/referrals/qualify",
            body = payload
        ) { json ->
            json.optBoolean("qualified", true)
        }
    }

    suspend fun getReferralDashboard(): BackendResult<ReferralDashboardData> {
        return executeJsonRequest(method = "GET", path = "/api/app/referrals", body = null) { json ->
            val data = json.optJSONObject("data") ?: json
            val referralsArr = data.optJSONArray("referrals") ?: JSONArray()
            val requestsArr = data.optJSONArray("rewardRequests") ?: JSONArray()

            val referrals = buildList {
                for (i in 0 until referralsArr.length()) {
                    val obj = referralsArr.optJSONObject(i) ?: continue
                    add(
                        ReferralItem(
                            referralId = obj.optString("id", obj.optString("referralId", "ref_$i")),
                            referredDisplay = obj.optString("referredDisplay", obj.optString("referredEmail", "User")),
                            botStarted = obj.optBoolean("botStarted", false),
                            apkDownloadClicked = obj.optBoolean("apkDownloadClicked", false),
                            appLoggedIn = obj.optBoolean("appLoggedIn", false),
                            qualified = obj.optBoolean("qualified", false),
                            createdAtMs = obj.optLong("createdAtMs", 0L)
                        )
                    )
                }
            }
            val rewardRequests = buildList {
                for (i in 0 until requestsArr.length()) {
                    val obj = requestsArr.optJSONObject(i) ?: continue
                    add(parseRewardRequest(obj))
                }
            }
            val qualifiedCount = data.optInt("qualifiedCount", referrals.count { it.qualified })
            val milestones = ReferralAndCryptoCalculator.evaluateMilestones(qualifiedCount, rewardRequests)
            ReferralDashboardData(
                referralCode = data.optString("referralCode", ""),
                referralShareUrl = data.optString("referralShareUrl", ""),
                totalInvitedCount = data.optInt("totalInvitedCount", referrals.size),
                qualifiedCount = qualifiedCount,
                totalUnlockedGb = data.optDouble(
                    "totalUnlockedGb",
                    ReferralAndCryptoCalculator.calculateTotalUnlockedGb(qualifiedCount)
                ),
                milestones = milestones,
                referrals = referrals,
                rewardRequests = rewardRequests
            )
        }
    }

    suspend fun claimReferralReward(milestoneThreshold: Int): BackendResult<ReferralRewardRequest> {
        val payload = JSONObject().apply {
            put("milestoneThreshold", milestoneThreshold)
        }
        return executeJsonRequest(
            method = "POST",
            path = "/api/app/referrals/rewards/claim",
            body = payload
        ) { json ->
            parseRewardRequest(json.optJSONObject("rewardRequest") ?: json.optJSONObject("data") ?: json)
        }
    }

    suspend fun adminListReferralRewardRequests(): BackendResult<List<ReferralRewardRequest>> {
        return executeJsonRequest(method = "GET", path = "/api/admin/referrals/rewards", body = null) { json ->
            val arr = json.optJSONArray("requests") ?: json.optJSONArray("data") ?: JSONArray()
            buildList {
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { add(parseRewardRequest(it)) }
                }
            }
        }
    }

    suspend fun adminReviewReferralReward(
        requestId: String,
        approve: Boolean,
        adminNote: String
    ): BackendResult<ReferralRewardRequest> {
        val payload = JSONObject().apply {
            put("decision", if (approve) "APPROVE" else "REJECT")
            put("adminNote", adminNote.trim())
        }
        return executeJsonRequest(
            method = "POST",
            path = "/api/admin/referrals/rewards/${requestId.trim()}/review",
            body = payload
        ) { json ->
            parseRewardRequest(json.optJSONObject("rewardRequest") ?: json.optJSONObject("data") ?: json)
        }
    }

    // --- 1-DAY FREE TRIAL (1 PER 7 DAYS SERVER-SIDE) ---

    suspend fun getFreeTrialStatus(): BackendResult<FreeTrialStatus> {
        return executeJsonRequest(method = "GET", path = "/api/app/trial/status", body = null) { json ->
            parseTrialStatus(json.optJSONObject("trial") ?: json.optJSONObject("data") ?: json)
        }
    }

    suspend fun activateFreeTrial(): BackendResult<FreeTrialStatus> {
        return executeJsonRequest(
            method = "POST",
            path = "/api/app/trial/activate",
            body = JSONObject()
        ) { json ->
            parseTrialStatus(json.optJSONObject("trial") ?: json.optJSONObject("data") ?: json)
        }
    }

    // --- DYNAMIC R2 APK RELEASES ---

    suspend fun getLatestRelease(): BackendResult<AppReleaseInfo> {
        return executeJsonRequest(
            method = "GET",
            path = "/api/app/releases/latest",
            body = null,
            requireAuth = false
        ) { json ->
            parseReleaseInfo(json.optJSONObject("release") ?: json.optJSONObject("data") ?: json)
        }
    }

    suspend fun recordReleaseDownload(releaseId: String): BackendResult<AppReleaseInfo> {
        return executeJsonRequest(
            method = "POST",
            path = "/api/app/releases/${releaseId.trim()}/download",
            body = JSONObject(),
            requireAuth = false
        ) { json ->
            parseReleaseInfo(json.optJSONObject("release") ?: json.optJSONObject("data") ?: json)
        }
    }

    suspend fun adminListReleases(): BackendResult<List<AppReleaseInfo>> {
        return executeJsonRequest(method = "GET", path = "/api/admin/releases", body = null) { json ->
            val arr = json.optJSONArray("releases") ?: json.optJSONArray("data") ?: JSONArray()
            buildList {
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { add(parseReleaseInfo(it)) }
                }
            }
        }
    }

    suspend fun adminSetReleasePublished(
        releaseId: String,
        published: Boolean
    ): BackendResult<AppReleaseInfo> {
        val payload = JSONObject().apply {
            put("isPublished", published)
        }
        return executeJsonRequest(
            method = "POST",
            path = "/api/admin/releases/${releaseId.trim()}/publish",
            body = payload
        ) { json ->
            parseReleaseInfo(json.optJSONObject("release") ?: json.optJSONObject("data") ?: json)
        }
    }

    suspend fun adminUploadApkRelease(
        fileName: String,
        apkBytes: ByteArray,
        versionName: String,
        versionCode: Int,
        releaseNotes: String,
        publishImmediately: Boolean,
        onProgress: (bytesUploaded: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): BackendResult<AppReleaseInfo> {
        if (apkBytes.isEmpty()) {
            return BackendResult.HttpError(400, "EMPTY_APK", "فایل APK خالی است")
        }
        return adminUploadApkReleaseStream(
            fileName = fileName,
            fileSizeBytes = apkBytes.size.toLong(),
            versionName = versionName,
            versionCode = versionCode,
            releaseNotes = releaseNotes,
            publishImmediately = publishImmediately,
            streamProvider = { ByteArrayInputStream(apkBytes) },
            onProgress = onProgress
        )
    }

    suspend fun adminUploadApkReleaseStream(
        fileName: String,
        fileSizeBytes: Long,
        versionName: String,
        versionCode: Int,
        releaseNotes: String,
        publishImmediately: Boolean,
        streamProvider: () -> InputStream?,
        onProgress: (bytesUploaded: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): BackendResult<AppReleaseInfo> = withContext(Dispatchers.IO) {
        if (fileSizeBytes == 0L) {
            return@withContext BackendResult.HttpError(400, "EMPTY_APK", "فایل APK خالی است")
        }
        if (fileSizeBytes > MAX_APK_UPLOAD_BYTES) {
            return@withContext BackendResult.HttpError(
                413,
                "PAYLOAD_TOO_LARGE",
                "حجم فایل APK بیش از حد مجاز (۱۰۰ مگابایت) است."
            )
        }
        val token = tokenProvider(false) ?: return@withContext BackendResult.Unauthenticated

        suspend fun doUpload(authToken: String): BackendResult<AppReleaseInfo> {
            val streamingBody = object : RequestBody() {
                override fun contentType(): MediaType = APK_MEDIA_TYPE

                override fun contentLength(): Long = if (fileSizeBytes > 0L) fileSizeBytes else -1L

                override fun writeTo(sink: BufferedSink) {
                    val input = streamProvider()
                        ?: throw IOException("Unable to open APK file stream")
                    input.use { stream ->
                        val buffer = ByteArray(UPLOAD_CHUNK_BYTES)
                        var uploaded = 0L
                        while (true) {
                            val read = stream.read(buffer)
                            if (read == -1) break
                            uploaded += read
                            if (uploaded > MAX_APK_UPLOAD_BYTES) {
                                throw IOException("APK file exceeds maximum allowed upload limit (100 MB)")
                            }
                            sink.write(buffer, 0, read)
                            onProgress(uploaded, fileSizeBytes)
                        }
                        if (uploaded == 0L) {
                            throw IOException("Selected APK stream is empty")
                        }
                        sink.flush()
                    }
                }
            }

            val multipart = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("versionName", versionName.trim())
                .addFormDataPart("versionCode", versionCode.toString())
                .addFormDataPart("releaseNotes", releaseNotes.trim())
                .addFormDataPart("isPublished", publishImmediately.toString())
                .addFormDataPart(
                    "file",
                    fileName.ifBlank { "tms-vpn.apk" },
                    streamingBody
                )
                .build()

            val request = Request.Builder()
                .url("${normalizedBase()}/api/admin/releases/upload")
                .header("Authorization", "Bearer $authToken")
                .post(multipart)
                .build()

            val result = executeAndParse(request) { json ->
                parseReleaseInfo(json.optJSONObject("release") ?: json.optJSONObject("data") ?: json)
            }
            return if (result is BackendResult.HttpError && result.httpCode == 413) {
                BackendResult.HttpError(
                    httpCode = 413,
                    errorCode = result.errorCode,
                    message = "حجم فایل APK از حد مجاز سرور بیشتر است (HTTP 413)."
                )
            } else {
                result
            }
        }

        val first = doUpload(token)
        if (first is BackendResult.HttpError && first.httpCode == 401) {
            val refreshed = tokenProvider(true) ?: return@withContext BackendResult.Unauthenticated
            return@withContext doUpload(refreshed)
        }
        return@withContext first
    }

    // --- CORE REQUEST EXECUTION & 401 TOKEN REFRESH ---

    private suspend fun <T> executeJsonRequest(
        method: String,
        path: String,
        body: JSONObject?,
        requireAuth: Boolean = true,
        mapper: (JSONObject) -> T
    ): BackendResult<T> {
        val token = if (requireAuth) {
            tokenProvider(false) ?: return BackendResult.Unauthenticated
        } else {
            tokenProvider(false)
        }

        fun buildRequest(activeToken: String?): Request {
            val builder = Request.Builder().url("${normalizedBase()}$path")
            if (!activeToken.isNullOrBlank()) {
                builder.header("Authorization", "Bearer $activeToken")
            }
            when (method.uppercase()) {
                "GET" -> builder.get()
                else -> {
                    val reqBody = (body ?: JSONObject()).toString().toRequestBody(JSON_MEDIA_TYPE)
                    builder.method(method.uppercase(), reqBody)
                }
            }
            return builder.build()
        }

        val firstAttempt = executeAndParse(buildRequest(token), mapper)
        if (requireAuth && firstAttempt is BackendResult.HttpError && firstAttempt.httpCode == 401) {
            val refreshedToken = tokenProvider(true) ?: return BackendResult.Unauthenticated
            return executeAndParse(buildRequest(refreshedToken), mapper)
        }
        return firstAttempt
    }

    private suspend fun <T> executeAndParse(
        request: Request,
        mapper: (JSONObject) -> T
    ): BackendResult<T> = withContext(Dispatchers.IO) {
        try {
            httpClient.newCall(request).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                val json = runCatching {
                    if (rawBody.isBlank()) JSONObject() else JSONObject(rawBody)
                }.getOrElse { JSONObject() }

                if (response.isSuccessful) {
                    return@withContext BackendResult.Success(mapper(json))
                }

                val errorCode = json.optString("errorCode", json.optString("error", "HTTP_${response.code}"))
                val msg = json.optString(
                    "message",
                    json.optString("errorMessage", "HTTP ${response.code}")
                ).ifBlank { "HTTP ${response.code}" }

                return@withContext BackendResult.HttpError(
                    httpCode = response.code,
                    errorCode = errorCode,
                    message = msg
                )
            }
        } catch (ioe: IOException) {
            return@withContext BackendResult.NetworkError(
                ioe.localizedMessage ?: "Network communication failed"
            )
        } catch (t: Throwable) {
            return@withContext BackendResult.NetworkError(
                t.localizedMessage ?: "Unexpected backend client error"
            )
        }
    }

    // --- JSON MAPPERS ---

    private fun parseCryptoInvoice(obj: JSONObject): CryptoPaymentInvoice {
        return CryptoPaymentInvoice(
            invoiceId = obj.optString("invoiceId", obj.optString("id", "")),
            userId = obj.optString("userId", ""),
            planId = obj.optString("planId", "plan_1m"),
            asset = CryptoAsset.fromString(obj.optString("asset", "USDT_TRC20")),
            network = obj.optString("network", "TRC20"),
            depositAddress = obj.optString("depositAddress", ""),
            expectedAmountCrypto = obj.optString("expectedAmountCrypto", ""),
            expectedAmountToman = obj.optLong("expectedAmountToman", 0L),
            lockedRateToman = obj.optLong("lockedRateToman", 0L),
            status = CryptoInvoiceStatus.fromString(obj.optString("status", "PENDING")),
            txHash = obj.optString("txHash", ""),
            receivedAmountCrypto = obj.optString("receivedAmountCrypto", ""),
            expiresAtMs = obj.optLong("expiresAtMs", 0L),
            createdAtMs = obj.optLong("createdAtMs", 0L),
            statusMessage = obj.optString("statusMessage", "")
        )
    }

    private fun parseRewardRequest(obj: JSONObject): ReferralRewardRequest {
        return ReferralRewardRequest(
            requestId = obj.optString("requestId", obj.optString("id", "")),
            userId = obj.optString("userId", ""),
            userEmail = obj.optString("userEmail", ""),
            milestoneThreshold = obj.optInt("milestoneThreshold", 2),
            rewardGb = obj.optDouble("rewardGb", 1.0),
            status = ReferralRewardStatus.fromString(obj.optString("status", "PENDING")),
            adminNote = obj.optString("adminNote", ""),
            createdAtMs = obj.optLong("createdAtMs", 0L),
            reviewedAtMs = obj.takeIf { it.has("reviewedAtMs") && !it.isNull("reviewedAtMs") }
                ?.optLong("reviewedAtMs")
        )
    }

    private fun parseTrialStatus(obj: JSONObject): FreeTrialStatus {
        return FreeTrialStatus(
            eligible = obj.optBoolean("eligible", false),
            active = obj.optBoolean("active", false),
            cooldownRemainingSeconds = obj.optLong("cooldownRemainingSeconds", 0L),
            activatedAtMs = obj.takeIf { it.has("activatedAtMs") && !it.isNull("activatedAtMs") }
                ?.optLong("activatedAtMs"),
            expiresAtMs = obj.takeIf { it.has("expiresAtMs") && !it.isNull("expiresAtMs") }
                ?.optLong("expiresAtMs"),
            nextEligibleAtMs = obj.takeIf { it.has("nextEligibleAtMs") && !it.isNull("nextEligibleAtMs") }
                ?.optLong("nextEligibleAtMs"),
            cooldownDays = obj.optInt("cooldownDays", 7),
            durationHours = obj.optInt("durationHours", 24),
            lastReason = obj.optString("reason", obj.optString("message", ""))
        )
    }

    private fun parseReleaseInfo(obj: JSONObject): AppReleaseInfo {
        return AppReleaseInfo(
            releaseId = obj.optString("releaseId", obj.optString("id", "")),
            versionName = obj.optString("versionName", ""),
            versionCode = obj.optInt("versionCode", 0),
            releaseNotes = obj.optString("releaseNotes", ""),
            fileSizeBytes = obj.optLong("fileSizeBytes", 0L),
            uploadedAtMs = obj.optLong("uploadedAtMs", 0L),
            downloadCount = obj.optLong("downloadCount", 0L),
            downloadUrl = obj.optString("downloadUrl", ""),
            isPublished = obj.optBoolean("isPublished", false),
            sha256 = obj.optString("sha256", "")
        )
    }
}
