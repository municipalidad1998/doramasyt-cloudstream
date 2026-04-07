@file:Suppress("UNUSED_PARAMETER","unused")
package com.lagradost.nicehttp

import org.jsoup.nodes.Document
import java.util.concurrent.TimeUnit

class NiceResponse {
    val text: String get() = ""
    val document: Document get() = Document("")
}

class Requests {
    suspend fun get(
        url: String, headers: Map<String,String> = emptyMap(),
        referer: String? = null, params: Map<String,String> = emptyMap(),
        cookies: Map<String,String> = emptyMap(), allowRedirects: Boolean = true,
        timeout: Int = 0, timeoutUnit: TimeUnit = TimeUnit.SECONDS,
        cacheTime: Long = 0L, interceptor: okhttp3.Interceptor? = null,
        verify: Boolean = true, parser: ResponseParser? = null
    ): NiceResponse = NiceResponse()

    suspend fun post(
        url: String, headers: Map<String,String> = emptyMap(),
        referer: String? = null, params: Map<String,String> = emptyMap(),
        cookies: Map<String,String> = emptyMap(), data: Map<String,String> = emptyMap(),
        files: List<Any> = emptyList(), json: Any? = null,
        requestBody: okhttp3.RequestBody? = null, allowRedirects: Boolean = true,
        timeout: Int = 0, timeoutUnit: TimeUnit = TimeUnit.SECONDS,
        cacheTime: Long = 0L, interceptor: okhttp3.Interceptor? = null,
        verify: Boolean = true, parser: ResponseParser? = null
    ): NiceResponse = NiceResponse()
}

interface ResponseParser
