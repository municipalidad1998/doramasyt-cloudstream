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

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = request.data + page
        val response = app.get(url)
        val document = response.document

        val homeList = document.select(".item").mapNotNull { element ->
            val titleElement = element.selectFirst("h3, a")
            val title = titleElement?.text()?.trim() ?: return@mapNotNull null
            val href = fixUrlNull(element.selectFirst("a")?.attr("href") ?: return@mapNotNull null) ?: return@mapNotNull null
            val poster = fixUrlNull(element.selectFirst("img")?.attr("src") ?: element.selectFirst("img")?.attr("data-src") ?: "")

            newMovieSearchResponse(
                name = title,
                url = href,
                type = TvType.AsianDrama
            ) {
                this.posterUrl = poster
            }
        }

        return newHomePageResponse(
            listOf(
                HomePageList(request.name, homeList, isHorizontalImages = false)
            )
        )
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        val url = "$mainUrl/doramas?q=${query}"
        val response = app.get(url)
        val document = response.document

        val results = document.select(".item").mapNotNull { element ->
            val titleElement = element.selectFirst("h3, a")
            val title = titleElement?.text()?.trim() ?: return@mapNotNull null
            val href = fixUrlNull(element.selectFirst("a")?.attr("href") ?: return@mapNotNull null) ?: return@mapNotNull null
            val poster = fixUrlNull(element.selectFirst("img")?.attr("src") ?: element.selectFirst("img")?.attr("data-src") ?: "")

            newMovieSearchResponse(
                name = title,
                url = href,
                type = TvType.AsianDrama
            ) {
                this.posterUrl = poster
            }
        }

        return results
    }

    override suspend fun load(url: String): LoadResponse {
        val response = app.get(url)
        val document = response.document

        val title = document.selectFirst("h1")?.text()?.trim() ?: "Unknown"
        val poster = fixUrlNull(document.selectFirst(".img-drama img")?.attr("src") ?: "")
        val backgroundPosterUrl = fixUrlNull(document.selectFirst(".background")?.attr("src") ?: document.selectFirst(".img-drama img")?.attr("src") ?: "")
        val plot = document.select(".sinopsis p, .description p, .text-center p").text().trim()
        val yearText = document.select(".date, .fecha, span:contains(202), span:contains(201)").firstOrNull()?.text()?.trim()?.takeLast(4)?.toIntOrNull()
        val tags = document.select(".genres a, .generos a").map { it.text().trim() }

        val episodes = mutableListOf<Episode>()
        
        val episodeElements = document.select(".episode-list .episode, .capitulos a, .list-episodes a, .episodes a")
        if (episodeElements.isNotEmpty()) {
            episodeElements.forEach { ep ->
                val epHref = fixUrlNull(ep.attr("href") ?: "") ?: return@forEach
                val epTitle = ep.text().trim()
                val epNumber = Regex("""[Ee]pisodio\s*(\d+)|[Cc]apitulo\s*(\d+)|Ep\s*(\d+)""").find(epTitle)?.destructured?.let { (a, b, c) ->
                    (a + b + c).toIntOrNull()
                } ?: 1
                
                episodes.add(
                    newEpisode(epHref) {
                        this.name = epTitle
                        this.episode = epNumber
                    }
                )
            }
        } else {
            val firstEpLink = document.selectFirst("a:contains(Ver Ahora), a:contains(Ver ahora), a:contains(Ver Episodio)")
            if (firstEpLink != null) {
                val epHref = fixUrlNull(firstEpLink.attr("href") ?: "") ?: ""
                if (epHref.isNotBlank()) {
                    episodes.add(
                        newEpisode(epHref) {
                            this.name = "Episodio 1"
                            this.episode = 1
                        }
                    )
                }
            }
        }

        val type = if (episodes.size > 1 || document.select(".type:contains(Dorama), .tipo:contains(Dorama)").isNotEmpty()) {
            TvType.TvSeries
        } else {
            TvType.AsianDrama
        }

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

        val servers = document.select(".server-list .server, .servers li, .player-options li, .options li")
        
        if (servers.isNotEmpty()) {
            servers.forEach { server ->
                val serverName = server.selectFirst(".name, .server-name, span")?.text()?.trim() ?: ""
                val linkElement = server.selectFirst("a, button")
                val linkUrl = linkElement?.attr("href") ?: linkElement?.attr("data-link") ?: linkElement?.attr("data-url") ?: ""
                
                if (linkUrl.isNotBlank()) {
                    extractVideoLink(linkUrl, serverName, callback)
                }
            }
        }

        val iframe = document.selectFirst("iframe")
        if (iframe != null) {
            val iframeSrc = iframe.attr("src")
            if (iframeSrc.isNotBlank()) {
                extractVideoLink(iframeSrc, "Default", callback)
            }
        }

        val playerScript = document.selectFirst("script:containsData(player), script:containsData(video), script:containsData(embed)")
        if (playerScript != null) {
            val scriptData = playerScript.data()
            val urlMatch = Regex("""["'](https?://[^"']+\.(?:mp4|m3u8|mkv|avi)[^"']*)["']""").find(scriptData)
            if (urlMatch != null) {
                val videoUrl = urlMatch.groupValues[1]
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
