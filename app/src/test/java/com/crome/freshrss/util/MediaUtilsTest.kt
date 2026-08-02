package com.crome.freshrss.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaUtilsTest {

    @Test
    fun detectsYoutubeAsVideo() {
        assertTrue(MediaUtils.isVideoUrl("https://www.youtube.com/watch?v=abc123"))
        assertTrue(MediaUtils.isVideoUrl("https://youtu.be/abc123"))
        assertFalse(MediaUtils.isAudioUrl("https://www.youtube.com/watch?v=abc123"))
    }

    @Test
    fun detectsDirectVideoExtension() {
        assertTrue(MediaUtils.isVideoUrl("https://cdn.example.com/clip.m4v"))
        assertTrue(MediaUtils.isVideoUrl("https://cdn.example.com/clip.mp4?token=1"))
    }

    @Test
    fun detectsDirectAudioExtension() {
        assertTrue(MediaUtils.isAudioUrl("https://cdn.example.com/ep.mp3"))
        assertTrue(MediaUtils.isAudioUrl("https://cdn.example.com/ep.m4a?dl=1"))
        assertFalse(MediaUtils.isVideoUrl("https://cdn.example.com/ep.mp3"))
    }

    @Test
    fun detectsPodcastHosts() {
        assertTrue(MediaUtils.isAudioUrl("https://soundcloud.com/user/track"))
        assertTrue(MediaUtils.isAudioUrl("https://podcasts.apple.com/us/podcast/x/id1?i=2"))
        assertTrue(MediaUtils.isAudioUrl("https://open.spotify.com/episode/xyz"))
    }

    @Test
    fun extractMediaFromHtmlAudioTag() {
        val html = """<p>Episode</p><audio controls src="https://cdn.example.com/show.mp3"></audio>"""
        val m = MediaUtils.extractMedia(html, "https://example.com/post", "My Feed", null)
        assertTrue(m.isAudio)
        assertFalse(m.isVideo)
        assertEquals("https://cdn.example.com/show.mp3", m.mediaUrl)
    }

    @Test
    fun extractMediaFromYoutubeFeed() {
        val m = MediaUtils.extractMedia(
            html = "<p>new video</p>",
            url = "https://www.youtube.com/watch?v=xyz",
            feedTitle = "Channel on YouTube",
            siteUrl = "https://youtube.com/channel/1",
        )
        assertTrue(m.isVideo)
        assertTrue(m.mediaUrl.contains("youtube") || m.mediaUrl.contains("youtu"))
    }

    @Test
    fun plainArticleIsNeither() {
        val m = MediaUtils.extractMedia(
            html = "<p>Just text news</p>",
            url = "https://news.example.com/story/1",
            feedTitle = "World News",
            siteUrl = "https://news.example.com",
        )
        assertFalse(m.isVideo)
        assertFalse(m.isAudio)
    }
}
