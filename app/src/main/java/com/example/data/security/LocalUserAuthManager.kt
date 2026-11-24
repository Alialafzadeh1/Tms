package com.example.data.security

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.example.data.model.AdminRole
import com.google.firebase.Firebase
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.auth.auth
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest

private const val TAG = "FirebaseUserAuth"

/**
 * Pure validated credential payload returned before calling Firebase Auth.
 */
sealed interface CredentialValidationResult {
    data class Valid(
        val email: String,
        val password: String,
        val displayName: String
    ) : CredentialValidationResult

    data class Invalid(val messageFa: String, val messageEn: String) : CredentialValidationResult
}

data class VerifiedFirebaseSession(
    val uid: String,
    val email: String,
    val displayName: String,
    val emailVerified: Boolean,
    val idToken: String,
    val isServerAdmin: Boolean,
    val verifiedAdminRole: AdminRole?
)

data class FirebaseAuthResult(
    val isSuccess: Boolean,
    val user: FirebaseUser? = null,
    val session: VerifiedFirebaseSession? = null,
    val isNewAccount: Boolean = false,
    val verificationEmailSent: Boolean = false,
    val errorMessageFa: String? = null,
    val errorMessageEn: String? = null
)

/**
 * Real Firebase Email/Password Authentication & Token Verification Manager.
 *
 * Security guarantees:
 * - Zero hardcoded passwords, hashes, salts, or offline bypass flows.
 * - Canonical user identity is strictly Firebase `uid`.
 * - Admin privileges are never determined by raw email strings; they come strictly
 *   from verified Firebase ID token custom claims (`admin == true` / `role`) and
 *   backend / Firestore verification.
 */
class LocalUserAuthManager(private val context: Context) {

