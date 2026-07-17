package com.dlunaunizar.bobitos.data.sync

import android.util.Log
import com.dlunaunizar.bobitos.core.model.SyncStatus
import com.dlunaunizar.bobitos.data.connectivity.ConnectivityRepository
import com.dlunaunizar.bobitos.data.connectivity.NetworkStatus
import com.dlunaunizar.bobitos.data.repository.AuthRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreSyncRepository @Inject constructor(
    private val authRepository: AuthRepository,
    private val connectivityRepository: ConnectivityRepository,
) : SyncRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val refreshReads = AtomicLong(0)
    private val mutableStatus = MutableStateFlow(
        if (connectivityRepository.status.value == NetworkStatus.ONLINE) {
            SyncStatus.REFRESHING
        } else {
            SyncStatus.OFFLINE
        },
    )

    override val status: StateFlow<SyncStatus> = mutableStatus.asStateFlow()

    override suspend fun refresh(spaceId: String?): Boolean {
        if (connectivityRepository.status.value != NetworkStatus.ONLINE) {
            markOffline()
            return false
        }
        mutableStatus.value = SyncStatus.REFRESHING
        return try {
            val user = authRepository.currentUser.value
            if (user != null && user.isEmailVerified) {
                if (spaceId == null) {
                    firestore.collection(MEMBERSHIPS)
                        .whereEqualTo(FIELD_USER_ID, user.id)
                        .whereEqualTo(FIELD_STATUS, STATUS_ACTIVE)
                        .limit(1)
                        .get(Source.SERVER)
                        .await()
                    recordRefreshReads(1, "spaces")
                } else {
                    val membership = firestore.collection(MEMBERSHIPS)
                        .document("${spaceId}_${user.id}")
                        .get(Source.SERVER)
                        .await()
                    if (
                        membership.exists() &&
                        membership.getString(FIELD_STATUS) == STATUS_ACTIVE
                    ) {
                        firestore.collection(SPACES)
                            .document(spaceId)
                            .get(Source.SERVER)
                            .await()
                        recordRefreshReads(2, "space:active")
                    } else {
                        recordRefreshReads(1, "membership:active")
                    }
                }
            }
            if (connectivityRepository.status.value == NetworkStatus.ONLINE) {
                mutableStatus.value = SyncStatus.ONLINE
                true
            } else {
                markOffline()
                false
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            markOffline()
            false
        }
    }

    override fun requireRefresh() {
        mutableStatus.value = if (connectivityRepository.status.value == NetworkStatus.ONLINE) {
            SyncStatus.REFRESHING
        } else {
            SyncStatus.OFFLINE
        }
    }

    override fun markOffline() {
        mutableStatus.value = SyncStatus.OFFLINE
    }

    override fun requireWritable() {
        if (
            connectivityRepository.status.value != NetworkStatus.ONLINE ||
            mutableStatus.value != SyncStatus.ONLINE
        ) {
            throw WriteNotAllowedException()
        }
    }

    override fun reportWriteFailure(error: Throwable) {
        val firestoreError = error as? FirebaseFirestoreException
        if (
            connectivityRepository.status.value != NetworkStatus.ONLINE ||
            firestoreError?.code == FirebaseFirestoreException.Code.UNAVAILABLE ||
            firestoreError?.code == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED
        ) {
            markOffline()
        }
    }

    private fun recordRefreshReads(count: Long, scope: String) {
        val total = refreshReads.addAndGet(count)
        Log.d(LOG_TAG, "serverRefresh scope=$scope reads=$count total=$total")
    }

    private companion object {
        const val LOG_TAG = "BobitosRealtime"
        const val SPACES = "spaces"
        const val MEMBERSHIPS = "memberships"
        const val FIELD_USER_ID = "userId"
        const val FIELD_STATUS = "status"
        const val STATUS_ACTIVE = "ACTIVE"
    }
}
