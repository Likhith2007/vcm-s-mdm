package com.mdmesh.policy.security

import org.junit.Assert.assertEquals
import org.junit.Test

class UsbDebugPolicyTest {

    @Test
    fun `has usbDebug capability key`() {
        assertEquals("usbDebug", UsbDebugPolicy.CAPABILITY_KEY)
    }
}
