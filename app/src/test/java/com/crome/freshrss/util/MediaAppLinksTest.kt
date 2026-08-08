package com.crome.freshrss.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaAppLinksTest {

    @Test
    fun youtubePackagesForWatchAndShorts() {
        val pkgs = MediaAppLinks.preferredPackages("https://www.youtube.com/watch?v=abc123")
        assertTrue(pkgs.contains("com.google.android.youtube"))
        assertEquals(
            MediaAppLinks.YOUTUBE_PACKAGES,
            MediaAppLinks.preferredPackages("https://youtu.be/abc123"),
        )
        assertTrue(
            MediaAppLinks.preferredPackages("https://www.youtube.com/shorts/xyz")
                .contains("com.google.android.youtube"),
        )
    }

    @Test
    fun spotifyAndSoundcloud() {
        assertEquals(
            MediaAppLinks.SPOTIFY_PACKAGES,
            MediaAppLinks.preferredPackages("https://open.spotify.com/episode/xyz"),
        )
        assertEquals(
            MediaAppLinks.SOUNDCLOUD_PACKAGES,
            MediaAppLinks.preferredPackages("https://soundcloud.com/user/track"),
        )
    }

    @Test
    fun vimeoAndTwitch() {
        assertEquals(
            MediaAppLinks.VIMEO_PACKAGES,
            MediaAppLinks.preferredPackages("https://vimeo.com/12345"),
        )
        assertEquals(
            MediaAppLinks.TWITCH_PACKAGES,
            MediaAppLinks.preferredPackages("https://www.twitch.tv/somechannel"),
        )
    }

    @Test
    fun plainNewsHasNoPreferredPackage() {
        assertTrue(
            MediaAppLinks.preferredPackages("https://news.example.com/story/1").isEmpty(),
        )
    }

    @Test
    fun youtubeHostDetection() {
        assertTrue(MediaAppLinks.isYoutubeHost("www.youtube.com"))
        assertTrue(MediaAppLinks.isYoutubeHost("youtu.be"))
        assertTrue(MediaAppLinks.isYoutubeHost("m.youtube.com"))
        assertFalse(MediaAppLinks.isYoutubeHost("notyoutube.com"))
    }
}
