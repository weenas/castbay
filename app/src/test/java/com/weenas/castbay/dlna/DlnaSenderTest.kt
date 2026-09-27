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

    @Test
    fun namesTheAppFromTheMediaHost() {
        assertEquals(DlnaSender.NETEASE_MUSIC, DlnaSender.fromUrl("http://m701.music.126.net/2026/x.m4a?dlna=1"))
        assertEquals(DlnaSender.IQIYI, DlnaSender.fromUrl("http://mus.video.iqiyi.com/mus/1.m3u8"))
        assertEquals("", DlnaSender.fromUrl("http://192.168.1.5:8080/song.mp3"))
        assertEquals("", DlnaSender.fromUrl(null))
    }
}
