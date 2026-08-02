package com.crome.freshrss.util

/**
 * Backward-compatible facade over [MediaUtils].
 *
 * New code should call [MediaUtils] directly. Existing call sites that
 * destructure `Pair(mediaUrl, isVideo)` keep working via [extractMedia].
 */
object VideoUtils {

    fun isVideoUrl(url: String?): Boolean = MediaUtils.isVideoUrl(url)

    fun isAudioUrl(url: String?): Boolean = MediaUtils.isAudioUrl(url)

    /** Legacy pair API: media URL + isVideo. Prefer [MediaUtils.extractMedia]. */
    fun extractMedia(
        html: String?,
        url: String?,
        feedTitle: String?,
        siteUrl: String?,
    ): Pair<String, Boolean> {
        val m = MediaUtils.extractMedia(html, url, feedTitle, siteUrl)
        return m.mediaUrl to m.isVideo
    }

    fun extractMediaFull(
        html: String?,
        url: String?,
        feedTitle: String?,
        siteUrl: String?,
    ): MediaUtils.ExtractedMedia = MediaUtils.extractMedia(html, url, feedTitle, siteUrl)

    fun playableUrl(mediaUrl: String?, articleUrl: String?): String =
        MediaUtils.playableUrl(mediaUrl, articleUrl)
}
