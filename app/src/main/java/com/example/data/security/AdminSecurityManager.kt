package com.example.data.security

import android.content.Context
import android.util.Base64
import com.example.data.model.AdminRole
import com.example.data.model.AuthAuditLog
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class SessionToken(
    val rawToken: String,
    val userId: String,
    val role: AdminRole,
    val issuedAtMs: Long,
    val expiresAtMs: Long
)

sealed interface AuthAttemptResult {
    data class Success(val sessionToken: SessionToken) : AuthAttemptResult
    data class InvalidCredentials(val remainingAttempts: Int, val messageFa: String, val messageEn: String) : AuthAttemptResult
    data class LockedOut(val remainingLockoutSeconds: Long, val messageFa: String, val messageEn: String) : AuthAttemptResult
}

class AdminSecurityManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("tms_admin_security_prefs", Context.MODE_PRIVATE)
    private val secureRandom = SecureRandom()

    private val hmacSecretKey: ByteArray by lazy {
        val existing = prefs.getString("security_hmac_secret", null)
        if (existing != null) {
            Base64.decode(existing, Base64.NO_WRAP)
        } else {
            val keyBytes = ByteArray(32)
            secureRandom.nextBytes(keyBytes)
            prefs.edit().putString("security_hmac_secret", Base64.encodeToString(keyBytes, Base64.NO_WRAP)).commit()
            keyBytes
        }
    }

    init {
        if (!prefs.contains("emergency_recovery_token")) {
            prefs.edit()
                .putString("emergency_recovery_token", generateSecureRecoveryToken())
                .commit()
        }
    }

    private fun generateSalt(): String {
        val saltBytes = ByteArray(16)
        secureRandom.nextBytes(saltBytes)
        return Base64.encodeToString(saltBytes, Base64.NO_WRAP)
    }

    private fun generateSecureRecoveryToken(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val sb = StringBuilder("TMS-REC-")
        repeat(12) {
            sb.append(alphabet[secureRandom.nextInt(alphabet.length)])
        }
        return sb.toString()
    }

    fun hasConfiguredPin(): Boolean {
        val salt = prefs.getString("admin_pin_salt", "").orEmpty()
        val hash = prefs.getString("admin_pin_hash", "").orEmpty()
        return salt.isNotBlank() && hash.isNotBlank()
    }

    fun isCustomPinConfigured(): Boolean = prefs.getBoolean("pin_is_custom_set", false) && hasConfiguredPin()

    fun configureInitialPin(
        userId: String,
        role: AdminRole,
        newPin: String
    ): Pair<Boolean, String> {
        if (hasConfiguredPin()) {
            return Pair(false, "پین امنیتی از قبل تنظیم شده است / Security PIN is already configured")
        }
        val trimmed = newPin.trim()
        if (trimmed.length < 6) {
            return Pair(false, "پین جدید باید حداقل ۶ رقم یا کاراکتر باشد / PIN must be at least 6 characters")
        }
        val newSalt = generateSalt()
        val newHash = hashCredential(trimmed, newSalt)
        prefs.edit()
            .putString("admin_pin_salt", newSalt)
            .putString("admin_pin_hash", newHash)
            .putBoolean("pin_is_custom_set", true)
            .putInt("failed_attempts_count", 0)
            .remove("lockout_until_ms")
            .commit()
        return Pair(true, "پین امنیتی اختصاصی با موفقیت ثبت شد / Custom admin PIN configured")
    }

    fun hashCredential(input: String, salt: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(Base64.decode(salt, Base64.NO_WRAP))
        var hashed = md.digest(input.toByteArray(Charsets.UTF_8))
        // 10,000 iterations key stretching (bcrypt-equivalent computational resistance)
        for (i in 0 until 10_000) {
            md.reset()
            hashed = md.digest(hashed)
        }
        return Base64.encodeToString(hashed, Base64.NO_WRAP)
    }

    private fun hashesEqual(a: String, b: String): Boolean {
        if (a.isBlank() || b.isBlank()) return false
        return MessageDigest.isEqual(
            a.toByteArray(Charsets.UTF_8),
            b.toByteArray(Charsets.UTF_8)
        )
    }

    fun isLockedOut(): Pair<Boolean, Long> {
        val lockoutUntil = prefs.getLong("lockout_until_ms", 0L)
        val now = System.currentTimeMillis()
        return if (lockoutUntil > now) {
            val remainingSec = (lockoutUntil - now) / 1000L
            Pair(true, remainingSec)
        } else {
            Pair(false, 0L)
        }
    }

    fun verifyAdminCredential(
        userId: String,
        role: AdminRole,
        pinOrPassword: String,
        deviceInfo: String
    ): Pair<AuthAttemptResult, AuthAuditLog> {
        val (locked, remainingSec) = isLockedOut()
        if (locked) {
            val auditLog = AuthAuditLog(
                userId = userId,
                action = "AUTH_BLOCKED_LOCKOUT",
                role = role.id,
                module = "Authentication",
                status = "LOCKED_OUT",
                ipOrDevice = deviceInfo,
                details = "تلاش در زمان قفل امنیتی / Attempt during active lockout ($remainingSec s remaining)"
            )
            return Pair(
                AuthAttemptResult.LockedOut(
                    remainingLockoutSeconds = remainingSec,
                    messageFa = "حساب مدیریتی به دلیل تلاش‌های ناموفق مکرر موقتاً قفل شده است ($remainingSec ثانیه باقی‌مانده)",
                    messageEn = "Account temporarily locked due to consecutive failed attempts ($remainingSec s remaining)"
                ),
                auditLog
            )
        }

        val storedSalt = prefs.getString("admin_pin_salt", "") ?: ""
        val storedHash = prefs.getString("admin_pin_hash", "") ?: ""
        if (storedSalt.isBlank() || storedHash.isBlank()) {
            val auditLog = AuthAuditLog(
                userId = userId,
                action = "ADMIN_PIN_NOT_CONFIGURED",
                role = role.id,
                module = "Authentication",
                status = "FAILED",
                ipOrDevice = deviceInfo,
                details = "پین امنیتی هنوز تنظیم نشده است / Security PIN not configured yet"
            )
            return Pair(
                AuthAttemptResult.InvalidCredentials(
                    remainingAttempts = 5,
                    messageFa = "پین امنیتی هنوز تنظیم نشده است؛ ابتدا یک پین جدید تعریف کنید یا از کلید ریکاوری استفاده کنید.",
                    messageEn = "No admin PIN configured yet. Set a custom PIN or use your recovery key."
                ),
                auditLog
            )
        }

        val computedHash = hashCredential(pinOrPassword.trim(), storedSalt)

        if (hashesEqual(computedHash, storedHash)) {
            // Success: reset failed attempts
            prefs.edit()
                .putInt("failed_attempts_count", 0)
                .remove("lockout_until_ms")
                .commit()

            val token = issueSessionToken(userId, role)
            val auditLog = AuthAuditLog(
                userId = userId,
                action = "ADMIN_AUTH_SUCCESS",
                role = role.id,
                module = "Authentication",
                status = "SUCCESS",
                ipOrDevice = deviceInfo,
                details = "احراز هویت پین و امضای نشست رمزنگاری شده با موفقیت صادر شد / Cryptographic session issued"
            )
            return Pair(AuthAttemptResult.Success(token), auditLog)
        } else {
            val failedCount = prefs.getInt("failed_attempts_count", 0) + 1
            prefs.edit().putInt("failed_attempts_count", failedCount).commit()

            if (failedCount >= 5) {
                val lockoutTime = System.currentTimeMillis() + (300_000L) // 5 minutes
                prefs.edit().putLong("lockout_until_ms", lockoutTime).commit()

                val auditLog = AuthAuditLog(
                    userId = userId,
                    action = "ADMIN_LOCKOUT_TRIGGERED",
                    role = role.id,
                    module = "Authentication",
                    status = "LOCKED_OUT",
                    ipOrDevice = deviceInfo,
                    details = "قفل امنیتی فعال شد پس از ۵ تلاش ناموفق / 5 consecutive failed attempts trigger"
                )
                return Pair(
                    AuthAttemptResult.LockedOut(
                        remainingLockoutSeconds = 300L,
                        messageFa = "به دلیل ۵ مرتبه ورود اشتباه، پنل مدیریت برای ۵ دقیقه مسدود شد.",
                        messageEn = "Maximum attempts exceeded. Management console locked for 5 minutes."
                    ),
                    auditLog
                )
            } else {
                val remaining = 5 - failedCount
                val auditLog = AuthAuditLog(
                    userId = userId,
                    action = "ADMIN_AUTH_FAILED",
                    role = role.id,
                    module = "Authentication",
                    status = "FAILED",
                    ipOrDevice = deviceInfo,
                    details = "کلمه عبور اشتباه ($failedCount از ۵) / Failed attempt ($failedCount/5)"
                )
                return Pair(
                    AuthAttemptResult.InvalidCredentials(
                        remainingAttempts = remaining,
                        messageFa = "کلمه عبور یا پین مدیریتی نادرست است ($remaining تلاش باقی‌مانده)",
                        messageEn = "Invalid security PIN ($remaining attempts remaining)"
                    ),
                    auditLog
                )
            }
        }
    }

    fun changeAdminPin(
        userId: String,
        role: AdminRole,
        oldPin: String,
        newPin: String
    ): Pair<Boolean, String> {
        val storedSalt = prefs.getString("admin_pin_salt", "") ?: ""
        val storedHash = prefs.getString("admin_pin_hash", "") ?: ""
        val computedOld = if (storedSalt.isNotBlank()) hashCredential(oldPin.trim(), storedSalt) else ""

        if (!hashesEqual(computedOld, storedHash)) {
            return Pair(false, "پین قبلی نامعتبر است / Incorrect current PIN")
        }

        if (newPin.trim().length < 6) {
            return Pair(false, "پین جدید باید حداقل ۶ رقم یا کاراکتر باشد / PIN must be at least 6 characters")
        }

        val newSalt = generateSalt()
        val newHash = hashCredential(newPin.trim(), newSalt)
        prefs.edit()
            .putString("admin_pin_salt", newSalt)
            .putString("admin_pin_hash", newHash)
            .putBoolean("pin_is_custom_set", true)
            .commit()

        return Pair(true, "پین امنیتی با موفقیت به‌روزرسانی شد / Admin PIN updated successfully")
    }

    fun emergencyResetPinWithRecoveryToken(
        recoveryToken: String,
        newPin: String
    ): Pair<Boolean, String> {
        val storedRecovery = prefs.getString("emergency_recovery_token", "") ?: ""
        if (storedRecovery.isBlank() || !hashesEqual(recoveryToken.trim(), storedRecovery)) {
            return Pair(false, "کلید ریکاوری اضطراری نامعتبر است / Invalid emergency recovery token")
        }

        if (newPin.trim().length < 6) {
            return Pair(false, "پین جدید باید حداقل ۶ رقم باشد / New PIN must be at least 6 digits")
        }

        val newSalt = generateSalt()
        val newHash = hashCredential(newPin.trim(), newSalt)
        val rotatedRecovery = generateSecureRecoveryToken()
        prefs.edit()
            .putString("admin_pin_salt", newSalt)
            .putString("admin_pin_hash", newHash)
            .putBoolean("pin_is_custom_set", true)
            .putString("emergency_recovery_token", rotatedRecovery)
            .putInt("failed_attempts_count", 0)
            .remove("lockout_until_ms")
            .commit()

        return Pair(true, "پین امنیتی با موفقیت بازیابی و بازنشانی شد / PIN reset via recovery token")
    }

    fun getRecoveryToken(): String {
        val existing = prefs.getString("emergency_recovery_token", null)
        if (!existing.isNullOrBlank()) return existing
        val generated = generateSecureRecoveryToken()
        prefs.edit().putString("emergency_recovery_token", generated).commit()
        return generated
    }

    // --- CRYPTOGRAPHIC JWT-LIKE SESSION MANAGEMENT ---

    fun issueSessionToken(userId: String, role: AdminRole, validityMs: Long = 86_400_000L): SessionToken {
        val issuedAt = System.currentTimeMillis()
        val expiresAt = issuedAt + validityMs

        val payload = JSONObject().apply {
            put("sub", userId)
            put("role", role.id)
            put("iat", issuedAt)
            put("exp", expiresAt)
            put("iss", "TMS_VPN_AUTH_AUTHORITY")
        }

        val payloadB64 = Base64.encodeToString(payload.toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP or Base64.URL_SAFE)
        val signature = computeHmac(payloadB64)
        val rawToken = "$payloadB64.$signature"

        prefs.edit().putString("active_session_token", rawToken).commit()

        return SessionToken(
            rawToken = rawToken,
            userId = userId,
            role = role,
            issuedAtMs = issuedAt,
            expiresAtMs = expiresAt
        )
    }

    fun validateSessionToken(token: String): Pair<Boolean, AdminRole?> {
        try {
            val parts = token.split(".")
            if (parts.size != 2) return Pair(false, null)
            val payloadB64 = parts[0]
            val signature = parts[1]

            val expectedSignature = computeHmac(payloadB64)
            if (signature != expectedSignature) return Pair(false, null)

            val jsonStr = String(Base64.decode(payloadB64, Base64.NO_WRAP or Base64.URL_SAFE), Charsets.UTF_8)
            val json = JSONObject(jsonStr)
            val exp = json.getLong("exp")
            if (System.currentTimeMillis() > exp) return Pair(false, null)

            val roleStr = json.getString("role")
            return Pair(true, AdminRole.fromString(roleStr))
        } catch (_: Exception) {
            return Pair(false, null)
        }
    }

    private fun computeHmac(data: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        val keySpec = SecretKeySpec(hmacSecretKey, "HmacSHA256")
        mac.init(keySpec)
        val signed = mac.doFinal(data.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(signed, Base64.NO_WRAP or Base64.URL_SAFE)
    }

    fun revokeCurrentSession() {
        prefs.edit().remove("active_session_token").commit()
    }
}
