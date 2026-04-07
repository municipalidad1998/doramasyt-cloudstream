@file:Suppress("unused","UNUSED_PARAMETER")
@file:JvmName("ExtractorApiKt")
package com.lagradost.cloudstream3.utils

enum class ExtractorLinkType { VIDEO, M3U8, DASH, MAGNET }

enum class Qualities(val value: Int, val defaultPriority: Int = 0) {
    Unknown(0), P144(144), P240(240), P360(360), P480(480), P720(720), P1080(1080), P1440(1440), P2160(2160);
    companion object {
        fun getStringByInt(quality: Int?) = quality?.toString() ?: "Unknown"
        fun getStringByIntFull(quality: Int) = "$quality p"
    }
}

open class ExtractorLink(
    open val source: String = "", open val name: String = "", open val url: String = "",
    open var referer: String = "", open var quality: Int = 0,
    open val type: ExtractorLinkType = ExtractorLinkType.VIDEO,
    open var headers: Map<String,String> = emptyMap(),
    open var extractorData: String? = null,
    open var audioTracks: List<Any> = emptyList()
) {
    constructor(source: String, name: String, url: String, referer: String, quality: Int,
        isM3u8: Boolean, headers: Map<String,String> = emptyMap(), extractorData: String? = null
    ) : this(source, name, url, referer, quality,
        if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO, headers, extractorData)
    
    constructor(source: String, name: String, url: String, referer: String, quality: Int,
        headers: Map<String,String>, extractorData: String?, type: ExtractorLinkType, audioTracks: List<Any>
    ) : this(source, name, url, referer, quality, type, headers, extractorData, audioTracks)

    constructor(source: String, name: String, url: String, referer: String, quality: Int?,
        type: ExtractorLinkType, headers: Map<String,String>, extractorData: String?
    ) : this(source, name, url, referer, quality ?: 0, type, headers, extractorData)
    
    fun isM3u8() = type == ExtractorLinkType.M3U8
    fun isDash() = type == ExtractorLinkType.DASH
    fun setType(t: ExtractorLinkType) { /* no-op in val */ }
}

class ExtractorLinkBuilder(val source: String, val name: String, val url: String, val type: ExtractorLinkType) {
    var referer: String = ""
    var quality: Int = 0
    var headers: Map<String, String> = emptyMap()
    var extractorData: String? = null
}

suspend fun newExtractorLink(
    source: String, name: String, url: String,
    type: ExtractorLinkType = ExtractorLinkType.VIDEO,
    block: suspend ExtractorLinkBuilder.() -> Unit = {}
): ExtractorLink {
    val b = ExtractorLinkBuilder(source, name, url, type).apply { block() }
    return ExtractorLink(b.source, b.name, b.url, b.referer, b.quality, b.type, b.headers, b.extractorData)
}

abstract class ExtractorApi {
    open val name: String = ""
    open val mainUrl: String = ""
    open val requiresReferer: Boolean = false
    abstract suspend fun getUrl(url: String, referer: String? = null,
        subtitleCallback: (com.lagradost.cloudstream3.SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit)
    fun fixUrl(url: String): String = if (url.startsWith("http")) url else "$mainUrl$url"
}

fun getExtractorApis(): List<ExtractorApi> = emptyList()
fun getExtractorApiFromName(name: String): ExtractorApi? = null
fun inferTypeFromUrl(url: String): ExtractorLinkType = when {
    url.contains(".m3u8") -> ExtractorLinkType.M3U8
    url.contains(".mpd") -> ExtractorLinkType.DASH
    else -> ExtractorLinkType.VIDEO
}
val INFER_TYPE = ExtractorLinkType.VIDEO
suspend fun loadExtractor(url: String, referer: String? = null,
    subtitleCallback: (com.lagradost.cloudstream3.SubtitleFile) -> Unit = {},
    callback: (ExtractorLink) -> Unit = {}) {}
