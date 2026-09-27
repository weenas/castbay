package com.weenas.castbay.dlna

import org.junit.Assert.assertEquals
import org.junit.Test

class DlnaSenderTest {
    @Test
    fun namesTheAppFromItsUserAgent() {
        assertEquals(DlnaSender.IQIYI, DlnaSender.fromUserAgent("UPnP/1.0 IQIYIDLNA/iqiyidlna/NewDLNA/1.0"))
        assertEquals(DlnaSender.QQ_MUSIC, DlnaSender.fromUserAgent("QQMusic/12.0 UPnP/1.0"))
        assertEquals("", DlnaSender.fromUserAgent("Linux/5.10 UPnP/1.0 Portable SDK for UPnP devices/1.14"))
        assertEquals("", DlnaSender.fromUserAgent(null))
    }
}
