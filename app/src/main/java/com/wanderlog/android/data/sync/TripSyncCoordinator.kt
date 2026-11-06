package com.wanderlog.android.data.sync

import com.wanderlog.android.domain.model.sync.TripSyncBundle
import com.wanderlog.android.domain.model.sync.TripSyncManifest
import com.wanderlog.android.domain.model.sync.TripSyncMergePlan
import com.wanderlog.android.domain.usecase.sync.CompareTripSyncManifestsUseCase
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TripSyncCoordinator @Inject constructor(
    private val manifestBuilder: TripSyncManifestBuilder,
    private val bundleBuilder: TripSyncBundleBuilder,
    private val bundleApplier: TripSyncBundleApplier,
    private val compareTripSyncManifests: CompareTripSyncManifestsUseCase
) {

    suspend fun buildLocalBundle(tripId: String): TripSyncBundle? =
        bundleBuilder.buildBundle(tripId)

    suspend fun buildLocalManifest(tripId: String): TripSyncManifest? =
        manifestBuilder.buildManifest(tripId)

    suspend fun buildLocalManifestOrEmpty(tripId: String): TripSyncManifest =
        buildLocalManifest(tripId)
            ?: TripSyncManifest(
                tripId = tripId,
                generatedAt = System.currentTimeMillis(),
                records = emptyList()
            )

    suspend fun compareWithRemoteManifest(
        tripId: String,
        remoteManifest: TripSyncManifest
    ): TripSyncMergePlan? {
        val localManifest = buildLocalManifest(tripId) ?: return null
        return compareTripSyncManifests(localManifest, remoteManifest)
    }

    suspend fun compareWithRemoteManifestOrEmpty(remoteManifest: TripSyncManifest): TripSyncMergePlan =
        compareTripSyncManifests(buildLocalManifestOrEmpty(remoteManifest.tripId), remoteManifest)

    suspend fun applyRemoteBundle(bundle: TripSyncBundle): TripSyncApplyResult =
        bundleApplier.applyBundle(bundle)
}
