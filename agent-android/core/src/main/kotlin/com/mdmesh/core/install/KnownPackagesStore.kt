package com.mdmesh.core.install

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The installed-package baseline [InstalledPackagePoller] diffs against. `null` from [get] means
 * "never seeded" — the poller's first run must record the current package set as the baseline
 * without flagging any of it as a pending install (those are pre-existing apps, not something the
 * user just did).
 */
@Singleton
class KnownPackagesStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("mdm_known_packages", Context.MODE_PRIVATE)

    fun get(): Set<String>? = if (prefs.contains(KEY)) prefs.getStringSet(KEY, emptySet()) else null

    fun set(packages: Set<String>) {
        prefs.edit().putStringSet(KEY, packages).apply()
    }

    private companion object {
        const val KEY = "packages"
    }
}
