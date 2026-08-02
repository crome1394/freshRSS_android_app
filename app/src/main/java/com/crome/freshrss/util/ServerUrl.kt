package com.crome.freshrss.util

/**
 * Normalize and validate the FreshRSS base URL.
 *
 * - Blank host is invalid
 * - Missing scheme defaults to https
 * - http is only allowed when [allowCleartext] is true
 * - Trailing slashes are stripped
 */
object ServerUrl {

    data class Result(
        val ok: Boolean,
        val normalized: String = "",
        val error: String? = null,
        val isCleartext: Boolean = false,
    )

    fun normalize(raw: String?, allowCleartext: Boolean): Result {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) {
            return Result(ok = false, error = "Server URL is required")
        }

        val withScheme = if (trimmed.contains("://")) {
            trimmed
        } else {
            "https://$trimmed"
        }

        val uri = try {
            java.net.URI(withScheme)
        } catch (_: Exception) {
            return Result(ok = false, error = "Invalid server URL")
        }

        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            return Result(
                ok = false,
                error = "URL must start with https:// (or http:// if allowed)",
            )
        }
        if (uri.host.isNullOrBlank()) {
            return Result(ok = false, error = "URL must include a host name or IP")
        }

        val isHttp = scheme == "http"
        if (isHttp && !allowCleartext) {
            return Result(
                ok = false,
                error = "HTTP is blocked. Use https:// or enable Allow insecure HTTP in Settings.",
                isCleartext = true,
            )
        }

        val normalized = withScheme.trimEnd('/')
        return Result(ok = true, normalized = normalized, isCleartext = isHttp)
    }

    /** True if the URL uses an explicit http scheme. */
    fun isCleartextUrl(raw: String?): Boolean {
        val t = raw?.trim().orEmpty()
        if (t.isEmpty()) return false
        if (!t.contains("://")) return false
        return t.startsWith("http://", ignoreCase = true)
    }
}
