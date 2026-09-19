package com.mdmesh.core.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Last check-in/enroll failure, for surfacing on the status screen. [CheckInCoordinator]
 * records it on every failed cycle and clears it on the next successful one, so an operator
 * staring at "Enrolling…" can see *why* it isn't progressing without adb.
 */
@Singleton
class SyncStatus @Inject constructor() {

    data class Failure(val message: String, val atMillis: Long)

    private val _lastError = MutableStateFlow<Failure?>(null)
    val lastError: StateFlow<Failure?> = _lastError

    fun recordFailure(t: Throwable) {
        _lastError.value = Failure(t.message ?: t.javaClass.simpleName, System.currentTimeMillis())
    }

    fun clear() {
        _lastError.value = null
    }
}
