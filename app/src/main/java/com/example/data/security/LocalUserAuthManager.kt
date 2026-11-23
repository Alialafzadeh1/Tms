package com.example.data.security

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.example.data.local.RegisteredUserEntity
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private const val TAG = "UserAuthSession"
private const val PREFS_NAME = "tms_local_intranet_auth"
private const val KEY_ACTIVE_EMAIL = "active_offline_email"
private const val KEY_HASH_PREFIX = "pw_hash_"
private const val KEY_SALT_PREFIX = "pw_salt_"
private const val KEY_NAME_PREFIX = "display_name_"
private const val KEY_CREATED_PREFIX = "created_at_"
private const val KEY_REGISTERED_EMAILS = "registered_emails_index"

private const val ADMIN_PW_SALT = "tms-admin-vault-salt-2026"
private const val ADMIN_PW_HASH_B64 = "C6Xr4uUsQHB39tH0Oi4OQLCTKQSN1GeEbTbLz319cT4="

data class AuthenticatedUser(
    val uid: String,
    val username: String,
    val email: String,
    val role: String = "USER",
    val isOfflineIntranetMode: Boolean = false,
    val isAdmin: Boolean = false
)

data class LocalEmailAuthResult(
    val isSuccess: Boolean,
    val user: AuthenticatedUser? = null,
    val isNewAccount: Boolean = false,
    val errorMessage: String? = null
)

/**
 * Unified Email & Password Authentication Manager for TMS VPN / TMS NEXUS AI.
 *
 * - Authenticates both standard users and Super Admin (`alafzadehali@gmail.com`) via salted PBKDF2-HMAC-SHA256.
 * - Maintains user display name, UID (`intranet_<hash>` for stable ID parity), and registered user index
 *   so the Admin Panel sees every registered user immediately.
 */
class LocalUserAuthManager(private val context: Context) {

