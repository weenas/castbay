package com.weenas.castbay.util

import org.junit.Assert.assertEquals
import org.junit.Test

class LogReportTest {
    @Test
    fun personalValuesAreTakenOut() {
        assertEquals(
            "DLNA music: ⟦…⟧ · ⟦…⟧",
            LogReport.scrub("DLNA music: ${Log.personal("Song")} · ${Log.personal("http://x/a.mp3")}")
        )
    }

    @Test
    fun linksEmailsAndHardwareAddressesAreTakenOut() {
        assertEquals(
            "Playing <link> for <email> on <mac>",
            LogReport.scrub("Playing https://cdn.example.com/v.m3u8?token=1 for me@example.com on AA:BB:CC:11:22:33")
        )
    }

    @Test
    fun localAddressesStayAndInternetOnesGo() {
        assertEquals(
            "10.1.2.6 192.168.1.5 172.20.10.7 <ip> <ipv6> fe80::1",
            LogReport.scrub("10.1.2.6 192.168.1.5 172.20.10.7 8.8.8.8 2408:8207:1234::5 fe80::1")
        )
    }

    @Test
    fun versionNumbersAreNotAddresses() {
        assertEquals("BYD 23.1.4.2510219", LogReport.scrub("BYD 23.1.4.2510219"))
    }

    @Test
    fun pinsAndPasswordsAreTakenOut() {
        assertEquals(
            "*** CLIENT MUST NOW ENTER PIN = \"…\" AS AIRPLAY PASSWORD",
            LogReport.scrub("*** CLIENT MUST NOW ENTER PIN = \"4821\" AS AIRPLAY PASSWORD")
        )
    }

    @Test
    fun failuresHaveShortReasons() {
        assertEquals("timed out", LogReport.reason(java.net.SocketTimeoutException()))
        assertEquals("HTTP 403", LogReport.reason(LogReport.Refused(403)))
        assertEquals("too many reports", LogReport.reason(LogReport.TooManyReports()))
        assertEquals("address not found (DNS)", LogReport.reason(java.net.UnknownHostException("castbay.weenas.com")))
    }

    @Test
    fun keepsOnlyThisProcessesLogcatLines() {
        val lines = listOf(
            "--------- beginning of main",
            "10-04 15:21:53.785  6213  6256 I CastBay: mine",
            "10-04 15:21:53.790  2705  3117 D MDnsDS  : someone else's",
            "10-04 15:21:54.000  6213  6213 W AirPlay: mine too",
            "Unrecognized Option -",
            "Usage: logcat [options] [filterspecs]",
        )
        assertEquals(
            listOf("10-04 15:21:53.785  6213  6256 I CastBay: mine", "10-04 15:21:54.000  6213  6213 W AirPlay: mine too"),
            LogReport.processLines(lines, 6213)
        )
        assertEquals(emptyList<String>(), LogReport.processLines(listOf("Unrecognized Option -", "Usage: logcat"), 6213))
    }
}
