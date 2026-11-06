package com.wanderlog.android.domain.usecase.sync

import com.wanderlog.android.domain.model.sync.SyncRecordKey
import com.wanderlog.android.domain.model.sync.TripSyncManifest
import com.wanderlog.android.domain.model.sync.TripSyncMergePlan
import com.wanderlog.android.domain.model.sync.TripSyncRecord
import javax.inject.Inject

class CompareTripSyncManifestsUseCase @Inject constructor() {

    operator fun invoke(local: TripSyncManifest, remote: TripSyncManifest): TripSyncMergePlan {
        require(local.protocolVersion == remote.protocolVersion) {
            "These app versions cannot sync (local protocol ${local.protocolVersion}, remote ${remote.protocolVersion}). Update both devices to the same latest app version and retry."
        }
        require(local.tripId == remote.tripId) {
            "Cannot compare manifests for different trips"
        }

        val localByKey = local.records.associateBy(TripSyncRecord::key)
        val remoteByKey = remote.records.associateBy(TripSyncRecord::key)
        val allKeys = (localByKey.keys + remoteByKey.keys)
            .sortedWith(compareBy<SyncRecordKey>({ it.entityType.ordinal }, { it.id }))

        val recordsToPush = mutableListOf<TripSyncRecord>()
        val recordsToPull = mutableListOf<TripSyncRecord>()
        val unchangedKeys = mutableListOf<SyncRecordKey>()

        for (key in allKeys) {
            val localRecord = localByKey[key]
            val remoteRecord = remoteByKey[key]
            when {
                localRecord == null && remoteRecord != null -> recordsToPull += remoteRecord
                localRecord != null && remoteRecord == null -> recordsToPush += localRecord
                localRecord != null && remoteRecord != null -> {
                    if (SyncMergePolicy.recordsAreEquivalent(localRecord, remoteRecord)) {
                        unchangedKeys += key
                    } else if (SyncMergePolicy.comparePriority(localRecord, remoteRecord) >= 0) {
                        recordsToPush += localRecord
                    } else {
                        recordsToPull += remoteRecord
                    }
                }
            }
        }

        return TripSyncMergePlan(
            recordsToPush = recordsToPush,
            recordsToPull = recordsToPull,
            unchangedKeys = unchangedKeys
        )
    }
}
