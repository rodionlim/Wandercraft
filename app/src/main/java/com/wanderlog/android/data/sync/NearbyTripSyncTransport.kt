package com.wanderlog.android.data.sync

import android.content.Context
import android.os.Build
import com.google.android.gms.common.api.Status
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import org.json.JSONArray
import org.json.JSONObject
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.wanderlog.android.BuildConfig
import com.wanderlog.android.domain.model.sync.SyncEntityType
import com.wanderlog.android.domain.model.sync.TripSyncBundle
import com.wanderlog.android.domain.model.sync.TripSyncManifest
import com.wanderlog.android.domain.model.sync.TripSyncRecord
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

enum class NearbySyncMode {
    IDLE,
    ADVERTISING,
    DISCOVERING,
    CONNECTED
}

data class NearbySyncPeer(
    val endpointId: String,
    val endpointName: String
)

data class NearbyTripSyncState(
    val tripId: String? = null,
    val mode: NearbySyncMode = NearbySyncMode.IDLE,
    val localEndpointName: String = "",
    val discoveredPeers: List<NearbySyncPeer> = emptyList(),
    val connectedPeerName: String? = null,
    val statusMessage: String = "Idle",
    val lastSyncSummary: String? = null,
    val logLines: List<String> = emptyList(),
    val errorMessage: String? = null,
    val isBusy: Boolean = false
)

@JsonClass(generateAdapter = true)
data class NearbySyncControlMessage(
    val type: String,
    val tripId: String,
    val manifest: TripSyncManifest? = null,
    val supportsApplyConfirmation: Boolean = true
)

