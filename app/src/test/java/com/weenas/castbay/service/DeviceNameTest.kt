package com.weenas.castbay.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceNameTest {
    @Test
    fun renamesTheFormerDefaultOnFirstMigration() {
        assertEquals("映灣", DeviceName.migratedReceiverName("CastBay", false, "映灣"))
    }

    @Test
    fun missingOrBlankReceiverNamesUseTheNewDefault() {
        for (name in listOf(null, "", "  \t")) {
            assertEquals("映灣", DeviceName.migratedReceiverName(name, false, "映灣"))
            assertEquals("映灣", DeviceName.migratedReceiverName(name, true, "映灣"))
        }
    }

    @Test
    fun customReceiverNamesArePreservedExactly() {
        for (name in listOf("客廳電視", "My CastBay", "CastBay 4K", " CastBay ")) {
            assertEquals(name, DeviceName.migratedReceiverName(name, false, "映灣"))
        }
    }

    @Test
    fun aLaterUserChoiceOfCastBayIsNotRenamedAgain() {
        assertEquals("CastBay", DeviceName.migratedReceiverName("CastBay", true, "映灣"))
    }

    private fun tvName(system: String?, bluetooth: String? = null, model: String? = null) =
        DeviceName.tvName(system, bluetooth, model, listOf(model))

    @Test
    fun tvNameIsTheSystemNameThenTheModel() {
        assertEquals("Living Room TV", tvName(" Living Room TV ", "Bedroom", "BRAVIA 4K UR2"))
        assertEquals("BRAVIA 4K UR2", tvName(null, model = "BRAVIA 4K UR2"))
        assertEquals("TCL Android TV", tvName("TCL_Android_TV"))
        assertEquals("", tvName(" ", "", ""))
        assertEquals("", tvName("castbay"))
    }

    @Test
    fun aProductCodeGivesWayToTheBluetoothName() {
        // TCL: the device name is the product code; the owner's name is the Bluetooth name.
        assertEquals("卧室电视TCL", tvName("tcl_m7642", "卧室电视TCL", "tcl_m7642"))
        assertEquals("卧室电视TCL", tvName("M7642", "卧室电视TCL", "m7642"))
        // With no Bluetooth name, the code is still better than nothing.
        assertEquals("tcl m7642", tvName("tcl_m7642", null, "tcl_m7642"))
    }

    @Test
    fun appendsTheTvName() {
        assertEquals("CastBay (客厅电视)", DeviceName.compose("CastBay", "客厅电视"))
        assertEquals("CastBay", DeviceName.compose("CastBay", ""))
        assertEquals("${DeviceName.BRAND} (BRAVIA)", DeviceName.compose(" ", "BRAVIA"))
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
        assertEquals("${DeviceName.BRAND} (BRAVIA)", settings.advertisedName)
        assertEquals(DeviceName.BRAND, settings.copy(appendTvName = false).advertisedName)
        assertTrue(settings.copy(appendTvName = false).needsRestartComparedTo(settings))
    }
}
