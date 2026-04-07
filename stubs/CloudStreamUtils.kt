@file:Suppress("UNUSED_PARAMETER","unused")
@file:JvmName("ExtractorApiKt")
package com.lagradost.cloudstream3.utils

enum class ExtractorLinkType { VIDEO, M3U8, DASH, MAGNET }

object Qualities {
    object Unknown { val value = 0 }
    object P360   { val value = 360 }
    object P480   { val value = 480 }
    object P720   { val value = 720 }
    object P1080  { val value = 1080 }
}

open class ExtractorLink(
    val source: String = "", val name: String = "", val url: String = "",
    var referer: String = "", var quality: Int = 0,
    val isM3u8: Boolean = false, var headers: Map<String,String> = emptyMap(),
    val type: ExtractorLinkType = ExtractorLinkType.VIDEO
)

class ExtractorLinkBuilder(val source: String, val name: String, val url: String, val type: ExtractorLinkType) {
    var referer: String = ""
    var quality: Int = 0
    var headers: Map<String, String> = emptyMap()
}

suspend fun newExtractorLink(
    source: String, name: String, url: String,
    type: ExtractorLinkType = ExtractorLinkType.VIDEO,
    block: suspend ExtractorLinkBuilder.() -> Unit = {}
): ExtractorLink {
    val b = ExtractorLinkBuilder(source, name, url, type).apply { block() }
    return ExtractorLink(source, name, url, b.referer, b.quality, type == ExtractorLinkType.M3U8, b.headers, type)
}
