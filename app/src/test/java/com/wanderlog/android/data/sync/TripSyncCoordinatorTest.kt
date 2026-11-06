package com.wanderlog.android.data.sync

import com.wanderlog.android.domain.model.sync.SyncEntityType
import com.wanderlog.android.domain.model.sync.SyncMetadata
import com.wanderlog.android.domain.model.sync.SyncTripPayload
import com.wanderlog.android.domain.model.sync.TripSyncBundle
import com.wanderlog.android.domain.model.sync.TripSyncManifest
import com.wanderlog.android.domain.model.sync.TripSyncMergePlan
import com.wanderlog.android.domain.model.sync.TripSyncRecord
import com.wanderlog.android.domain.usecase.sync.CompareTripSyncManifestsUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripSyncCoordinatorTest {

    private val manifestBuilder = mockk<TripSyncManifestBuilder>()
    private val bundleBuilder = mockk<TripSyncBundleBuilder>()
    private val bundleApplier = mockk<TripSyncBundleApplier>()
    private val compareUseCase = mockk<CompareTripSyncManifestsUseCase>()
    private val coordinator = TripSyncCoordinator(manifestBuilder, bundleBuilder, bundleApplier, compareUseCase)

    @Test
    fun `buildLocalManifest uses metadata without serializing attachment contents`() = kotlinx.coroutines.test.runTest {
        val bundle = bundle()
        coEvery { manifestBuilder.buildManifest("trip-1", any()) } returns bundle.toManifest()

        val manifest = coordinator.buildLocalManifest("trip-1")

        assertEquals("trip-1", manifest?.tripId)
        assertEquals(1, manifest?.records?.size)
    }

    @Test
    fun `compareWithRemoteManifest uses local manifest and compare use case`() = kotlinx.coroutines.test.runTest {
        val localBundle = bundle()
        val remoteManifest = TripSyncManifest(
            tripId = "trip-1",
            generatedAt = 20L,
            records = listOf(
                TripSyncRecord(
                    entityType = SyncEntityType.TRIP,
                    id = "trip-1",
                    updatedAt = 30L,
                    lastModifiedByDeviceId = "device-remote"
                )
            )
        )
        val expectedPlan = TripSyncMergePlan(
            recordsToPush = emptyList(),
            recordsToPull = remoteManifest.records,
            unchangedKeys = emptyList()
        )

        coEvery { manifestBuilder.buildManifest("trip-1", any()) } returns localBundle.toManifest()
        every { compareUseCase(localBundle.toManifest(), remoteManifest) } returns expectedPlan

        val result = coordinator.compareWithRemoteManifest("trip-1", remoteManifest)

        assertEquals(expectedPlan, result)
    }

    @Test
    fun `compareWithRemoteManifestOrEmpty falls back to empty local manifest`() = kotlinx.coroutines.test.runTest {
        val realCoordinator = TripSyncCoordinator(manifestBuilder, bundleBuilder, bundleApplier, CompareTripSyncManifestsUseCase())
        val remoteManifest = TripSyncManifest(
            tripId = "trip-1",
            generatedAt = 20L,
            records = listOf(
                TripSyncRecord(
                    entityType = SyncEntityType.TRIP,
                    id = "trip-1",
                    updatedAt = 30L,
                    lastModifiedByDeviceId = "device-remote"
                )
            )
        )
        val expectedPlan = TripSyncMergePlan(
            recordsToPush = emptyList(),
            recordsToPull = remoteManifest.records,
            unchangedKeys = emptyList()
        )

        coEvery { manifestBuilder.buildManifest("trip-1", any()) } returns null

        val result = realCoordinator.compareWithRemoteManifestOrEmpty(remoteManifest)

        assertEquals(expectedPlan, result)
        assertTrue(result.recordsToPush.isEmpty())
        assertEquals(remoteManifest.records, result.recordsToPull)
    }

    @Test
    fun `applyRemoteBundle delegates to bundle applier`() = kotlinx.coroutines.test.runTest {
        val bundle = bundle()
        val expected = TripSyncApplyResult(appliedRecords = 2, skippedRecords = 1)
        coEvery { bundleApplier.applyBundle(bundle) } returns expected

        val result = coordinator.applyRemoteBundle(bundle)

        assertEquals(expected, result)
        coVerify(exactly = 1) { bundleApplier.applyBundle(bundle) }
    }

    private fun bundle(): TripSyncBundle = TripSyncBundle(
        generatedAt = 10L,
        trip = SyncTripPayload(
            id = "trip-1",
            name = "Kyoto",
            destination = "Kyoto",
            startDate = "2026-07-01",
            endDate = "2026-07-05",
            currencyCode = "JPY",
            metadata = SyncMetadata(updatedAt = 10L, lastModifiedByDeviceId = "device-local")
        )
    )
}