    companion object {
        const val ADMIN_EMAIL = "alafzadehali@gmail.com"

        private val _offlineUserFlow = MutableStateFlow<AuthenticatedUser?>(null)
        val offlineUserFlow: StateFlow<AuthenticatedUser?> = _offlineUserFlow.asStateFlow()

        fun isAdminEmail(email: String?): Boolean {
            return email?.trim()?.equals(ADMIN_EMAIL, ignoreCase = true) == true
        }

        fun restoreOfflineSession(context: Context): AuthenticatedUser? {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val savedEmail = prefs.getString(KEY_ACTIVE_EMAIL, null)?.trim()?.lowercase()
            if (savedEmail.isNullOrBlank()) {
                _offlineUserFlow.value = null
                return null
            }
            val storedName = prefs.getString("$KEY_NAME_PREFIX${sha256Hex(savedEmail)}", null).orEmpty()
            val restored = buildEmailUser(savedEmail, storedName)
            _offlineUserFlow.value = restored
            return restored
        }

        fun clearOfflineSession(context: Context) {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().remove(KEY_ACTIVE_EMAIL).apply()
            _offlineUserFlow.value = null
        }

        fun getAllRegisteredPrefProfiles(context: Context): List<RegisteredUserEntity> {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val emails = (prefs.getStringSet(KEY_REGISTERED_EMAILS, emptySet()).orEmpty() +
                listOfNotNull(prefs.getString(KEY_ACTIVE_EMAIL, null)))
                .map { it.trim().lowercase() }
                .filter { it.isNotBlank() }
                .distinct()

            val now = System.currentTimeMillis()
            return emails.map { email ->
                val emailKey = sha256Hex(email)
                val user = buildEmailUser(email, prefs.getString("$KEY_NAME_PREFIX$emailKey", null).orEmpty())
                val created = prefs.getLong("$KEY_CREATED_PREFIX$emailKey", now)
                RegisteredUserEntity(
                    userId = user.uid,
                    displayName = user.username,
                    email = user.email,
                    role = user.role,
                    maxDevices = 10,
                    dataLimitBytes = 500.0 * 1024 * 1024 * 1024,
                    subscriptionStatus = if (user.isAdmin) "active" else "expired",
                    createdAtMs = created,
                    updatedAtMs = now
                )
            }
        }

        private fun buildEmailUser(normalizedEmail: String, customName: String = ""): AuthenticatedUser {
            val uidSuffix = sha256Hex(normalizedEmail).take(16)
            val admin = isAdminEmail(normalizedEmail)
            val resolvedName = customName.trim().ifBlank {
                if (admin) "Super Admin" else normalizedEmail.substringBefore("@").ifBlank { "TMS User" }
            }
            return AuthenticatedUser(
                uid = "intranet_$uidSuffix",
                username = resolvedName,
                email = normalizedEmail,
                role = if (admin) "SUPER_ADMIN" else "USER",
                isOfflineIntranetMode = false,
                isAdmin = admin
            )
        }

        private fun sha256Hex(text: String): String {
            val bytes = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }

    fun getActiveSessionUser(): AuthenticatedUser? {
        val fbUser = runCatching { Firebase.auth.currentUser }.getOrNull()
        if (fbUser != null) {
            val admin = isAdminEmail(fbUser.email)
            val displayName = fbUser.displayName?.takeIf { it.isNotBlank() }
                ?: fbUser.email?.substringBefore("@")?.takeIf { it.isNotBlank() }
                ?: "TMS User"
            return AuthenticatedUser(
                uid = fbUser.uid,
                username = displayName,
                email = fbUser.email.orEmpty(),
                role = if (admin) "SUPER_ADMIN" else "USER",
                isOfflineIntranetMode = false,
                isAdmin = admin
            )
        }
        return _offlineUserFlow.value ?: restoreOfflineSession(context)
    }

    fun signInOrRegisterOfflineEmail(
        rawEmail: String,
        rawPassword: String,
        rawDisplayName: String = "",
        requireExplicitSignUp: Boolean? = null
    ): LocalEmailAuthResult {
        val email = rawEmail.trim().lowercase()
        val password = rawPassword.trim()
        val displayName = rawDisplayName.trim()

        if (!isValidEmail(email)) {
            return LocalEmailAuthResult(
                isSuccess = false,
                errorMessage = "لطفاً یک نشانی ایمیل معتبر وارد کنید"
            )
        }
        if (password.length < 6) {
            return LocalEmailAuthResult(
                isSuccess = false,
                errorMessage = "رمز عبور باید حداقل ۶ کاراکتر باشد"
            )
        }

        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val emailKey = sha256Hex(email)
        val currentIndex = prefs.getStringSet(KEY_REGISTERED_EMAILS, emptySet()).orEmpty().toMutableSet()

        // Strict verification for the reserved Super Admin email (never permits arbitrary registration)
        if (isAdminEmail(email)) {
            if (requireExplicitSignUp == true) {
                return LocalEmailAuthResult(
                    isSuccess = false,
                    errorMessage = "حساب مدیریت ارشد از پیش ثبت شده است؛ لطفاً از بخش ورود اقدام کنید"
                )
            }
            val candidateDigest = pbkdf2HmacSha256(
                password = password,
                salt = ADMIN_PW_SALT.toByteArray(Charsets.UTF_8)
            )
            val expectedDigest = Base64.decode(ADMIN_PW_HASH_B64, Base64.NO_WRAP)
            if (!MessageDigest.isEqual(expectedDigest, candidateDigest)) {
                return LocalEmailAuthResult(
                    isSuccess = false,
                    errorMessage = "رمز عبور برای این ایمیل اشتباه است"
                )
            }
            currentIndex.add(email)
            val resolvedName = displayName.ifBlank {
                prefs.getString("$KEY_NAME_PREFIX$emailKey", null) ?: "Super Admin"
            }
            prefs.edit()
                .putString(KEY_ACTIVE_EMAIL, email)
                .putString("$KEY_NAME_PREFIX$emailKey", resolvedName)
                .putStringSet(KEY_REGISTERED_EMAILS, currentIndex)
                .apply()
            val adminUser = buildEmailUser(email, resolvedName)
            _offlineUserFlow.value = adminUser
            return LocalEmailAuthResult(
                isSuccess = true,
                user = adminUser,
                isNewAccount = false,
                errorMessage = null
            )
        }

        val storedHash = prefs.getString("$KEY_HASH_PREFIX$emailKey", null)
        val storedSaltB64 = prefs.getString("$KEY_SALT_PREFIX$emailKey", null)

        val isNewAccount: Boolean
        if (storedHash != null && storedSaltB64 != null) {
            if (requireExplicitSignUp == true) {
                return LocalEmailAuthResult(
                    isSuccess = false,
                    errorMessage = "این ایمیل قبلاً ثبت‌نام شده است؛ از بخش ورود وارد شوید"
                )
            }
            val salt = Base64.decode(storedSaltB64, Base64.NO_WRAP)
            val candidateHash = hashPassword(password, salt)
            val expectedBytes = Base64.decode(storedHash, Base64.NO_WRAP)
            val actualBytes = Base64.decode(candidateHash, Base64.NO_WRAP)
            if (!MessageDigest.isEqual(expectedBytes, actualBytes)) {
                return LocalEmailAuthResult(
                    isSuccess = false,
                    errorMessage = "رمز عبور برای این ایمیل اشتباه است"
                )
            }
            isNewAccount = false
        } else {
            if (requireExplicitSignUp == false) {
                return LocalEmailAuthResult(
                    isSuccess = false,
                    errorMessage = "حسابی با این ایمیل یافت نشد؛ لطفاً ابتدا ثبت‌نام کنید"
                )
            }
            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            val derivedHash = hashPassword(password, salt)
            val saltB64 = Base64.encodeToString(salt, Base64.NO_WRAP)
            val nowMs = System.currentTimeMillis()
            prefs.edit()
                .putString("$KEY_HASH_PREFIX$emailKey", derivedHash)
                .putString("$KEY_SALT_PREFIX$emailKey", saltB64)
                .putLong("$KEY_CREATED_PREFIX$emailKey", nowMs)
                .apply()
            isNewAccount = true
        }

        currentIndex.add(email)
        val storedName = prefs.getString("$KEY_NAME_PREFIX$emailKey", null).orEmpty()
        val effectiveName = displayName.ifBlank { storedName.ifBlank { email.substringBefore("@") } }
        prefs.edit()
            .putString(KEY_ACTIVE_EMAIL, email)
            .putString("$KEY_NAME_PREFIX$emailKey", effectiveName)
            .putStringSet(KEY_REGISTERED_EMAILS, currentIndex)
            .apply()

        val user = buildEmailUser(email, effectiveName)
        _offlineUserFlow.value = user
        return LocalEmailAuthResult(
            isSuccess = true,
            user = user,
            isNewAccount = isNewAccount,
            errorMessage = null
        )
    }

    suspend fun logout() {
        clearOfflineSession(context)
        try {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear credential state on sign-out: ${e.message}")
        } finally {
            runCatching { Firebase.auth.signOut() }
        }
    }

    private fun isValidEmail(email: String): Boolean {
        val at = email.indexOf('@')
        val dot = email.lastIndexOf('.')
        return at > 0 && dot > at + 1 && dot < email.length - 1 && !email.contains(' ')
    }

    private fun hashPassword(password: String, salt: ByteArray): String {
        val dk = pbkdf2HmacSha256(password, salt)
        return Base64.encodeToString(dk, Base64.NO_WRAP)
    }

    private fun pbkdf2HmacSha256(
        password: String,
        salt: ByteArray,
        iterations: Int = 10_000,
        keyLenBytes: Int = 32
    ): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(password.toByteArray(Charsets.UTF_8), "HmacSHA256"))

        val hashLen = 32
        val blocks = (keyLenBytes + hashLen - 1) / hashLen
        val result = ByteArray(blocks * hashLen)
        val intBlock = ByteArray(4)

        for (blockIndex in 1..blocks) {
            intBlock[0] = ((blockIndex ushr 24) and 0xFF).toByte()
            intBlock[1] = ((blockIndex ushr 16) and 0xFF).toByte()
            intBlock[2] = ((blockIndex ushr 8) and 0xFF).toByte()
            intBlock[3] = (blockIndex and 0xFF).toByte()

            mac.reset()
            mac.update(salt)
            var u = mac.doFinal(intBlock)
            val t = u.copyOf()

            for (iter in 1 until iterations) {
                mac.reset()
                u = mac.doFinal(u)
                for (j in t.indices) {
                    t[j] = (t[j].toInt() xor u[j].toInt()).toByte()
                }
            }
            System.arraycopy(t, 0, result, (blockIndex - 1) * hashLen, hashLen)
        }
        return result.copyOf(keyLenBytes)
    }
}

