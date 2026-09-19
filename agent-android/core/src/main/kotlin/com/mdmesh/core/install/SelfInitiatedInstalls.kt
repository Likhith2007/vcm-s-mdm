package com.mdmesh.core.install

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks package names an MDM-initiated install is currently in flight for, so
 * [com.mdmesh.agent.service.PackageEventReceiver] can tell an admin-pushed `app.install` apart
 * from an install the device's own user started (Play Store / sideload) — only the latter gets
 * gated by the pending-approval flow. [InstallManager] marks a package right before committing its
 * own install session and clears it if the commit doesn't succeed (see [InstallManager] for why
 * that exact timing matters).
 */
@Singleton
class SelfInitiatedInstalls @Inject constructor() {

    private val marked = ConcurrentHashMap.newKeySet<String>()

    fun mark(packageName: String) {
        marked += packageName
    }

    /** Returns true and forgets [packageName] if it was marked; false otherwise. */
    fun consume(packageName: String): Boolean = marked.remove(packageName)

    fun clear(packageName: String) {
        marked -= packageName
    }
}
