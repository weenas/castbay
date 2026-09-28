package com.weenas.castbay.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SenderReportTest {
    /** Values in SenderReport.KEYS order, as an iPhone reported them. */
    private val iphone = doubleArrayOf(59.0, 60.0, 45.0, 0.0, 1.0, 2.0, 5.0, 0.011179660480084209, 7172663.77, 22731512.13)

    @Test
    fun parsesAnIphoneReport() {
        val report = SenderReport.parse(iphone)!!
        assertEquals(59, report.sentFps)
        assertEquals(60, report.targetFps)
        assertEquals(45, report.screenFps)
        assertEquals(3, report.droppedFps)
        assertEquals(5, report.roundTripMs)
        assertEquals(7172663L, report.usedBps)
        assertEquals(22731512L, report.capacityBps)
    }

    @Test
    fun missingValuesAreLeftOut() {
        // The first report of a session carries fewer keys (no screen or drop counts).
        val first = doubleArrayOf(0.0, 60.0, -1.0, -1.0, -1.0, -1.0, 8.0, 0.05, 15.6, 21832256.8)
        val report = SenderReport.parse(first)!!
        assertNull(report.screenFps)
        assertEquals(0, report.droppedFps)
        assertNull(SenderReport.parse(DoubleArray(10) { -1.0 }))
    }
}
