package com.weenas.castbay.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MirroringDeviceQuirksTest {
    @Test fun onlyTheTestedPanasonicUsesTextureView() {
        assertTrue(MirroringDeviceQuirks.needsTextureView("Panasonic", "4KTV-JUP", 30))
        assertTrue(MirroringDeviceQuirks.needsTextureView("panasonic", "4ktv-jup", 30))
        assertFalse(MirroringDeviceQuirks.needsTextureView("Panasonic", "other", 30))
        assertFalse(MirroringDeviceQuirks.needsTextureView("Other", "4KTV-JUP", 30))
        assertFalse(MirroringDeviceQuirks.needsTextureView("Panasonic", "4KTV-JUP", 31))
    }
}
