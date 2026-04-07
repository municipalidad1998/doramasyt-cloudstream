@file:Suppress("unused","UNUSED_PARAMETER")
package com.lagradost.nicehttp

import org.jsoup.nodes.Document
import okhttp3.*
import java.util.concurrent.TimeUnit

interface ResponseParser {
    fun parse(text: String, klass: kotlin.reflect.KClass<*>): Any
    fun parseSafe(text: String, klass: kotlin.reflect.KClass<*>): Any?
    fun writeValueAsString(obj: Any): String
    fun getMapper(): Any
}

class NiceResponse(val okhttpResponse: Response = Response.Builder().build(), val parser: ResponseParser? = null) {
    val text: String get() = ""
    val document: Document get() = Document("")
    val code: Int get() = 200
    val url: String get() = ""
    val cookies: Map<String,String> get() = emptyMap()
    val headers: Headers get() = Headers.of()
    val body: ResponseBody? get() = null
    val size: Long? get() = null
    val isSuccessful: Boolean get() = true
}

open class Requests() {
    constructor(
        client: OkHttpClient, defaultHeaders: Map<String,String> = emptyMap(),
        defaultReferer: String = "", defaultCookies: Map<String,String> = emptyMap(),
        defaultData: Map<String,String> = emptyMap(), defaultCacheTime: Int = 0,
        defaultCacheTimeUnit: TimeUnit = TimeUnit.SECONDS, defaultTimeOut: Long = 0L,
        responseParser: ResponseParser? = null
    ) : this()
    
    var defaultHeaders: Map<String,String> = emptyMap()
    var defaultReferer: String = ""
    var defaultCookies: Map<String,String> = emptyMap()
    var defaultData: Map<String,String> = emptyMap()
    var defaultCacheTime: Int = 0
    var defaultCacheTimeUnit: TimeUnit = TimeUnit.SECONDS
    var defaultTimeOut: Long = 30000L
    var responseParser: ResponseParser? = null
    var baseClient: OkHttpClient = OkHttpClient()
    
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
