package com.crome.freshrss.util

import java.util.regex.Pattern

/**
 * Video / audio URL heuristics (ported from freshrss-api.sh VIDEO_RE / extract_media,
 * extended with common podcast and direct-audio patterns).
 *
 * Audio detection is conservative: clear file extensions, HTML media tags, and
 * well-known podcast hosts — not every page that mentions "podcast".
 */
object MediaUtils {

    data class ExtractedMedia(
        val mediaUrl: String = "",
        val isVideo: Boolean = false,
        val isAudio: Boolean = false,
    )

    private val videoHost: Pattern = Pattern.compile(
        """(youtube\.com|youtu\.be|youtube-nocookie\.com|vimeo\.com|twitch\.tv|\.m4v\b|\.mp4\b|\.webm\b|\.mkv\b|/shorts/)""",
        Pattern.CASE_INSENSITIVE,
    )

    private val directVideo: Pattern = Pattern.compile(
        """\.(m4v|mp4|webm|mkv)(\?|$)""",
        Pattern.CASE_INSENSITIVE,
    )

    private val directAudio: Pattern = Pattern.compile(
        """\.(mp3|m4a|aac|ogg|opus|wav|flac|oga)(\?|$)""",
        Pattern.CASE_INSENSITIVE,
    )

    private val audioHost: Pattern = Pattern.compile(
        """(soundcloud\.com|podcasts\.apple\.com|open\.spotify\.com/episode|anchor\.fm|""" +
            """podbean\.com|libsyn\.com|transistor\.fm|buzzsprout\.com|simplecast\.com|""" +
            """megaphone\.fm|captivate\.fm|spreaker\.com|castbox\.fm|overcast\.fm|""" +
            """pocketcasts\.com|stitcher\.com|iheart\.com/podcast)""",
        Pattern.CASE_INSENSITIVE,
    )

    private val mediaSrc: Pattern = Pattern.compile(
        """<(?:video|audio|source|iframe)[^>]+(?:src|data-src)=["']([^"']+)["']""",
        Pattern.CASE_INSENSITIVE,
    )

    private val audioTag: Pattern = Pattern.compile(
        """<audio\b""",
        Pattern.CASE_INSENSITIVE,
    )

    private val ytWatch: Pattern = Pattern.compile(
        """(https?://(?:www\.)?(?:youtube\.com/(?:watch\?v=|shorts/|embed/|live/)|youtu\.be/)[^\s"'<>&]+)""",
        Pattern.CASE_INSENSITIVE,
    )

    private val httpUrl: Pattern = Pattern.compile(
        """https?://[^\s"'<>]+""",
        Pattern.CASE_INSENSITIVE,
    )

    fun isVideoUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return videoHost.matcher(url).find() || directVideo.matcher(url).find()
    }

    fun isAudioUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        // Pure video hosts are not audio (even if "sound" appears in path elsewhere).
        if (isVideoUrl(url) && !directAudio.matcher(url).find()) return false
        return directAudio.matcher(url).find() || audioHost.matcher(url).find()
    }

    fun extractMedia(
        html: String?,
        url: String?,
        feedTitle: String?,
        siteUrl: String?,
    ): ExtractedMedia {
        val candidates = linkedSetOf<String>()

        for (blob in listOfNotNull(url, html)) {
            val yt = ytWatch.matcher(blob)
            while (yt.find()) {
                val g = yt.group(1) ?: continue
                candidates += g.trimEnd(')', '.', ',', ';')
            }
        }
        if (!html.isNullOrBlank()) {
            val src = mediaSrc.matcher(html)
            while (src.find()) {
                val g = src.group(1) ?: continue
                candidates += g
            }
            val http = httpUrl.matcher(html)
            while (http.find()) {
                val raw = http.group() ?: continue
                val u = raw.trimEnd(')', '.', ',', ';', ']')
                if (directVideo.matcher(u).find() ||
                    directAudio.matcher(u).find() ||
                    audioHost.matcher(u).find() ||
                    isVideoUrl(u)
                ) {
                    candidates += u
                }
            }
        }
        if (!url.isNullOrBlank()) candidates += url

        var videoMedia = ""
        var audioMedia = ""
        for (u in candidates) {
            if (videoMedia.isEmpty() && isVideoUrl(u)) videoMedia = u
            if (audioMedia.isEmpty() && isAudioUrl(u)) audioMedia = u
            if (videoMedia.isNotEmpty() && audioMedia.isNotEmpty()) break
        }

        val feedBlob = (feedTitle.orEmpty()) + " " + (siteUrl.orEmpty())
        val feedIsYt = Regex("youtube", RegexOption.IGNORE_CASE).containsMatchIn(feedBlob)

        if (videoMedia.isEmpty() && feedIsYt && !url.isNullOrBlank()) {
            videoMedia = url
        }

        val hasAudioTag = !html.isNullOrBlank() && audioTag.matcher(html).find()
        if (audioMedia.isEmpty() && hasAudioTag) {
            // Prefer a direct audio candidate from src; else leave blank but flag audio.
            audioMedia = candidates.firstOrNull { isAudioUrl(it) }.orEmpty()
        }

        val isVid = videoMedia.isNotEmpty() || (feedIsYt && !url.isNullOrBlank())
        val isAud = audioMedia.isNotEmpty() ||
            (hasAudioTag && !isVid) ||
            candidates.any { directAudio.matcher(it).find() }

        val finalMedia = when {
            isVid && videoMedia.isNotEmpty() -> videoMedia
            isAud && audioMedia.isNotEmpty() -> audioMedia
            isVid -> url.orEmpty()
            isAud && !url.isNullOrBlank() && isAudioUrl(url) -> url
            else -> audioMedia.ifBlank { videoMedia }
        }

        return ExtractedMedia(
            mediaUrl = finalMedia,
            isVideo = isVid,
            // Independent flag: an enclosure can be audio even when page also embeds video.
            isAudio = isAud,
        )
    }

    fun playableUrl(mediaUrl: String?, articleUrl: String?): String =
        mediaUrl?.takeIf { it.isNotBlank() } ?: articleUrl.orEmpty()
}
