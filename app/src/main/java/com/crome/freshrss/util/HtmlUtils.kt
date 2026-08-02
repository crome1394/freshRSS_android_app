package com.crome.freshrss.util

import com.crome.freshrss.data.model.ReaderDefaults

/**
 * Lightweight HTML → plain text, matching freshrss-api.sh strip_html.
 */
object HtmlUtils {

    fun stripHtml(html: String?): String {
        if (html.isNullOrBlank()) return ""
        var t = html
        t = t.replace(Regex("(?is)<script[^>]*>.*?</script>"), " ")
        t = t.replace(Regex("(?is)<style[^>]*>.*?</style>"), " ")
        t = t.replace(Regex("(?is)<br\\s*/?>"), "\n")
        t = t.replace(Regex("(?is)</p>"), "\n\n")
        t = t.replace(Regex("(?is)<[^>]+>"), " ")
        t = t
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
        t = t.replace(Regex("[ \\t]{2,}"), " ")
        t = t.replace(Regex("\n{3,}"), "\n\n")
        return t.trim()
    }

    fun summary(plain: String, max: Int = ReaderDefaults.SUMMARY_MAX): String =
        if (plain.length <= max) plain else plain.take(max) + "…"

    fun truncateHtml(html: String, max: Int = ReaderDefaults.HTML_MAX): String =
        if (html.length <= max) html else html.take(max) + "…"

    fun truncateText(text: String, max: Int = ReaderDefaults.TEXT_MAX): String =
        if (text.length <= max) text else text.take(max) + "…"
}
