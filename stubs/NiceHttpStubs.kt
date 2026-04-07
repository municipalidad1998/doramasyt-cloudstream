@file:Suppress("unused","UNUSED_PARAMETER")
package com.lagradost.nicehttp

import org.jsoup.nodes.Document
import okhttp3.*
import java.util.concurrent.TimeUnit

interface ResponseParser

class NiceResponse {
    val text: String get() = ""
    val document: Document get() = Document("")
    val code: Int get() = 200
    val url: String get() = ""
    val cookies: Map<String,String> get() = emptyMap()
    val isSuccessful: Boolean get() = true
    val okhttpResponse: Response? get() = null
    val body: ResponseBody? get() = null
    val headers: Headers? get() = null
    val size: Long? get() = null
}

open class Requests {
    var defaultHeaders: Map<String,String> = emptyMap()
    var defaultReferer: String = ""
    var defaultCookies: Map<String,String> = emptyMap()
    var defaultData: Map<String,String> = emptyMap()
    var defaultCacheTime: Int = 0
    var defaultCacheTimeUnit: TimeUnit = TimeUnit.SECONDS
    var defaultTimeOut: Long = 30000L
    var responseParser: ResponseParser? = null
    var baseClient: OkHttpClient? = null
    
    suspend fun get(
        url: String, headers: Map<String,String> = emptyMap(), referer: String? = null,
        params: Map<String,String> = emptyMap(), cookies: Map<String,String> = emptyMap(),
        allowRedirects: Boolean = true, cacheTime: Int = 0, cacheTimeUnit: TimeUnit = TimeUnit.SECONDS,
        timeout: Long = 0L, interceptor: Interceptor? = null, verify: Boolean = true,
        parser: ResponseParser? = null
    ): NiceResponse = NiceResponse()
    
    suspend fun post(
        url: String, headers: Map<String,String> = emptyMap(), referer: String? = null,
        params: Map<String,String> = emptyMap(), cookies: Map<String,String> = emptyMap(),
        data: Map<String,String> = emptyMap(), files: List<Any> = emptyList(),
        json: Any? = null, requestBody: RequestBody? = null,
        allowRedirects: Boolean = true, cacheTime: Int = 0, cacheTimeUnit: TimeUnit = TimeUnit.SECONDS,
        timeout: Long = 0L, interceptor: Interceptor? = null, verify: Boolean = true,
        parser: ResponseParser? = null
    ): NiceResponse = NiceResponse()
}