@Singleton
class NearbyTripSyncTransport @Inject constructor(
    @ApplicationContext private val context: Context,
    private val coordinator: TripSyncCoordinator,
    private val syncMetadataStamp: SyncMetadataStamp,
    private val moshi: Moshi
) {

    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var session = 0
    private var connectedEndpointId: String? = null
    private val sentManifests = mutableSetOf<String>()
    private var peerSupportsApplyConfirmation = false
    private var awaitingIncomingBundle = false
    private var awaitingOutgoingTransfer = false
    private var awaitingApplyConfirmation = false
    private var operationMutex = Mutex()
    private var timeoutJob: Job? = null

    fun clearError() { _state.update { it.copy(errorMessage = null) } }

    private fun watchForTimeout() {
        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            delay(120_000)
            if (state.value.isBusy) {
                reportError("Sync timed out. Keep both devices awake, then stop and retry the session", IllegalStateException("No transfer progress for two minutes"))
            }
        }
    }
    private val connectionsClient: ConnectionsClient by lazy(LazyThreadSafetyMode.NONE) {
        Nearby.getConnectionsClient(context)
    }
    private val controlPayloadAdapter: JsonAdapter<Map<String, Any?>> by lazy(LazyThreadSafetyMode.NONE) {
        moshi.adapter(Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java))
    }
    private val bundleAdapter: JsonAdapter<TripSyncBundle> by lazy(LazyThreadSafetyMode.NONE) {
        moshi.adapter(TripSyncBundle::class.java)
    }
    private val bundlePayloadAdapter: JsonAdapter<Map<String, Any?>> by lazy(LazyThreadSafetyMode.NONE) {
        moshi.adapter(Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java))
    }
    private val discoveredPeers = linkedMapOf<String, NearbySyncPeer>()
    private val knownEndpointNames = mutableMapOf<String, String>()
    private val incomingFilePayloads = mutableMapOf<Long, Pair<String, Payload>>()
    private val outgoingBundleFiles = mutableMapOf<Long, File>()

    private val _state = MutableStateFlow(NearbyTripSyncState(localEndpointName = buildLocalEndpointName()))
    val state: StateFlow<NearbyTripSyncState> = _state.asStateFlow()

    suspend fun startAdvertising(tripId: String) {
        stop()
        _state.update {
            it.copy(
                tripId = tripId,
                mode = NearbySyncMode.ADVERTISING,
                statusMessage = "Advertising trip sync",
                errorMessage = null,
                lastSyncSummary = null,
                logLines = emptyList(),
                discoveredPeers = emptyList(),
                connectedPeerName = null,
                localEndpointName = buildLocalEndpointName(),
                isBusy = false
            )
        }

        runCatching {
            connectionsClient.startAdvertising(
                buildLocalEndpointName(),
                SERVICE_ID,
                connectionLifecycleCallback(session),
                AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
            ).await()
        }.onSuccess {
            appendLog("Advertising as ${buildLocalEndpointName()}")
        }.onFailure { error ->
            reportError("Unable to advertise sync session", error)
            stop()
        }
    }

    suspend fun startDiscovery(tripId: String?) {
        stop()
        _state.update {
            it.copy(
                tripId = tripId,
                mode = NearbySyncMode.DISCOVERING,
                statusMessage = "Discovering nearby sync hosts",
                errorMessage = null,
                lastSyncSummary = null,
                logLines = emptyList(),
                discoveredPeers = emptyList(),
                connectedPeerName = null,
                localEndpointName = buildLocalEndpointName(),
                isBusy = false
            )
        }

        runCatching {
            connectionsClient.startDiscovery(
                SERVICE_ID,
                endpointDiscoveryCallback(session),
                DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
            ).await()
        }.onSuccess {
            appendLog("Looking for nearby trip sync sessions")
        }.onFailure { error ->
            reportError("Unable to start discovery", error)
            stop()
        }
    }

    suspend fun requestConnection(peer: NearbySyncPeer) {
        if (state.value.mode != NearbySyncMode.DISCOVERING || state.value.isBusy) return
        clearError()
        watchForTimeout()
        knownEndpointNames[peer.endpointId] = peer.endpointName
        _state.update { it.copy(statusMessage = "Requesting connection to ${peer.endpointName}", isBusy = true) }
        runCatching {
            connectionsClient.requestConnection(
                buildLocalEndpointName(),
                peer.endpointId,
                connectionLifecycleCallback(session)
            ).await()
        }.onFailure { error ->
            reportError("Unable to connect to ${peer.endpointName}", error)
            _state.update { it.copy(isBusy = false) }
        }
    }

    fun stop() {
        session++
        scope.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        operationMutex = Mutex()
        connectedEndpointId = null
        sentManifests.clear()
        peerSupportsApplyConfirmation = false
        awaitingIncomingBundle = false
        awaitingApplyConfirmation = false
        awaitingOutgoingTransfer = false
        runCatching {
            connectionsClient.stopAdvertising()
            connectionsClient.stopDiscovery()
            connectionsClient.stopAllEndpoints()
        }
        discoveredPeers.clear()
        knownEndpointNames.clear()
        incomingFilePayloads.values.forEach { (_, payload) ->
            deleteIncomingPayload(payload)
        }
        incomingFilePayloads.clear()
        outgoingBundleFiles.values.forEach(File::delete)
        outgoingBundleFiles.clear()
        _state.update {
            it.copy(
                tripId = null,
                mode = NearbySyncMode.IDLE,
                discoveredPeers = emptyList(),
                connectedPeerName = null,
                statusMessage = "Idle",
                isBusy = false
            )
        }
    }

    private suspend fun sendLocalManifest(
        endpointId: String,
        tripId: String,
        allowEmptyLocal: Boolean = false
    ) {
        if (!sentManifests.add(endpointId)) return
        val manifest = withContext(Dispatchers.IO) {
            if (allowEmptyLocal) coordinator.buildLocalManifestOrEmpty(tripId)
            else coordinator.buildLocalManifest(tripId)
                ?: error("The hosted trip no longer exists. Open another trip and retry.")
        }
        val message = NearbySyncControlMessage(
            type = CONTROL_TYPE_MANIFEST,
            tripId = tripId,
            manifest = manifest
        )
        val bytes = serializeOutgoingControlPayload(message, controlPayloadAdapter).encodeToByteArray()
        require(bytes.size <= ConnectionsClient.MAX_BYTES_DATA_SIZE) {
            "This trip has too many records for the sync manifest."
        }
        connectionsClient.sendPayload(endpointId, Payload.fromBytes(bytes)).await()
        appendLog("Sent manifest to ${knownEndpointNames[endpointId] ?: endpointId}")
    }

    private suspend fun sendLocalBundle(endpointId: String, tripId: String) {
        val bundle = withContext(Dispatchers.IO) { coordinator.buildLocalBundle(tripId) }
            ?: error("The trip was removed before it could be sent")
        val syncDir = File(context.cacheDir, "trip-sync/outgoing").apply { mkdirs() }
        val bundleFile = withContext(Dispatchers.IO) {
            File.createTempFile("bundle-", ".json", syncDir).apply { writeText(bundleAdapter.toJson(bundle)) }
        }
        val payload = Payload.fromFile(bundleFile)
        outgoingBundleFiles[payload.id] = bundleFile
        try {
            connectionsClient.sendPayload(endpointId, payload).await()
        } catch (error: Exception) {
            outgoingBundleFiles.remove(payload.id)?.delete()
            throw error
        }
        if (bundle.trip.metadata.deletedAt != null) {
            appendLog("Sent deleted-trip tombstone to ${knownEndpointNames[endpointId] ?: endpointId}")
        } else {
            appendLog("Sent bundle to ${knownEndpointNames[endpointId] ?: endpointId}")
        }
    }

    private suspend fun handleReceivedManifest(
        endpointId: String,
        manifest: TripSyncManifest,
        controlTripId: String
    ) {
        val remoteTripId = controlTripId.normalizedSyncTripId()
            ?: (manifest.tripId as Any?).normalizedSyncTripId()
            ?: throw IllegalStateException("Received sync manifest without a valid trip ID")

        val tripId = state.value.tripId
        if (tripId != null && tripId != remoteTripId) {
            error("The other device selected a different trip. Stop and join the intended trip.")
        }

        val normalizedManifest = manifest.withNormalizedTripId(remoteTripId)
        require(normalizedManifest.protocolVersion == TripSyncManifest.CURRENT_PROTOCOL_VERSION) {
            "These app versions cannot sync (this device: ${TripSyncManifest.CURRENT_PROTOCOL_VERSION}, other device: ${normalizedManifest.protocolVersion}). Update both devices to the same latest app version and retry."
        }

        if (tripId == null) {
            _state.update {
                it.copy(
                    tripId = remoteTripId,
                    statusMessage = "Connected to remote trip $remoteTripId"
                )
            }
        }

        // Always reply, including when the joining device already owns this trip.
        // Sending at most once avoids manifest ping-pong between peers.
        sendLocalManifest(endpointId, remoteTripId, allowEmptyLocal = true)
        val plan = withContext(Dispatchers.IO) { coordinator.compareWithRemoteManifestOrEmpty(normalizedManifest) }
        val remoteTripRecord = normalizedManifest.records.firstOrNull { record ->
            record.entityType == com.wanderlog.android.domain.model.sync.SyncEntityType.TRIP &&
                record.id == remoteTripId
        }
        val summary = buildString {
            append("Manifest compared")
            append(": push ")
            append(plan.recordsToPush.size)
            append(", pull ")
            append(plan.recordsToPull.size)
            append(", unchanged ")
            append(plan.unchangedKeys.size)
        }
        awaitingIncomingBundle = plan.recordsToPull.isNotEmpty()
        awaitingOutgoingTransfer = plan.recordsToPush.isNotEmpty()
        awaitingApplyConfirmation = awaitingOutgoingTransfer && peerSupportsApplyConfirmation
        _state.update { it.copy(lastSyncSummary = summary, statusMessage = summary,
            isBusy = awaitingIncomingBundle || awaitingApplyConfirmation || awaitingOutgoingTransfer) }
        watchForTimeout()
        appendLog(summary)

        if (remoteTripRecord?.deletedAt != null) {
            appendLog("Remote trip $remoteTripId is deleted; sync will remove the local copy if that tombstone wins")
        }

        if (plan.recordsToPush.isNotEmpty()) {
            sendLocalBundle(endpointId, remoteTripId)
        } else if (plan.recordsToPull.isNotEmpty()) {
            appendLog("Waiting for the other device's changes")
        } else if (plan.recordsToPull.isEmpty()) {
            appendLog("Trips are already in sync")
        }
    }

    private suspend fun handleReceivedBundle(endpointId: String, payload: Payload) {
        var file: File? = null
        try {
            file = withContext(Dispatchers.IO) { copyIncomingPayloadToCache(payload) }
                ?: error("Received bundle has no readable file data")
            val bundle = withContext(Dispatchers.IO) {
                parseIncomingTripSyncBundle(file.readText(), bundleAdapter, bundlePayloadAdapter)
            } ?: error("Unable to parse incoming sync bundle")
            require(bundle.trip.id == state.value.tripId) { "Received bundle for a different trip" }
            val result = withContext(Dispatchers.IO) { coordinator.applyRemoteBundle(bundle) }
            val summary = "Applied ${result.appliedRecords} remote records, skipped ${result.skippedRecords}"
            awaitingIncomingBundle = false
            _state.update { it.copy(lastSyncSummary = summary, statusMessage = summary, isBusy = awaitingApplyConfirmation || awaitingOutgoingTransfer) }
            appendLog(summary)
            val ack = serializeOutgoingControlPayload(NearbySyncControlMessage("applied", bundle.trip.id), controlPayloadAdapter)
            connectionsClient.sendPayload(endpointId, Payload.fromBytes(ack.encodeToByteArray())).await()
        } finally {
            file?.delete()
            deleteIncomingPayload(payload)
        }
    }

    private fun copyIncomingPayloadToCache(payload: Payload): File? {
        val payloadFile = payload.asFile() ?: return null
        val incomingDir = File(context.cacheDir, "trip-sync/incoming").apply { mkdirs() }
        val targetFile = File.createTempFile("incoming-", ".json", incomingDir)

        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val uri = payloadFile.asUri() ?: error("Incoming file URI is unavailable")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(targetFile).use { output -> input.copyTo(output) }
                } ?: error("Incoming file could not be opened")
            } else {
                val sourceFile = payloadFile.asJavaFile() ?: error("Incoming file is unavailable")
                sourceFile.inputStream().use { input ->
                    FileOutputStream(targetFile).use { output -> input.copyTo(output) }
                }
            }
            targetFile
        }.getOrElse { error ->
            targetFile.delete()
            reportError("Unable to copy incoming bundle", error)
            null
        }
    }

    private fun deleteIncomingPayload(payload: Payload) {
        val payloadFile = payload.asFile() ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                payloadFile.asUri()?.let { uri ->
                    context.contentResolver.delete(uri, null, null)
                }
            } else {
                payloadFile.asJavaFile()?.delete()
            }
        }
    }

    private fun appendLog(message: String) {
        _state.update { current ->
            val nextLogs = (current.logLines + message).takeLast(MAX_LOG_LINES)
            current.copy(logLines = nextLogs)
        }
    }

    private fun reportError(message: String, throwable: Throwable) {
        if (throwable is CancellationException) throw throwable
        val fullMessage = throwable.message?.let { "$message: $it" } ?: message
        appendLog(fullMessage)
        _state.update { it.copy(errorMessage = fullMessage, statusMessage = fullMessage, isBusy = false) }
    }

    private fun clearPeer(endpointId: String) {
        discoveredPeers.remove(endpointId)
        knownEndpointNames.remove(endpointId)
        _state.update { it.copy(discoveredPeers = discoveredPeers.values.toList()) }
    }

    private fun buildLocalEndpointName(): String {
        val model = Build.MODEL.orEmpty().ifBlank { "Android" }
        val suffix = syncMetadataStamp.currentDeviceId().takeLast(4)
        return "Wanderlog $model $suffix"
    }

    private fun endpointDiscoveryCallback(generation: Int) = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            if (generation != session) return
            val peer = NearbySyncPeer(endpointId = endpointId, endpointName = info.endpointName)
            discoveredPeers[endpointId] = peer
            knownEndpointNames[endpointId] = info.endpointName
            _state.update { it.copy(discoveredPeers = discoveredPeers.values.toList()) }
            appendLog("Found ${info.endpointName}")
        }

        override fun onEndpointLost(endpointId: String) {
            if (generation != session) return
            if (connectedEndpointId != endpointId) {
                appendLog("Lost ${knownEndpointNames[endpointId] ?: endpointId}")
                clearPeer(endpointId)
            }
        }
    }

    private fun connectionLifecycleCallback(generation: Int) = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            if (generation != session) return
            knownEndpointNames[endpointId] = connectionInfo.endpointName
            appendLog("Connection initiated with ${connectionInfo.endpointName}")
            scope.launch {
                runCatching {
                    connectionsClient.acceptConnection(endpointId, payloadCallback(generation)).await()
                }.onFailure { error ->
                    reportError("Unable to accept connection", error)
                }
            }
        }

        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            if (generation != session) return
            if (resolution.status.statusCode == ConnectionsStatusCodes.STATUS_OK) {
                connectedEndpointId = endpointId
                connectionsClient.stopAdvertising()
                connectionsClient.stopDiscovery()
                discoveredPeers.clear()
                val endpointName = knownEndpointNames[endpointId] ?: endpointId
                watchForTimeout()
                _state.update {
                    it.copy(
                        mode = NearbySyncMode.CONNECTED,
                        connectedPeerName = endpointName,
                        statusMessage = "Connected to $endpointName; comparing trips",
                        isBusy = true,
                        discoveredPeers = discoveredPeers.values.toList()
                    )
                }
                appendLog("Connected to $endpointName")
                scope.launch {
                    val tripId = state.value.tripId
                    if (tripId != null) {
                        runCatching { operationMutex.withLock { sendLocalManifest(endpointId, tripId) } }
                            .onFailure { error -> reportError("Unable to send manifest", error) }
                    }
                }
            } else {
                val status = resolution.status
                appendLog("Connection failed: ${statusCodeDescription(status)}")
                _state.update { it.copy(isBusy = false, statusMessage = "Connection failed") }
            }
        }

        override fun onDisconnected(endpointId: String) {
            if (generation != session) return
            val endpointName = knownEndpointNames[endpointId] ?: endpointId
            appendLog("Disconnected from $endpointName")
            stop()
            _state.update {
                it.copy(
                    mode = NearbySyncMode.IDLE,
                    connectedPeerName = null,
                    statusMessage = "Disconnected",
                    isBusy = false
                )
            }
        }
    }

    private fun payloadCallback(generation: Int) = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (generation != session || connectedEndpointId != endpointId) return
            when (payload.type) {
                Payload.Type.BYTES -> {
                    val bytes = payload.asBytes() ?: return
                    scope.launch {
                        runCatching {
                            operationMutex.withLock {
                            val message = parseIncomingControlPayload(
                                payload = bytes.decodeToString(),
                                controlPayloadAdapter = controlPayloadAdapter
                            )
                            if (message?.type == CONTROL_TYPE_MANIFEST && message.manifest != null) {
                                peerSupportsApplyConfirmation = message.supportsApplyConfirmation
                                handleReceivedManifest(
                                    endpointId = endpointId,
                                    manifest = message.manifest,
                                    controlTripId = message.tripId
                                )
                            } else if (message?.type == "applied" && message.tripId == state.value.tripId) {
                                awaitingApplyConfirmation = false
                                _state.update { it.copy(isBusy = awaitingIncomingBundle || awaitingOutgoingTransfer, statusMessage = if (awaitingIncomingBundle) "Changes saved on the other device; waiting for its changes" else "Other device confirmed changes saved") }
                                appendLog("Other device confirmed changes saved")
                            } else error("Unrecognized sync message. Update both devices and retry.")
                            }
                        }.onFailure { error ->
                            reportError("Unable to handle control payload", error)
                        }
                    }
                }

                Payload.Type.FILE -> {
                    incomingFilePayloads[payload.id] = endpointId to payload
                    appendLog("Receiving bundle file from ${knownEndpointNames[endpointId] ?: endpointId}")
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            if (generation != session) return
            if (update.status == PayloadTransferUpdate.Status.IN_PROGRESS) watchForTimeout()
            when (update.status) {
                PayloadTransferUpdate.Status.SUCCESS -> {
                    outgoingBundleFiles.remove(update.payloadId)?.let { file ->
                        file.delete()
                        awaitingOutgoingTransfer = false
                        _state.update { it.copy(isBusy = awaitingIncomingBundle || awaitingApplyConfirmation) }
                        if (!peerSupportsApplyConfirmation) {
                            _state.update { it.copy(isBusy = awaitingIncomingBundle, statusMessage = "Bundle delivered; check the other device to confirm it was saved") }
                        }
                    }
                    val pending = incomingFilePayloads.remove(update.payloadId)
                    if (pending != null) {
                        scope.launch {
                            runCatching {
                                operationMutex.withLock { handleReceivedBundle(endpointId = pending.first, payload = pending.second) }
                            }.onFailure { error ->
                                reportError("Unable to apply incoming bundle", error)
                            }
                        }
                    }
                }

                PayloadTransferUpdate.Status.FAILURE,
                PayloadTransferUpdate.Status.CANCELED -> {
                    outgoingBundleFiles.remove(update.payloadId)?.delete()
                    incomingFilePayloads.remove(update.payloadId)?.second?.let(::deleteIncomingPayload)
                    reportError("Transfer interrupted. Stop and retry with both devices awake", IllegalStateException("Transfer failed or was canceled"))
                }
            }
        }
    }

    private fun statusCodeDescription(status: Status): String = when (status.statusCode) {
        ConnectionsStatusCodes.STATUS_OK -> "OK"
        else -> status.statusMessage ?: "status ${status.statusCode}"
    }

    private companion object {
        const val MAX_LOG_LINES = 20
        const val CONTROL_TYPE_MANIFEST = "manifest"
        const val SERVICE_ID = BuildConfig.APPLICATION_ID + ".trip.sync"
        val STRATEGY: Strategy = Strategy.P2P_POINT_TO_POINT
    }
}

