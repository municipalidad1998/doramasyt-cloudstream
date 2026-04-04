package com.cncverse

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.net.URLDecoder

class DoramasYTProvider : MainAPI() {
    override var mainUrl = "https://www.doramasyt.com"
    override var name = "DoramasYT"
    override val hasMainPage = true
    override var lang = "es"
    override val supportedTypes = setOf(TvType.AsianDrama, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "$mainUrl/doramas?p=" to "Recientes",
        "$mainUrl/emision?p=" to "En Emision",
        "$mainUrl/peliculas?p=" to "Peliculas",
        "$mainUrl/genero/k-drama?p=" to "K-Drama",
        "$mainUrl/genero/c-drama?p=" to "C-Drama",
        "$mainUrl/genero/j-drama?p=" to "J-Drama",
        "$mainUrl/genero/thai-drama?p=" to "Thai-Drama",
        "$mainUrl/genero/romance?p=" to "Romance",
        "$mainUrl/genero/comedia?p=" to "Comedia",
        "$mainUrl/genero/accion?p=" to "Accion",
        "$mainUrl/genero/drama?p=" to "Drama",
        "$mainUrl/genero/fantasia?p=" to "Fantasia",
    )

    private fun parseSearchResponse(document: org.jsoup.nodes.Document): List<SearchResponse> {
        return document.select("li.ficha_efecto").mapNotNull { li ->
            val link = li.selectFirst("a") ?: return@mapNotNull null
            val href = fixUrlNull(link.attr("href")) ?: return@mapNotNull null
            val title = li.selectFirst("h3.title_cap")?.text()?.trim() ?: return@mapNotNull null
            val img = li.selectFirst("img.lazy")
            val poster = fixUrlNull(img?.attr("data-src") ?: img?.attr("src") ?: "")

            newMovieSearchResponse(
                name = title,
                url = href,
                type = TvType.AsianDrama
            ) {
                this.posterUrl = poster
            }
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = request.data + page
        val response = app.get(url)
        val homeList = parseSearchResponse(response.document)

        return newHomePageResponse(
            listOf(
                HomePageList(request.name, homeList, isHorizontalImages = false)
            )
        )
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        val url = "$mainUrl/buscar?q=${query}"
        val response = app.get(url)
        return parseSearchResponse(response.document)
    }

    override suspend fun load(url: String): LoadResponse {
        val response = app.get(url)
        val document = response.document

        val title = document.selectFirst("h1")?.text()?.trim() ?: "Unknown"
        val poster = fixUrlNull(document.selectFirst(".img-drama img, .card-img-top img, .background")?.attr("src")
            ?: document.selectFirst(".img-drama img, .card-img-top img, .background")?.attr("data-src") ?: "")
        val backgroundPosterUrl = poster
        val plot = document.select(".sinopsis p, .description p, .text-muted:has(p) p, .card-body p").text().trim()
            .ifBlank { document.selectFirst("p:contains(sinopsis) + p, .sinopsis")?.text()?.trim() ?: "" }
        val yearText = document.selectFirst("span:contains(202), span:contains(201), .badge:contains(202), .badge:contains(201)")
            ?.text()?.trim()?.takeLast(4)?.toIntOrNull()
        val tags = document.select(".genres a, .generos a, .badge.bg-secondary, a[href*='/genero/']").map { it.text().trim() }

        val episodes = mutableListOf<Episode>()
        
        document.select("a[href*='/ver/']").forEach { ep ->
            val epHref = fixUrlNull(ep.attr("href")) ?: return@forEach
            val epTitle = ep.text().trim().ifBlank { epHref.substringAfterLast("/") }
            val epNumber = Regex("""[Ee]pisodio\s*(\d+)|[Cc]apitulo\s*(\d+)|Ep\s*(\d+)|(\d+)""").find(epTitle)?.destructured?.let { (a, b, c, d) ->
                (a + b + c + d).toIntOrNull()
            } ?: 1

            episodes.add(
                newEpisode(epHref) {
                    this.name = epTitle
                    this.episode = epNumber
                }
            )
        }

        val type = if (episodes.size > 1) TvType.TvSeries else TvType.AsianDrama

        return newTvSeriesLoadResponse(title, url, type, episodes) {
            this.posterUrl = poster
            this.backgroundPosterUrl = backgroundPosterUrl
            this.plot = plot
            this.year = yearText
            this.tags = tags
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val response = app.get(data)
        val document = response.document

        document.select("iframe").forEach { iframe ->
            val src = iframe.attr("src")
            if (src.isNotBlank()) {
                extractVideoLink(src, "Default", callback)
            }
        }

        document.select("script").forEach { script ->
            val scriptData = script.data()
            if (scriptData.contains("jwplayer") || scriptData.contains("player") || scriptData.contains("source")) {
                Regex("""["'](https?://[^"']+\.(?:mp4|m3u8|mkv)[^"']*)["']""").findAll(scriptData).forEach { match ->
                    val videoUrl = match.groupValues[1]
                    callback.invoke(
                        newExtractorLink(
                            source = name,
                            name = name,
                            url = videoUrl,
                            type = if (videoUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                        ) {
                            this.quality = Qualities.Unknown.value
                            this.referer = mainUrl
                        }
                    )
                }
            }
        }

        return true
    }

    private suspend fun extractVideoLink(url: String, serverName: String, callback: (ExtractorLink) -> Unit) {
        if (url.isBlank()) return
        
        val decodedUrl = try { URLDecoder.decode(url, "UTF-8") } catch (e: Exception) { url }
        
        when {
            decodedUrl.contains("mega.nz") -> {
                callback.invoke(
                    newExtractorLink(
                        source = "$name - $serverName",
                        name = "$name - $serverName",
                        url = decodedUrl,
                        type = ExtractorLinkType.VIDEO
                    ) {
                        this.quality = Qualities.Unknown.value
                        this.referer = "https://mega.nz"
                    }
                )
            }
            decodedUrl.contains("filemoon") || decodedUrl.contains("filemoon.sx") -> {
                val pageResponse = app.get(decodedUrl)
                val pageDoc = pageResponse.document
                val scriptData = pageDoc.select("script").map { it.data() }.joinToString("\n")
                val videoUrlMatch = Regex("""file:\s*["']([^"']+\.(?:m3u8|mp4)[^"']*)["']""").find(scriptData)
                    ?: Regex("""sources\s*=\s*\[\s*\{\s*file:\s*["']([^"']+)["']""").find(scriptData)
                if (videoUrlMatch != null) {
                    val videoUrl = videoUrlMatch.groupValues[1]
                    callback.invoke(
                        newExtractorLink(
                            source = "$name - $serverName",
                            name = "$name - $serverName",
                            url = videoUrl,
                            type = if (videoUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                        ) {
                            this.quality = Qualities.Unknown.value
                            this.referer = "https://filemoon.sx"
                        }
                    )
                }
            }
            decodedUrl.contains("dood") || decodedUrl.contains("doodstream") -> {
                val pageResponse = app.get(decodedUrl)
                val pageDoc = pageResponse.document
                val scriptData = pageDoc.select("script").map { it.data() }.joinToString("\n")
                val dsMatch = Regex("""/pass_md5/([^"']*)""").find(scriptData)
                if (dsMatch != null) {
                    val passUrl = "https://doodstream.com/pass_md5/${dsMatch.groupValues[1]}"
                    val token = app.get(passUrl).text
                    if (token.isNotBlank()) {
                        callback.invoke(
                            newExtractorLink(
                                source = "$name - $serverName",
                                name = "$name - $serverName",
                                url = "$token${generateRandomString()}",
                                type = ExtractorLinkType.VIDEO
                            ) {
                                this.quality = Qualities.Unknown.value
                                this.referer = "https://doodstream.com"
                                this.headers = mapOf("Referer" to "https://doodstream.com/")
                            }
                        )
                    }
                }
            }
            decodedUrl.contains("streamtape") -> {
                val pageResponse = app.get(decodedUrl)
                val pageDoc = pageResponse.document
                val scriptData = pageDoc.select("script").map { it.data() }.joinToString("\n")
                val urlMatch = Regex("""innerHTML\s*=\s*["']([^"']+)["']""").find(scriptData)
                if (urlMatch != null) {
                    val videoUrl = urlMatch.groupValues[1]
                    callback.invoke(
                        newExtractorLink(
                            source = "$name - $serverName",
                            name = "$name - $serverName",
                            url = videoUrl,
                            type = ExtractorLinkType.VIDEO
                        ) {
                            this.quality = Qualities.Unknown.value
                            this.referer = "https://streamtape.com"
                        }
                    )
                }
            }
            decodedUrl.contains("voe") || decodedUrl.contains("voe.sx") -> {
                val pageResponse = app.get(decodedUrl)
                val pageDoc = pageResponse.document
                val scriptData = pageDoc.select("script").map { it.data() }.joinToString("\n")
                val videoUrlMatch = Regex("""hls":\s*["']([^"']+)["']""").find(scriptData)
                    ?: Regex("""sources:\s*\[\s*\{\s*src:\s*["']([^"']+)["']""").find(scriptData)
                if (videoUrlMatch != null) {
                    val videoUrl = videoUrlMatch.groupValues[1]
                    callback.invoke(
                        newExtractorLink(
                            source = "$name - $serverName",
                            name = "$name - $serverName",
                            url = videoUrl,
                            type = if (videoUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                        ) {
                            this.quality = Qualities.Unknown.value
                            this.referer = "https://voe.sx"
                        }
                    )
                }
            }
            decodedUrl.contains("lulu") || decodedUrl.contains("lulustream") -> {
                val pageResponse = app.get(decodedUrl)
                val pageDoc = pageResponse.document
                val scriptData = pageDoc.select("script").map { it.data() }.joinToString("\n")
                val videoUrlMatch = Regex("""file:\s*["']([^"']+\.(?:m3u8|mp4)[^"']*)["']""").find(scriptData)
                if (videoUrlMatch != null) {
                    val videoUrl = videoUrlMatch.groupValues[1]
                    callback.invoke(
                        newExtractorLink(
                            source = "$name - $serverName",
                            name = "$name - $serverName",
                            url = videoUrl,
                            type = if (videoUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                        ) {
                            this.quality = Qualities.Unknown.value
                            this.referer = "https://lulustream.com"
                        }
                    )
                }
            }
            decodedUrl.contains("mxdrop") -> {
                val pageResponse = app.get(decodedUrl)
                val pageDoc = pageResponse.document
                val scriptData = pageDoc.select("script").map { it.data() }.joinToString("\n")
                val videoUrlMatch = Regex("""url:\s*["']([^"']+\.(?:m3u8|mp4)[^"']*)["']""").find(scriptData)
                if (videoUrlMatch != null) {
                    val videoUrl = videoUrlMatch.groupValues[1]
                    callback.invoke(
                        newExtractorLink(
                            source = "$name - $serverName",
                            name = "$name - $serverName",
                            url = videoUrl,
                            type = if (videoUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                        ) {
                            this.quality = Qualities.Unknown.value
                            this.referer = "https://mxdrop.net"
                        }
                    )
                }
            }
            decodedUrl.contains("gofile.io") -> {
                callback.invoke(
                    newExtractorLink(
                        source = "$name - $serverName",
                        name = "$name - $serverName",
                        url = decodedUrl,
                        type = ExtractorLinkType.VIDEO
                    ) {
                        this.quality = Qualities.Unknown.value
                        this.referer = "https://gofile.io"
                    }
                )
            }
            decodedUrl.contains("pixeldrain") -> {
                callback.invoke(
                    newExtractorLink(
                        source = "$name - $serverName",
                        name = "$name - $serverName",
                        url = decodedUrl,
                        type = ExtractorLinkType.VIDEO
                    ) {
                        this.quality = Qualities.Unknown.value
                        this.referer = "https://pixeldrain.com"
                    }
                )
            }
            decodedUrl.startsWith("http") -> {
                callback.invoke(
                    newExtractorLink(
                        source = "$name - $serverName",
                        name = "$name - $serverName",
                        url = decodedUrl,
                        type = if (decodedUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                    ) {
                        this.quality = Qualities.Unknown.value
                        this.referer = mainUrl
                    }
                )
            }
        }
    }

    private fun generateRandomString(length: Int = 10): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..length).map { chars.random() }.joinToString("")
    }
}
