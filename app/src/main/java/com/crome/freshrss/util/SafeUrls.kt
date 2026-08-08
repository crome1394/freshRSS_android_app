package com.crome.freshrss.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import java.net.URI

/**
 * Hardens outbound link opens: only `http` / `https` schemes are allowed.
 * Blocks `javascript:`, `file:`, `content:`, custom schemes from malicious feeds.
 *
 * Validation uses [java.net.URI] so it works in JVM unit tests; Android [Uri]
 * is only created when launching an intent.
 */
object SafeUrls {

    private val ALLOWED = setOf("http", "https")

    /**
     * Returns a normalized http(s) URL string, or null if unsafe / blank.
     * Prefer this over Android Uri when you only need validation.
     */
    fun normalizeHttpUrl(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        val uri = try {
            URI(trimmed)
        } catch (_: Exception) {
            return null
        }
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme !in ALLOWED) return null
        // Require a host so bare "http:" / "https:" cannot slip through.
        if (uri.host.isNullOrBlank()) return null
        return trimmed
    }

    /** Android Uri for safe http(s) links, or null. */
    fun parseHttpUrl(raw: String?): Uri? =
        normalizeHttpUrl(raw)?.let { Uri.parse(it) }

    fun isSafeHttpUrl(raw: String?): Boolean = normalizeHttpUrl(raw) != null

    /**
     * Launch a viewer for [raw] if it is a safe http(s) URL.
     * Uses the default handler (no chooser). Prefer [openWithChooser] when the
     * user should pick among YouTube, Spotify, browser, etc.
     * @return true if an activity was started
     */
    fun openInBrowser(
        context: Context,
        raw: String?,
        labelIfBlocked: String = "Blocked unsafe link",
    ): Boolean {
        val normalized = normalizeHttpUrl(raw)
        if (normalized == null) {
            if (!raw.isNullOrBlank()) {
                Toast.makeText(context, labelIfBlocked, Toast.LENGTH_SHORT).show()
            }
            return false
        }
        return try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(normalized)))
            true
        } catch (_: Exception) {
            Toast.makeText(context, "No app can open this link", Toast.LENGTH_SHORT).show()
            false
        }
    }

    /**
     * Show the system “Open with” chooser for a safe http(s) URL, preferring
     * installed media apps (YouTube, Spotify, …) when the host matches.
     * @return true if the chooser (or a handler) was started
     */
    fun openWithChooser(
        context: Context,
        raw: String?,
        title: String = "Open with",
        labelIfBlocked: String = "Blocked unsafe link",
    ): Boolean = MediaAppLinks.openWithChooser(context, raw, title, labelIfBlocked)
}
