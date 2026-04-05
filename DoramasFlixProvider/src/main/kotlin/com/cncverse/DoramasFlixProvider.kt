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
import org.jsoup.nodes.Element
import java.net.URLDecoder

class DoramasFlixProvider : MainAPI() {
    override var mainUrl = "https://doramasflix.io"
    override var name = "DoramasFlix"
    override val hasMainPage = true
    override var lang = "es"
    override val supportedTypes = setOf(TvType.AsianDrama, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "$mainUrl/doramas" to "Doramas",
        "$mainUrl/peliculas" to "Peliculas",
        "$mainUrl/variedades" to "Variedades",
    )

    private fun Element.toSearchResponse(): SearchResponse? {
        val a = this.selectFirst("a") ?: return null
        val href = fixUrlNull(a.attr("href")) ?: return null
        val title = this.selectFirst("h3, p, span")?.text()?.trim() ?: return null
        val img = this.selectFirst("img")
        val poster = fixUrlNull(img?.attr("data-src") ?: img?.attr("src") ?: "")

        return newMovieSearchResponse(
            name = title,
            url = href,
            type = TvType.AsianDrama
        ) {
            this.posterUrl = poster
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = request.data
        val response = app.get(url)
        val homeList = response.document.select("article, .item, li").mapNotNull { it.toSearchResponse() }

        return newHomePageResponse(
            listOf(
                HomePageList(request.name, homeList, isHorizontalImages = false)
            )
        )
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        val url = "$mainUrl?q=${query}"
        val response = app.get(url)
        return response.document.select("article, .item, li").mapNotNull { it.toSearchResponse() }
    }

    override suspend fun load(url: String): LoadResponse {
        val response = app.get(url)
        val document = response.document

        val title = document.selectFirst("h1")?.text()?.trim() ?: "Unknown"
        val poster = fixUrlNull(document.selectFirst("img")?.attr("data-src") ?: document.selectFirst("img")?.attr("src") ?: "")
        val backgroundPosterUrl = poster
        val plot = document.select("p, .description, .sinopsis").text().trim()
        val tags = document.select("a[href*='/generos/'], a[href*='/etiquetas/']").map { it.text().trim() }

        val episodes = mutableListOf<Episode>()
        
        document.select("a[href*='/ver/'], a[href*='/capitulo/'], a[href*='/episodio/']").forEach { ep ->
            val epHref = fixUrlNull(ep.attr("href")) ?: return@forEach
            val epText = ep.text().trim()
            val epTitle = epText.ifBlank { epHref.substringAfterLast("/").replace("-", " ").replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } }
            val epNumber = Regex("""(\d+)""").find(epText)?.groupValues?.get(1)?.toIntOrNull() ?: 1

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

        // Look for iframes
        document.select("iframe").forEach { iframe ->
            val src = iframe.attr("src").trim()
            if (src.isNotBlank() && src.startsWith("http")) {
                extractVideoLink(src, "Server", callback)
            }
        }

        // Look for video URLs in scripts
        document.select("script").forEach { script ->
            val scriptData = script.data()
            Regex("""["'](https?://[^"']+\.(?:mp4|m3u8|mkv)[^"']*)["']""").findAll(scriptData).forEach { match ->
                val videoUrl = match.groupValues[1]
                callback.invoke(
                    newExtractorLink(
                        source = name,
                        name = name,
                        url = videoUrl,
                        type = if (videoUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                    ) {
                        this.referer = mainUrl
                        this.quality = Qualities.Unknown.value
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
            decodedUrl.contains("filemoon") || decodedUrl.contains("filemoon.sx") -> {
                val pageResponse = app.get(decodedUrl)
                val pageDoc = pageResponse.document
                val scriptData = pageDoc.select("script").map { it.data() }.joinToString("\n")
                val videoUrlMatch = Regex("""file:\s*["']([^"']+\.(?:m3u8|mp4)[^"']*)["']""").find(scriptData)
                if (videoUrlMatch != null) {
                    callback.invoke(
                        newExtractorLink(
                            source = "$name - Filemoon",
                            name = "$name - Filemoon",
                            url = videoUrlMatch.groupValues[1],
                            type = if (videoUrlMatch.groupValues[1].contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                        ) {
                            this.referer = "https://filemoon.sx"
                            this.quality = Qualities.Unknown.value
                        }
                    )
                }
            }
            decodedUrl.contains("voe") || decodedUrl.contains("voe.sx") -> {
                val pageResponse = app.get(decodedUrl)
                val pageDoc = pageResponse.document
                val scriptData = pageDoc.select("script").map { it.data() }.joinToString("\n")
                val videoUrlMatch = Regex("""hls":\s*["']([^"']+)["']""").find(scriptData)
                if (videoUrlMatch != null) {
                    callback.invoke(
                        newExtractorLink(
                            source = "$name - Voe",
                            name = "$name - Voe",
                            url = videoUrlMatch.groupValues[1],
                            type = if (videoUrlMatch.groupValues[1].contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                        ) {
                            this.referer = "https://voe.sx"
                            this.quality = Qualities.Unknown.value
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
                    callback.invoke(
                        newExtractorLink(
                            source = "$name - Lulu",
                            name = "$name - Lulu",
                            url = videoUrlMatch.groupValues[1],
                            type = if (videoUrlMatch.groupValues[1].contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                        ) {
                            this.referer = "https://lulustream.com"
                            this.quality = Qualities.Unknown.value
                        }
                    )
                }
            }
            decodedUrl.startsWith("http") -> {
                callback.invoke(
                    newExtractorLink(
                        source = "$name - $serverName",
                        name = "$name - $serverName",
                        url = decodedUrl,
                        type = if (decodedUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                    ) {
                        this.referer = mainUrl
                        this.quality = Qualities.Unknown.value
                    }
                )
            }
        }
    }
}
