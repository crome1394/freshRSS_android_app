package com.crome.freshrss.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests — use [SafeUrls.normalizeHttpUrl] / [SafeUrls.isSafeHttpUrl]
 * (java.net.URI). Avoid [SafeUrls.parseHttpUrl] here; it needs Android Uri.
 */
class SafeUrlsTest {

    @Test
    fun allowsHttpAndHttps() {
        assertTrue(SafeUrls.isSafeHttpUrl("https://example.com/a"))
        assertTrue(SafeUrls.isSafeHttpUrl("http://10.0.0.5/freshrss"))
        assertEquals(
            "https://news.example/x?y=1",
            SafeUrls.normalizeHttpUrl("https://news.example/x?y=1"),
        )
    }

    @Test
    fun blocksDangerousSchemes() {
        assertFalse(SafeUrls.isSafeHttpUrl("javascript:alert(1)"))
        assertFalse(SafeUrls.isSafeHttpUrl("file:///etc/passwd"))
        assertFalse(SafeUrls.isSafeHttpUrl("content://com.example/x"))
        assertFalse(SafeUrls.isSafeHttpUrl("ftp://files.example/a"))
        assertFalse(SafeUrls.isSafeHttpUrl("data:text/html,hi"))
        assertNull(SafeUrls.normalizeHttpUrl("javascript:void(0)"))
    }

    @Test
    fun blocksBlankAndHostless() {
        assertFalse(SafeUrls.isSafeHttpUrl(null))
        assertFalse(SafeUrls.isSafeHttpUrl(""))
        assertFalse(SafeUrls.isSafeHttpUrl("   "))
        assertFalse(SafeUrls.isSafeHttpUrl("http:"))
        assertFalse(SafeUrls.isSafeHttpUrl("https://"))
    }

    @Test
    fun trimsWhitespace() {
        val u = SafeUrls.normalizeHttpUrl("  https://example.com/path  ")
        assertEquals("https://example.com/path", u)
        assertTrue(SafeUrls.isSafeHttpUrl("  https://example.com/path  "))
    }
}
