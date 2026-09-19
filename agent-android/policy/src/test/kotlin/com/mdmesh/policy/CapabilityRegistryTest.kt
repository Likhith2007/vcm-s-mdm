package com.mdmesh.policy

import com.mdmesh.policy.app.AppBlockPolicy
import com.mdmesh.policy.app.AppHidePolicy
import com.mdmesh.policy.security.AdminRemovalPolicy
import com.mdmesh.policy.security.FactoryResetPolicy
import com.mdmesh.policy.security.UnknownSourcesPolicy
import com.mdmesh.policy.security.UsbDebugPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure JVM checks for the capability keys consumed by CapabilityRegistry. */
class CapabilityRegistryTest {

    @Test
    fun `phase 1 capability keys remain stable`() {
        assertEquals("usbDebug", UsbDebugPolicy.CAPABILITY_KEY)
        assertEquals("factoryReset", FactoryResetPolicy.CAPABILITY_KEY)
        assertEquals("unknownSources", UnknownSourcesPolicy.CAPABILITY_KEY)
        assertEquals("adminRemoval", AdminRemovalPolicy.CAPABILITY_KEY)
        assertEquals("appBlock", AppBlockPolicy.CAPABILITY_KEY)
        assertEquals("appHide", AppHidePolicy.CAPABILITY_KEY)
    }
}