private fun Any?.normalizedSyncTripId(): String? =
    normalizedSyncString()

private fun Any?.normalizedSyncString(): String? =
    (this as? String)?.trim()?.takeIf { it.isNotBlank() }

internal fun parseIncomingControlPayload(
    payload: String,
    controlPayloadAdapter: JsonAdapter<Map<String, Any?>>
): NearbySyncControlMessage? {
    val payloadMap = controlPayloadAdapter.fromJson(payload) ?: return null
    val type = payloadMap["type"].normalizedSyncString() ?: return null
    val controlTripId = payloadMap["tripId"].normalizedSyncTripId()
    val manifest = when (val manifestValue = payloadMap["manifest"]) {
        is Map<*, *> -> manifestValue.toIncomingTripSyncManifest(controlTripId)
        else -> null
    }

    return NearbySyncControlMessage(
        type = type,
        tripId = controlTripId ?: manifest?.tripId ?: "",
        manifest = manifest,
        supportsApplyConfirmation = payloadMap["supportsApplyConfirmation"] == true
    )
}

internal fun serializeOutgoingControlPayload(
    message: NearbySyncControlMessage,
    controlPayloadAdapter: JsonAdapter<Map<String, Any?>>
): String {
    val payload = linkedMapOf<String, Any?>(
        "type" to message.type,
        "tripId" to message.tripId,
        "manifest" to message.manifest?.toOutgoingControlPayloadMap(),
        "supportsApplyConfirmation" to message.supportsApplyConfirmation
    )
    return controlPayloadAdapter.toJson(payload)
}

