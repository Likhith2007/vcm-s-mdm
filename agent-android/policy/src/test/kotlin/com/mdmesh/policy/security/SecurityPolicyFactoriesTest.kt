package com.mdmesh.policy.security

import org.junit.Assert.assertEquals
import org.junit.Test

class SecurityPolicyFactoriesTest {

    @Test
    fun `security policy capability keys are distinct`() {
        val keys = setOf(
            UsbDebugPolicy.CAPABILITY_KEY,
            FactoryResetPolicy.CAPABILITY_KEY,
            UnknownSourcesPolicy.CAPABILITY_KEY,
            AdminRemovalPolicy.CAPABILITY_KEY,
        )

        assertEquals(4, keys.size)
    }
}
