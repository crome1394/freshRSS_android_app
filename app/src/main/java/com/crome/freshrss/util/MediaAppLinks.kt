package com.crome.freshrss.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import java.net.URI

/**
 * Opens media http(s) links in installed native apps (YouTube, Spotify, …)
 * via an “Open with” chooser. Falls back to any handler, then the browser.
 *
 * Pure host→package mapping is unit-testable; launch logic needs [Context].
 */
object MediaAppLinks {

    /** Well-known packages for core media hosts (v1). */
    val YOUTUBE_PACKAGES = listOf(
        "com.google.android.youtube",
        "org.schabi.newpipe",
    )
    val SPOTIFY_PACKAGES = listOf("com.spotify.music")
    val SOUNDCLOUD_PACKAGES = listOf("com.soundcloud.android")
    val VIMEO_PACKAGES = listOf("com.vimeo.android.videoapp")
    val TWITCH_PACKAGES = listOf("tv.twitch.android.app")

    /**
     * Preferred app packages for [url], ordered most-specific first.
     * Empty if the host is not a known media service (generic chooser still works).
     */
    fun preferredPackages(url: String): List<String> {
        val host = hostOf(url) ?: return emptyList()
        return when {
            isYoutubeHost(host) -> YOUTUBE_PACKAGES
            host.contains("spotify") -> SPOTIFY_PACKAGES
            host.contains("soundcloud") -> SOUNDCLOUD_PACKAGES
            host.contains("vimeo") -> VIMEO_PACKAGES
            host.contains("twitch") -> TWITCH_PACKAGES
            else -> emptyList()
        }
    }

    fun isYoutubeHost(host: String): Boolean {
        val h = host.lowercase().removePrefix("www.")
        return h == "youtu.be" ||
            h == "youtube.com" ||
            h.endsWith(".youtube.com") ||
            h == "youtube-nocookie.com" ||
            h.endsWith(".youtube-nocookie.com")
    }

    fun hostOf(url: String): String? =
        try {
            URI(url.trim()).host?.lowercase()
        } catch (_: Exception) {
            null
        }

    /**
     * Show an “Open with” sheet preferring installed media apps for this URL.
     * @return true if an activity / chooser was started
     */
    fun openWithChooser(
        context: Context,
        raw: String?,
        title: String = "Open with",
        labelIfBlocked: String = "Blocked unsafe link",
    ): Boolean {
        val normalized = SafeUrls.normalizeHttpUrl(raw)
        if (normalized == null) {
            if (!raw.isNullOrBlank()) {
                Toast.makeText(context, labelIfBlocked, Toast.LENGTH_SHORT).show()
            }
            return false
        }

        val uri = Uri.parse(normalized)
        val pm = context.packageManager

        // Intents targeted at installed preferred apps (YouTube, Spotify, …).
        val appIntents = preferredPackages(normalized)
            .filter { isPackageInstalled(pm, it) }
            .map { pkg ->
                Intent(Intent.ACTION_VIEW, uri).apply {
                    setPackage(pkg)
                    addCategory(Intent.CATEGORY_BROWSABLE)
                }
            }

        return try {
            when {
                appIntents.isNotEmpty() -> {
                    // Lead with first media app; other media apps appear as extras.
                    // Also append a generic VIEW so browsers remain available.
                    val chooserTarget = appIntents.first()
                    val extras = ArrayList<Intent>()
                    appIntents.drop(1).forEach { extras += it }
                    extras += Intent(Intent.ACTION_VIEW, uri).apply {
                        addCategory(Intent.CATEGORY_BROWSABLE)
                    }
                    val chooser = Intent.createChooser(chooserTarget, title).apply {
                        if (extras.isNotEmpty()) {
                            putExtra(Intent.EXTRA_INITIAL_INTENTS, extras.toTypedArray())
                        }
                    }
                    context.startActivity(chooser)
                    true
                }
                else -> {
                    // No known package installed: try non-browser handlers first (API 30+),
                    // then a full chooser that includes browsers.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val nonBrowser = Intent(Intent.ACTION_VIEW, uri).apply {
                            addCategory(Intent.CATEGORY_BROWSABLE)
                            addFlags(Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER)
                        }
                        try {
                            context.startActivity(Intent.createChooser(nonBrowser, title))
                            return true
                        } catch (_: Exception) {
                            // Fall through to any handler.
                        }
                    }
                    val any = Intent(Intent.ACTION_VIEW, uri).apply {
                        addCategory(Intent.CATEGORY_BROWSABLE)
                    }
                    context.startActivity(Intent.createChooser(any, title))
                    true
                }
            }
        } catch (_: Exception) {
            // Last resort: open without chooser (default handler).
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                true
            } catch (_: Exception) {
                Toast.makeText(context, "No app can open this link", Toast.LENGTH_SHORT).show()
                false
            }
        }
    }

    private fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean =
        try {
            pm.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
}
