package com.example.data.security

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
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
 * Manages active user authentication supporting:
 * 1) Online Firebase Google Sign-In via Jetpack Credential Manager.
 * 2) Salted local Email & Password vault authentication for Iran's National Intranet
 *    blackouts when Google / Firebase services are unreachable.
 * 3) Dedicated Super Admin authorization bound exclusively to ADMIN_EMAIL.
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
            val restored = buildOfflineUser(savedEmail)
            _offlineUserFlow.value = restored
            return restored
        }

        fun clearOfflineSession(context: Context) {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().remove(KEY_ACTIVE_EMAIL).apply()
            _offlineUserFlow.value = null
        }

        private fun buildOfflineUser(normalizedEmail: String): AuthenticatedUser {
            val uidSuffix = sha256Hex(normalizedEmail).take(16)
            val admin = isAdminEmail(normalizedEmail)
            return AuthenticatedUser(
                uid = "intranet_$uidSuffix",
                username = normalizedEmail.substringBefore("@").ifBlank { "TMS User" },
                email = normalizedEmail,
                role = if (admin) "SUPER_ADMIN" else "USER",
                isOfflineIntranetMode = true,
                isAdmin = admin
            )
        }

        private fun sha256Hex(text: String): String {
            val bytes = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }

    fun getActiveSessionUser(): AuthenticatedUser? {
        val fbUser = Firebase.auth.currentUser
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
        rawPassword: String
    ): LocalEmailAuthResult {
        val email = rawEmail.trim().lowercase()
        val password = rawPassword.trim()

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

        // Strict verification for the reserved Super Admin email (never permits arbitrary registration)
        if (isAdminEmail(email)) {
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
            prefs.edit().putString(KEY_ACTIVE_EMAIL, email).apply()
            val adminUser = buildOfflineUser(email)
            _offlineUserFlow.value = adminUser
            return LocalEmailAuthResult(
                isSuccess = true,
                user = adminUser,
                isNewAccount = false,
                errorMessage = null
            )
        }

        val emailKey = sha256Hex(email)
        val storedHash = prefs.getString("$KEY_HASH_PREFIX$emailKey", null)
        val storedSaltB64 = prefs.getString("$KEY_SALT_PREFIX$emailKey", null)

        val isNewAccount: Boolean
        if (storedHash != null && storedSaltB64 != null) {
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
            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            val derivedHash = hashPassword(password, salt)
            val saltB64 = Base64.encodeToString(salt, Base64.NO_WRAP)
            prefs.edit()
                .putString("$KEY_HASH_PREFIX$emailKey", derivedHash)
                .putString("$KEY_SALT_PREFIX$emailKey", saltB64)
                .apply()
            isNewAccount = true
        }

        prefs.edit().putString(KEY_ACTIVE_EMAIL, email).apply()
        val user = buildOfflineUser(email)
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
            Firebase.auth.signOut()
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

    /**
     * Deterministic RFC 2898 PBKDF2-HMAC-SHA256 using javax.crypto.Mac (supported on all API 24+ & JVMs).
     */
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
