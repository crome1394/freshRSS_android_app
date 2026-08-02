package com.crome.freshrss.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FeverAuthTest {
    @Test
    fun apiKey_matchesMd5OfUserColonPassword() {
        // echo -n 'admin:secret' | md5sum
        assertEquals(
            "2d1e4f91dae49cd08eaba2293e422273",
            FeverAuth.apiKey("admin", "secret"),
        )
    }

    @Test
    fun isVideoUrl_detectsYoutubeAndMp4() {
        assert(VideoUtils.isVideoUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assert(VideoUtils.isVideoUrl("https://cdn.example.com/clip.m4v"))
        assert(!VideoUtils.isVideoUrl("https://example.com/article.html"))
    }
}
