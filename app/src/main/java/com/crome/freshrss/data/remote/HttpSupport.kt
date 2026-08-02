package com.crome.freshrss.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.TimeUnit

internal object HttpSupport {
    const val USER_AGENT = "freshrss-android/0.1 (quickshell-port)"

    fun defaultClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()

    suspend fun execute(client: OkHttpClient, request: Request): Response =
        withContext(Dispatchers.IO) {
            client.newCall(request).execute()
        }

    suspend fun bodyString(client: OkHttpClient, request: Request): String =
        execute(client, request).use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw HttpException(resp.code, body.take(400))
            }
            body
        }

    fun formBody(vararg pairs: Pair<String, String>): FormBody {
        val b = FormBody.Builder()
        for ((k, v) in pairs) b.add(k, v)
        return b.build()
    }
}

class HttpException(val code: Int, val bodyPreview: String) :
    Exception("HTTP $code: $bodyPreview")