private fun Map<*, *>.toIncomingTripSyncManifest(controlTripId: String?): TripSyncManifest? {
    val tripIdValue = this["tripId"].normalizedSyncTripId() ?: controlTripId
    return buildIncomingTripSyncManifest(
        controlTripId = controlTripId,
        tripIdValue = tripIdValue,
        protocolVersionValue = this["protocolVersion"],
        generatedAtValue = this["generatedAt"],
        recordsValue = this["records"]
    )
}

private fun TripSyncManifest.toOutgoingControlPayloadMap(): Map<String, Any?> = linkedMapOf(
    "protocolVersion" to protocolVersion,
    "tripId" to tripId,
    "generatedAt" to generatedAt,
    "records" to records.map(TripSyncRecord::toOutgoingControlPayloadMap)
)

private fun TripSyncRecord.toOutgoingControlPayloadMap(): Map<String, Any?> = linkedMapOf(
    "entityType" to entityType.name,
    "id" to id,
    "updatedAt" to updatedAt,
    "deletedAt" to deletedAt,
    "lastModifiedByDeviceId" to lastModifiedByDeviceId,
    "contentHash" to contentHash,
    "sizeBytes" to sizeBytes
)

internal fun parseIncomingTripSyncBundle(
    payload: String,
    bundleAdapter: JsonAdapter<TripSyncBundle>,
    bundlePayloadAdapter: JsonAdapter<Map<String, Any?>>
): TripSyncBundle? {
    val normalizedPayload = normalizeIncomingTripSyncBundlePayload(payload, bundlePayloadAdapter)
    return bundleAdapter.fromJson(normalizedPayload)
}

