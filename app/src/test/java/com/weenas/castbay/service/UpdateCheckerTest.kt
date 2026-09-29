package com.weenas.castbay.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    @Test
    fun comparesVersionsAsNumbers() {
        assertTrue(UpdateChecker.isNewer("1.0.72", "1.0.70"))
        assertTrue(UpdateChecker.isNewer("1.0.100", "1.0.99"))
        assertTrue(UpdateChecker.isNewer("1.1", "1.0.99"))
        assertFalse(UpdateChecker.isNewer("1.0.70", "1.0.70"))
        assertFalse(UpdateChecker.isNewer("1.0.57", "1.0.70"))
        assertFalse(UpdateChecker.isNewer("nightly", "1.0.70"))
    }

    @Test
    fun picksTheHighestPublishedRelease() {
        val json = """[
            {"tag_name": "v1.0.80", "draft": true, "html_url": "d"},
            {"tag_name": "v1.0.57", "prerelease": true, "html_url": "a"},
            {"tag_name": "v1.0.72", "prerelease": true, "html_url": "b", "assets": [
                {"name": "CastBay.apk", "browser_download_url": "x"},
                {"name": "CastBay-1.0.72.apk", "browser_download_url": "apk", "digest": "sha256:${"ab".repeat(32)}"}
            ]},
            {"tag_name": "test", "html_url": "c"}
        ]"""
        assertEquals(AppUpdate("1.0.72", "b", listOf("apk"), "ab".repeat(32)), UpdateChecker.newest(json))
        assertNull(UpdateChecker.newest("[]"))
    }

    @Test
    fun readsTheWebsitesLatestJsonAndKeepsIt() {
        val json = """{"version": "1.0.86", "sha256": "${"cd".repeat(32)}", "urls": ["site", "github"], "page": "p"}"""
        val update = AppUpdate.fromJson(json)
        assertEquals(AppUpdate("1.0.86", "p", listOf("site", "github"), "cd".repeat(32)), update)
        assertEquals(update, AppUpdate.fromJson(update!!.toJson()))
        assertNull(AppUpdate.fromJson("not json"))
    }
}
