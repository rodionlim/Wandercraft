package com.wanderlog.android.presentation.sync

import android.net.Uri
import com.wanderlog.android.data.sync.TripFileStore
import com.wanderlog.android.domain.model.sync.TripSyncBundle
import kotlinx.coroutines.CancellationException
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wanderlog.android.data.sync.NearbySyncMode
import com.wanderlog.android.data.sync.NearbySyncPeer
import com.wanderlog.android.data.sync.NearbyTripSyncTransport
import com.wanderlog.android.domain.repository.TripRepository
import com.wanderlog.android.presentation.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TripSyncUiState(
    val tripId: String? = null,
    val tripName: String = "",
    val mode: NearbySyncMode = NearbySyncMode.IDLE,
    val localEndpointName: String = "",
    val discoveredPeers: List<NearbySyncPeer> = emptyList(),
    val connectedPeerName: String? = null,
    val statusMessage: String = "Idle",
    val lastSyncSummary: String? = null,
    val logLines: List<String> = emptyList(),
    val errorMessage: String? = null,
    val fileBusy: Boolean = false,
    val fileMessage: String? = null,
    val pendingFile: TripSyncBundle? = null,
    val isBusy: Boolean = false
)

@HiltViewModel
class TripSyncViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val tripRepository: TripRepository,
    private val tripFileStore: TripFileStore,
    private val nearbyTripSyncTransport: NearbyTripSyncTransport
) : ViewModel() {

    private val initialTripId = savedStateHandle.get<String>(Screen.TripSync.ARG_TRIP_ID)

    private val _state = MutableStateFlow(TripSyncUiState(tripId = initialTripId))
    val state: StateFlow<TripSyncUiState> = _state.asStateFlow()

    init {
        nearbyTripSyncTransport.stop()
        refreshTripName(initialTripId)

        viewModelScope.launch {
            nearbyTripSyncTransport.state.collect { transportState ->
                val previousTripId = _state.value.tripId
                val nextTripId = transportState.tripId ?: previousTripId
                _state.update {
                    it.copy(
                        tripId = nextTripId,
                        mode = transportState.mode,
                        localEndpointName = transportState.localEndpointName,
                        discoveredPeers = transportState.discoveredPeers,
                        connectedPeerName = transportState.connectedPeerName,
                        statusMessage = transportState.statusMessage,
                        lastSyncSummary = transportState.lastSyncSummary,
                        logLines = transportState.logLines,
                        errorMessage = transportState.errorMessage,
                        isBusy = transportState.isBusy
                    )
                }
                if (nextTripId != previousTripId || (_state.value.tripName.isBlank() && nextTripId != null)) {
                    refreshTripName(nextTripId)
                }
            }
        }
    }

    fun saveTripFile(uri: Uri) {
        val tripId = state.value.tripId ?: return
        fileOperation {
            try {
                tripFileStore.save(tripId, uri)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                throw IllegalStateException("Save failed: ${e.message ?: "storage unavailable"}. The selected location may contain an incomplete file; delete that file and try again.", e)
            }
            _state.update { it.copy(fileMessage = "Trip file saved. If you chose Drive, wait for its upload to finish, then share the file with the other account in Google Drive.") }
        }
    }

    fun openTripFile(uri: Uri) = fileOperation {
        _state.update { it.copy(pendingFile = null) }
        val bundle = tripFileStore.read(uri)
        _state.update { it.copy(pendingFile = bundle) }
    }

    fun dismissFilePreview() { _state.update { it.copy(pendingFile = null) } }

    fun mergeTripFile() {
        val bundle = state.value.pendingFile ?: return
        fileOperation {
            val result = tripFileStore.merge(bundle)
            _state.update { it.copy(pendingFile = null, tripId = bundle.trip.id,
                fileMessage = "Trip imported: ${result.appliedRecords} records updated, ${result.skippedRecords} unchanged.") }
            refreshTripName(bundle.trip.id)
        }
    }

    private fun fileOperation(block: suspend () -> Unit) {
        if (state.value.fileBusy) return
        nearbyTripSyncTransport.stop()
        _state.update { it.copy(fileBusy = true, fileMessage = null) }
        viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _state.update { it.copy(fileMessage = e.message ?: "Could not complete the trip file operation.") } }
            finally { _state.update { it.copy(fileBusy = false) } }
        }
    }

    fun startHosting() {
        if (state.value.fileBusy || state.value.pendingFile != null) return
        val tripId = state.value.tripId ?: return
        viewModelScope.launch { nearbyTripSyncTransport.startAdvertising(tripId) }
    }

    fun startDiscovery() {
        if (state.value.fileBusy || state.value.pendingFile != null) return
        viewModelScope.launch { nearbyTripSyncTransport.startDiscovery(initialTripId) }
    }

    fun connect(peer: NearbySyncPeer) {
        if (state.value.fileBusy || state.value.pendingFile != null) return
        viewModelScope.launch { nearbyTripSyncTransport.requestConnection(peer) }
    }

    fun stopSession() {
        nearbyTripSyncTransport.stop()
    }

    fun clearError() {
        nearbyTripSyncTransport.clearError()
    }

    override fun onCleared() {
        super.onCleared()
        nearbyTripSyncTransport.stop()
    }

    private fun refreshTripName(tripId: String?) {
        viewModelScope.launch {
            val tripName = tripId?.let { tripRepository.getTripById(it)?.name }.orEmpty()
            _state.update {
                if (it.tripId == tripId) it.copy(tripName = tripName) else it
            }
        }
    }
}