internal fun normalizeIncomingTripSyncBundlePayload(
    payload: String,
    bundlePayloadAdapter: JsonAdapter<Map<String, Any?>>
): String {
    val payloadMap = bundlePayloadAdapter.fromJson(payload)?.toMutableMap() ?: return payload
    payloadMap["protocolVersion"] = payloadMap["protocolVersion"]
        .toPositiveIntOrDefault(TripSyncManifest.CURRENT_PROTOCOL_VERSION)
    return bundlePayloadAdapter.toJson(payloadMap)
}

internal fun buildIncomingTripSyncManifest(
    controlTripId: String?,
    tripIdValue: Any?,
    protocolVersionValue: Any?,
    generatedAtValue: Any?,
    recordsValue: Any?
): TripSyncManifest? {
    val tripId = tripIdValue.normalizedSyncTripId() ?: controlTripId ?: return null
    return TripSyncManifest(
        protocolVersion = protocolVersionValue.toPositiveIntOrDefault(TripSyncManifest.CURRENT_PROTOCOL_VERSION),
        tripId = tripId,
        generatedAt = generatedAtValue.toLongOrDefault(0L),
        records = recordsValue.toIncomingTripSyncRecords()
    )
}

private fun Any?.toIncomingTripSyncRecords(): List<TripSyncRecord> = when (this) {
    is JSONArray -> buildList {
        for (index in 0 until length()) {
            optJSONObject(index)?.toIncomingTripSyncRecord()?.let(::add)
        }
    }
    is Iterable<*> -> mapNotNull { rawRecord ->
        (rawRecord as? Map<*, *>)?.toIncomingTripSyncRecord()
    }
    else -> emptyList()
}

