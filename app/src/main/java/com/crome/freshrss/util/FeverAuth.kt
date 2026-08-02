package com.crome.freshrss.util

import java.security.MessageDigest

/** Fever: api_key = md5(username:apiPassword) — same as freshrss-api.sh api_key(). */
object FeverAuth {
    fun apiKey(user: String, apiPassword: String): String {
        val md = MessageDigest.getInstance("MD5")
        val bytes = md.digest("$user:$apiPassword".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
