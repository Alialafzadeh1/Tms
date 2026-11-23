package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.model.SupportTicket
import com.example.data.model.SupportTicketCategory
import com.example.data.model.SupportTicketPriority
import com.example.data.model.SupportTicketReply
import com.example.data.model.SupportTicketStatus
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

private const val TAG = "SupportTicketRepository"

class SupportTicketRepository(private val dbProvider: () -> FirebaseFirestore?) {

    constructor(db: FirebaseFirestore) : this({ db })

    constructor(context: Context) : this(createFirestoreProvider(context.applicationContext))

    companion object {
        private fun createFirestoreProvider(appCtx: Context): () -> FirebaseFirestore? = {
            runCatching {
                if (FirebaseApp.getApps(appCtx).isEmpty()) {
                    FirebaseApp.initializeApp(appCtx)
                }
                FirebaseFirestore.getInstance(
                    appCtx.getString(R.string.firestore_database_id)
                )
            }.getOrNull()
        }
    }

    private val db: FirebaseFirestore
        get() = dbProvider()
            ?: throw IllegalStateException("Cloud Firestore is unavailable on this device or session.")

    private val authOrNull: FirebaseAuth?
        get() = runCatching { Firebase.auth }.getOrNull()

    private fun requireUserId(): String {
        return authOrNull?.currentUser?.uid
            ?: throw IllegalStateException("برای ثبت یا پیگیری تیکت پشتیبانی باید با حساب کاربری متصل شوید.")
    }

    fun observeUserTickets(uid: String): Flow<List<SupportTicket>> = flow {
        if (uid.isBlank() || authOrNull?.currentUser == null) {
            emit(emptyList())
            return@flow
        }
        emitAll(
            db.collection("support_tickets")
                .whereEqualTo("userId", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        SupportTicket::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt?.toDate()?.time ?: it.createdAt?.toDate()?.time ?: 0L }
                }
                .catch { error ->
                    if (error is CancellationException) throw error
                    Log.w(TAG, "observeUserTickets failed: ${error.message}")
                    emit(emptyList())
                }
        )
    }

    fun observeAllTicketsForAdmin(): Flow<List<SupportTicket>> = flow {
        if (authOrNull?.currentUser == null) {
            emit(emptyList())
            return@flow
        }
        emitAll(
            db.collection("support_tickets")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        SupportTicket::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    if (error is CancellationException) throw error
                    Log.w(TAG, "observeAllTicketsForAdmin failed: ${error.message}")
                    emit(emptyList())
                }
        )
    }

    fun observeTicketReplies(ticketId: String): Flow<List<SupportTicketReply>> = flow {
        if (ticketId.isBlank() || authOrNull?.currentUser == null) {
            emit(emptyList())
            return@flow
        }
        emitAll(
            db.collection("support_tickets")
                .document(ticketId)
                .collection("replies")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        SupportTicketReply::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    if (error is CancellationException) throw error
                    Log.w(TAG, "observeTicketReplies failed: ${error.message}")
                    emit(emptyList())
                }
        )
    }

    suspend fun createTicket(
        subject: String,
        description: String,
        category: SupportTicketCategory,
        priority: SupportTicketPriority,
        aiTriageSummary: String,
        userEmail: String
    ): Result<String> {
        return try {
            val uid = requireUserId()
            val docRef = db.collection("support_tickets").document()
            val cleanSubject = subject.trim().take(160)
            val cleanDesc = description.trim().take(3000)
            require(cleanSubject.isNotBlank() && cleanDesc.isNotBlank()) {
                "موضوع و شرح تیکت نمی‌تواند خالی باشد."
            }
            val initialStatus = if (aiTriageSummary.isNotBlank()) {
                SupportTicketStatus.AI_TRIAGED.name
            } else {
                SupportTicketStatus.OPEN.name
            }
            val payload = mapOf(
                "userId" to uid,
                "userEmail" to userEmail.take(256),
                "subject" to cleanSubject,
                "category" to category.name,
                "priority" to priority.name,
                "status" to initialStatus,
                "description" to cleanDesc,
                "aiTriageSummary" to aiTriageSummary.take(1000),
                "lastReplyText" to "",
                "lastReplyByRole" to "USER",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun postReply(
        ticket: SupportTicket,
        message: String,
        isAdminReply: Boolean,
        nextStatus: SupportTicketStatus? = null
    ): Result<Unit> {
        return try {
            val uid = requireUserId()
            val cleanMsg = message.trim().take(2000)
            require(cleanMsg.isNotBlank()) { "متن پاسخ نمی‌تواند خالی باشد." }

            val ticketRef = db.collection("support_tickets").document(ticket.ticketId)
            val replyRef = ticketRef.collection("replies").document()
            val senderRole = if (isAdminReply) "ADMIN" else "USER"
            val updatedStatus = when {
                nextStatus != null -> nextStatus.name
                isAdminReply -> SupportTicketStatus.ADMIN_REPLIED.name
                else -> SupportTicketStatus.OPEN.name
            }

            val batch = db.batch()
            batch.set(
                replyRef,
                mapOf(
                    "ticketId" to ticket.ticketId,
                    "userId" to ticket.userId,
                    "senderUid" to uid,
                    "senderRole" to senderRole,
                    "message" to cleanMsg,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                ticketRef,
                mapOf(
                    "status" to updatedStatus,
                    "lastReplyText" to cleanMsg.take(300),
                    "lastReplyByRole" to senderRole,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun adminUpdateStatus(
        ticketId: String,
        newStatus: SupportTicketStatus
    ): Result<Unit> {
        return try {
            requireUserId()
            db.collection("support_tickets").document(ticketId)
                .update(
                    mapOf(
                        "status" to newStatus.name,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
