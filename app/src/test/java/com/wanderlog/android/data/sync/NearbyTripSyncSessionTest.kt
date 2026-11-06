package com.wanderlog.android.data.sync

import android.content.Context
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.android.gms.tasks.Tasks
import com.squareup.moshi.Moshi
import com.wanderlog.android.domain.model.sync.*
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class NearbyTripSyncSessionTest {
    private val context = mockk<Context>(relaxed = true)
    private val client = mockk<ConnectionsClient>(relaxed = true)
    private val coordinator = mockk<TripSyncCoordinator>(relaxed = true)
    private val metadata = mockk<SyncMetadataStamp>()
    private val lifecycle = slot<ConnectionLifecycleCallback>()
    private val payloadCallback = slot<PayloadCallback>()
    private lateinit var transport: NearbyTripSyncTransport
    private val sent = mutableListOf<ByteArray>()

    @Before fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        mockkStatic(Nearby::class)
        mockkStatic(Payload::class)
        every { Nearby.getConnectionsClient(context) } returns client
        every { metadata.currentDeviceId() } returns "device"
        every { client.startDiscovery(any(), any(), any()) } returns Tasks.forResult(null)
        every { client.requestConnection(any<String>(), any(), capture(lifecycle)) } returns Tasks.forResult(null)
        every { client.acceptConnection(any(), capture(payloadCallback)) } returns Tasks.forResult(null)
        every { client.sendPayload(any<String>(), any()) } returns Tasks.forResult(null)
        every { Payload.fromBytes(any()) } answers {
            val bytes = firstArg<ByteArray>()
            sent += bytes
            mockk<Payload> { every { type } returns Payload.Type.BYTES; every { asBytes() } returns bytes }
        }
        transport = NearbyTripSyncTransport(context, coordinator, metadata, Moshi.Builder().build())
    }
    @After fun teardown() { transport.stop(); unmockkAll(); Dispatchers.resetMain() }

    private suspend fun connect() {
        transport.startDiscovery(null)
        transport.requestConnection(NearbySyncPeer("peer", "Other phone"))
        lifecycle.captured.onConnectionInitiated("peer", mockk { every { endpointName } returns "Other phone" })
        lifecycle.captured.onConnectionResult("peer", mockk { every { status } returns mockk { every { statusCode } returns ConnectionsStatusCodes.STATUS_OK } })
    }

    @Test fun `joining an existing trip still replies with its manifest`() = runTest {
        coEvery { coordinator.buildLocalManifestOrEmpty("trip") } returns TripSyncManifest(tripId = "trip", generatedAt = 1, records = emptyList())
        coEvery { coordinator.compareWithRemoteManifestOrEmpty(any()) } returns TripSyncMergePlan(emptyList(), emptyList(), emptyList())
        connect()
        val incoming = mockk<Payload> {
            every { type } returns Payload.Type.BYTES
            every { asBytes() } returns """{"type":"manifest","tripId":"trip","manifest":{"protocolVersion":6,"tripId":"trip","generatedAt":1,"records":[]}}""".toByteArray()
        }
        payloadCallback.captured.onPayloadReceived("peer", incoming)
        // IO dispatch is real; await the event instead of assuming callback work is synchronous.
        repeat(100) { if (sent.isEmpty()) kotlinx.coroutines.withContext(Dispatchers.IO) { Thread.sleep(10) } }
        assertEquals(1, sent.size)
        assertTrue(sent.single().decodeToString().contains("manifest"))
        payloadCallback.captured.onPayloadReceived("peer", incoming)
        repeat(10) { kotlinx.coroutines.withContext(Dispatchers.IO) { Thread.sleep(10) } }
        assertEquals(1, sent.size)
    }

    @Test fun `callbacks from a stopped session cannot reconnect or claim its trip`() = runTest {
        connect()
        val stale = lifecycle.captured
        transport.stop()
        stale.onConnectionResult("peer", mockk { every { status } returns mockk { every { statusCode } returns ConnectionsStatusCodes.STATUS_OK } })
        assertEquals(NearbySyncMode.IDLE, transport.state.value.mode)
        assertNull(transport.state.value.tripId)
    }
}