private fun JSONObject.toIncomingTripSyncRecord(): TripSyncRecord? {
    val entityType = opt("entityType").normalizedSyncString()
        ?.let { value -> runCatching { SyncEntityType.valueOf(value) }.getOrNull() }
        ?: return null
    val recordId = opt("id").normalizedSyncString() ?: return null

    return TripSyncRecord(
        entityType = entityType,
        id = recordId,
        updatedAt = optLong("updatedAt", 0L),
        deletedAt = optNullableLong("deletedAt"),
        lastModifiedByDeviceId = opt("lastModifiedByDeviceId").normalizedSyncString().orEmpty(),
        contentHash = opt("contentHash").normalizedSyncString(),
        sizeBytes = optNullableLong("sizeBytes")
    )
}

private fun Map<*, *>.toIncomingTripSyncRecord(): TripSyncRecord? {
    val entityType = this["entityType"].normalizedSyncString()
        ?.let { value -> runCatching { SyncEntityType.valueOf(value) }.getOrNull() }
        ?: return null
    val recordId = this["id"].normalizedSyncString() ?: return null

    return TripSyncRecord(
        entityType = entityType,
        id = recordId,
        updatedAt = this["updatedAt"].toLongOrDefault(0L),
        deletedAt = this["deletedAt"].toNullableLong(),
        lastModifiedByDeviceId = this["lastModifiedByDeviceId"].normalizedSyncString().orEmpty(),
        contentHash = this["contentHash"].normalizedSyncString(),
        sizeBytes = this["sizeBytes"].toNullableLong()
    )
}

private fun JSONObject.optNullableLong(name: String): Long? = when (val value = opt(name)) {
    null, JSONObject.NULL -> null
    is Number -> value.toLong()
    is String -> value.toLongOrNull()
    else -> null
}

private fun Any?.toLongOrDefault(defaultValue: Long): Long = when (this) {
    is Number -> toLong()
    is String -> toLongOrNull()
    else -> null
} ?: defaultValue

private fun Any?.toPositiveIntOrDefault(defaultValue: Int): Int = when (this) {
    is Number -> toInt()
    is String -> toIntOrNull()
    else -> null
}?.takeIf { it > 0 } ?: defaultValue

private fun Any?.toNullableLong(): Long? = when (this) {
    null -> null
    is Number -> toLong()
    is String -> toLongOrNull()
    else -> null
}

private fun TripSyncManifest.withNormalizedTripId(tripId: String): TripSyncManifest = TripSyncManifest(
    protocolVersion = protocolVersion,
    tripId = tripId,
    generatedAt = generatedAt,
    records = (records as? List<TripSyncRecord>).orEmpty()
)
