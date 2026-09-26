package com.weenas.castbay.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceNameTest {
    @Test
    fun usesTheTvsNameThenItsModel() {
        assertEquals("CastBay (Living Room TV)", DeviceName.compose(" Living Room TV ", "BRAVIA 4K UR2"))
        assertEquals("CastBay (BRAVIA 4K UR2)", DeviceName.compose(null, "BRAVIA 4K UR2"))
        assertEquals("CastBay (客厅电视)", DeviceName.compose("客厅电视", null))
        assertEquals("CastBay (TCL Android TV)", DeviceName.compose("TCL_Android_TV", null))
    }

    @Test
    fun plainCastBayWithoutAName() {
        assertEquals("CastBay", DeviceName.compose(null, null))
        assertEquals("CastBay", DeviceName.compose(" ", ""))
        assertEquals("CastBay", DeviceName.compose("castbay", null))
    }

    @Test
    fun staysWithinTheMdnsLimit() {
        val name = DeviceName.compose("客厅".repeat(30), null)
        assertTrue(name.toByteArray().size <= 60)
        assertTrue(name.startsWith("CastBay (客厅") && name.endsWith(")"))
    }
}
