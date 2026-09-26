package com.weenas.castbay.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceNameTest {
    @Test
    fun tvNameIsTheSystemNameThenTheModel() {
        assertEquals("Living Room TV", DeviceName.tvName(" Living Room TV ", "BRAVIA 4K UR2"))
        assertEquals("BRAVIA 4K UR2", DeviceName.tvName(null, "BRAVIA 4K UR2"))
        assertEquals("TCL Android TV", DeviceName.tvName("TCL_Android_TV", null))
        assertEquals("", DeviceName.tvName(" ", ""))
        assertEquals("", DeviceName.tvName("castbay", null))
    }

    @Test
    fun appendsTheTvName() {
        assertEquals("CastBay (客厅电视)", DeviceName.compose("CastBay", "客厅电视"))
        assertEquals("CastBay", DeviceName.compose("CastBay", ""))
        assertEquals("CastBay (BRAVIA)", DeviceName.compose(" ", "BRAVIA"))
        // Not twice when the chosen name already has it.
        assertEquals("客厅电视", DeviceName.compose("客厅电视", "客厅电视"))
    }

    @Test
    fun staysWithinTheMdnsLimit() {
        val name = DeviceName.compose("CastBay", "客厅".repeat(30))
        assertTrue(name.toByteArray().size <= 60)
        assertTrue(name.startsWith("CastBay (客厅") && name.endsWith(")"))
        assertTrue(DeviceName.compose("映".repeat(40), "TV").toByteArray().size <= 60)
    }

    @Test
    fun settingsAdvertiseTheComposedName() {
        val settings = ReceiverSettings(tvName = "BRAVIA")
        assertEquals("CastBay (BRAVIA)", settings.advertisedName)
        assertEquals("CastBay", settings.copy(appendTvName = false).advertisedName)
        assertTrue(settings.copy(appendTvName = false).needsRestartComparedTo(settings))
    }
}