    companion object {
        private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

        fun validateCredentialsInput(
            rawEmail: String,
            rawPassword: String,
            rawDisplayName: String = "",
            isSignUp: Boolean = false
        ): CredentialValidationResult {
            val email = rawEmail.trim().lowercase()
            val password = rawPassword
            val displayName = rawDisplayName.trim()

            if (!EMAIL_REGEX.matches(email)) {
                return CredentialValidationResult.Invalid(
                    messageFa = "لطفاً یک نشانی ایمیل معتبر وارد کنید",
                    messageEn = "Please enter a valid email address"
                )
            }
            if (password.length < 6) {
                return CredentialValidationResult.Invalid(
                    messageFa = "رمز عبور باید حداقل ۶ کاراکتر باشد",
                    messageEn = "Password must be at least 6 characters"
                )
            }
            if (isSignUp && displayName.isNotBlank() && displayName.length < 2) {
                return CredentialValidationResult.Invalid(
                    messageFa = "نام نمایشی باید حداقل ۲ کاراکتر باشد",
                    messageEn = "Display name must be at least 2 characters"
                )
            }
            return CredentialValidationResult.Valid(
                email = email,
                password = password,
                displayName = displayName
            )
        }

        /**
         * Computes the legacy offline UID (`intranet_<16-hex>`) strictly as a read-only
         * source identifier for migrating old Room data into the canonical Firebase UID.
         */
        fun computeLegacyIntranetUid(email: String): String {
            val normalized = email.trim().lowercase()
            if (normalized.isBlank()) return ""
            val bytes = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.UTF_8))
            val hex = bytes.joinToString("") { "%02x".format(it) }.take(16)
            return "intranet_$hex"
        }

        fun extractVerifiedClaims(
            claims: Map<String, Any>,
            uid: String,
            email: String,
            displayName: String,
            emailVerified: Boolean,
            token: String
        ): VerifiedFirebaseSession {
            val rawAdminClaim = claims["admin"] == true
            val rawRoleClaim = claims["role"]?.toString()?.trim()
            val roleFromClaim = AdminRole.fromVerifiedClaimOrNull(rawRoleClaim)
            val isAdmin = rawAdminClaim || roleFromClaim != null
            return VerifiedFirebaseSession(
                uid = uid,
                email = email,
                displayName = displayName,
                emailVerified = emailVerified,
                idToken = token,
                isServerAdmin = isAdmin,
                verifiedAdminRole = when {
                    roleFromClaim != null -> roleFromClaim
                    rawAdminClaim -> AdminRole.SUPER_ADMIN
                    else -> null
                }
            )
        }

        fun mapFirebaseAuthException(error: Throwable): Pair<String, String> {
            return when (error) {
                is FirebaseAuthInvalidUserException -> {
                    "حسابی با این ایمیل یافت نشد؛ لطفاً ابتدا ثبت‌نام کنید." to
                        "No account found for this email. Please register first."
                }
                is FirebaseAuthInvalidCredentialsException -> {
                    "ایمیل یا رمز عبور نامعتبر است." to
                        "Invalid email or password."
                }
                is FirebaseAuthUserCollisionException -> {
                    "این ایمیل قبلاً ثبت‌نام شده است؛ از بخش ورود وارد شوید." to
                        "This email is already registered. Please sign in instead."
                }
                is FirebaseNetworkException -> {
                    "عدم دسترسی به سرور احراز هویت؛ اتصال اینترنت خود را بررسی کنید." to
                        "Network error reaching authentication server. Check connectivity."
                }
                is FirebaseAuthException -> {
                    when (error.errorCode) {
                        "ERROR_TOO_MANY_REQUESTS" ->
                            "تعداد تلاش‌ها بیش از حد مجاز بوده است؛ چند دقیقه صبر کنید." to
                                "Too many attempts. Please wait a few minutes."
                        "ERROR_USER_DISABLED" ->
                            "این حساب کاربری غیرفعال شده است." to
                                "This account has been disabled."
                        "ERROR_WEAK_PASSWORD" ->
                            "رمز عبور ضعیف است؛ رمز قویتری انتخاب کنید." to
                                "Password is too weak. Choose a stronger password."
                        "ERROR_OPERATION_NOT_ALLOWED" ->
                            "ورود با ایمیل و رمز عبور در پروژه Firebase فعال نشده است." to
                                "Email/Password sign-in is not enabled in Firebase Console."
                        else -> (error.localizedMessage ?: "خطای احراز هویت") to
                            (error.localizedMessage ?: "Authentication failed")
                    }
                }
                else -> (error.localizedMessage ?: "خطای احراز هویت") to
                    (error.localizedMessage ?: "Authentication error")
            }
        }
    }

    fun getCurrentFirebaseUser(): FirebaseUser? = runCatching { Firebase.auth.currentUser }.getOrNull()

    suspend fun signInWithFirebaseEmail(
        rawEmail: String,
        rawPassword: String
    ): FirebaseAuthResult {
        val validation = validateCredentialsInput(rawEmail, rawPassword, isSignUp = false)
        if (validation is CredentialValidationResult.Invalid) {
            return FirebaseAuthResult(
                isSuccess = false,
                errorMessageFa = validation.messageFa,
                errorMessageEn = validation.messageEn
            )
        }
        val valid = validation as CredentialValidationResult.Valid

        return try {
            val result = Firebase.auth.signInWithEmailAndPassword(valid.email, valid.password).await()
            val user = result.user ?: return FirebaseAuthResult(
                isSuccess = false,
                errorMessageFa = "نشست کاربر پس از ورود دریافت نشد.",
                errorMessageEn = "Signed-in user session unavailable."
            )
            val session = getVerifiedFirebaseSession(user, forceRefresh = true)
            FirebaseAuthResult(
                isSuccess = true,
                user = user,
                session = session,
                isNewAccount = false
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Firebase sign-in failed: ${t.message}")
            val (fa, en) = mapFirebaseAuthException(t)
            FirebaseAuthResult(isSuccess = false, errorMessageFa = fa, errorMessageEn = en)
        }
    }

    suspend fun registerWithFirebaseEmail(
        rawDisplayName: String,
        rawEmail: String,
        rawPassword: String
    ): FirebaseAuthResult {
        val validation = validateCredentialsInput(
            rawEmail = rawEmail,
            rawPassword = rawPassword,
            rawDisplayName = rawDisplayName,
            isSignUp = true
        )
        if (validation is CredentialValidationResult.Invalid) {
            return FirebaseAuthResult(
                isSuccess = false,
                errorMessageFa = validation.messageFa,
                errorMessageEn = validation.messageEn
            )
        }
        val valid = validation as CredentialValidationResult.Valid

        return try {
            val result = Firebase.auth.createUserWithEmailAndPassword(valid.email, valid.password).await()
            val user = result.user ?: return FirebaseAuthResult(
                isSuccess = false,
                errorMessageFa = "خطا در ایجاد حساب کاربری Firebase.",
                errorMessageEn = "Failed creating Firebase account."
            )

            val desiredName = valid.displayName.ifBlank {
                valid.email.substringBefore("@").ifBlank { "TMS User" }
            }
            runCatching {
                val req = UserProfileChangeRequest.Builder()
                    .setDisplayName(desiredName)
                    .build()
                user.updateProfile(req).await()
            }

            val verificationSent = runCatching {
                user.sendEmailVerification().await()
                true
            }.getOrDefault(false)

            val session = getVerifiedFirebaseSession(user, forceRefresh = true)
            FirebaseAuthResult(
                isSuccess = true,
                user = user,
                session = session,
                isNewAccount = true,
                verificationEmailSent = verificationSent
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Firebase registration failed: ${t.message}")
            val (fa, en) = mapFirebaseAuthException(t)
            FirebaseAuthResult(isSuccess = false, errorMessageFa = fa, errorMessageEn = en)
        }
    }

    suspend fun sendPasswordReset(rawEmail: String): Result<Unit> {
        val email = rawEmail.trim().lowercase()
        if (!EMAIL_REGEX.matches(email)) {
            return Result.failure(IllegalArgumentException("لطفاً یک نشانی ایمیل معتبر وارد کنید"))
        }
        return try {
            Firebase.auth.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (t: Throwable) {
            val (fa, _) = mapFirebaseAuthException(t)
            Result.failure(IllegalStateException(fa, t))
        }
    }

    suspend fun sendEmailVerification(): Result<Unit> {
        val user = getCurrentFirebaseUser()
            ?: return Result.failure(IllegalStateException("ابتدا وارد حساب کاربری خود شوید"))
        return try {
            user.sendEmailVerification().await()
            Result.success(Unit)
        } catch (t: Throwable) {
            val (fa, _) = mapFirebaseAuthException(t)
            Result.failure(IllegalStateException(fa, t))
        }
    }

    suspend fun getVerifiedFirebaseSession(
        user: FirebaseUser? = getCurrentFirebaseUser(),
        forceRefresh: Boolean = false
    ): VerifiedFirebaseSession? {
        val activeUser = user ?: return null
        return try {
            val tokenResult = activeUser.getIdToken(forceRefresh).await()
            val token = tokenResult.token.orEmpty()
            val email = activeUser.email.orEmpty()
            val displayName = activeUser.displayName?.takeIf { it.isNotBlank() }
                ?: email.substringBefore("@").ifBlank { "TMS User" }
            extractVerifiedClaims(
                claims = tokenResult.claims,
                uid = activeUser.uid,
                email = email,
                displayName = displayName,
                emailVerified = activeUser.isEmailVerified,
                token = token
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Failed retrieving Firebase ID token claims: ${t.message}")
            null
        }
    }

    suspend fun getFreshIdToken(forceRefresh: Boolean = false): String? {
        val user = getCurrentFirebaseUser() ?: return null
        return runCatching { user.getIdToken(forceRefresh).await().token }.getOrNull()
    }

    suspend fun logout() {
        try {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear credential state on sign-out: ${e.message}")
        } finally {
            runCatching { Firebase.auth.signOut() }
        }
    }
}
