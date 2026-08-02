package com.crome.freshrss.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerUrlTest {

    @Test
    fun defaultsBareHostToHttps() {
        val r = ServerUrl.normalize("freshrss.example.com", allowCleartext = false)
        assertTrue(r.ok)
        assertEquals("https://freshrss.example.com", r.normalized)
    }

    @Test
    fun allowsHttpsAlways() {
        val r = ServerUrl.normalize("https://news.local/fr", allowCleartext = false)
        assertTrue(r.ok)
        assertEquals("https://news.local/fr", r.normalized)
    }

    @Test
    fun blocksHttpWhenCleartextDisallowed() {
        val r = ServerUrl.normalize("http://10.0.0.5", allowCleartext = false)
        assertFalse(r.ok)
        assertTrue(r.error!!.contains("HTTP", ignoreCase = true))
    }

    @Test
    fun allowsHttpWhenCleartextEnabled() {
        val r = ServerUrl.normalize("http://10.0.0.5/", allowCleartext = true)
        assertTrue(r.ok)
        assertEquals("http://10.0.0.5", r.normalized)
        assertTrue(r.isCleartext)
    }

    @Test
    fun rejectsBlank() {
        assertFalse(ServerUrl.normalize("", allowCleartext = true).ok)
        assertFalse(ServerUrl.normalize("   ", allowCleartext = true).ok)
    }

    @Test
    fun stripsTrailingSlash() {
        val r = ServerUrl.normalize("https://example.com/freshrss/", allowCleartext = false)
        assertEquals("https://example.com/freshrss", r.normalized)
    }
}
