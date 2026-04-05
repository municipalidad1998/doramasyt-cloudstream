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

class PeliCineHDProvider : MainAPI() {
    override var mainUrl = "https://pelicinehd.com"
    override var name = "PeliCineHD"
    override val hasMainPage = true
    override var lang = "es"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "$mainUrl/peliculas/" to "Peliculas",
        "$mainUrl/series/" to "Series",
        "$mainUrl/release/2024/" to "Estrenos 2024",
        "$mainUrl/release/2025/" to "Estrenos 2025",
        "$mainUrl/category/accion/" to "Accion",
        "$mainUrl/category/comedia/" to "Comedia",
        "$mainUrl/category/drama/" to "Drama",
        "$mainUrl/category/fantasia/" to "Fantasia",
    )

    private fun Element.toSearchResponse(): SearchResponse? {
        val a = this.selectFirst("a[href]") ?: return null
        val href = fixUrlNull(a.attr("href")) ?: return null
        val fullHref = if (href.startsWith("http")) href else "$mainUrl$href"
        
        val title = this.selectFirst("h2, h3, .entry-title, .title, .post-title, .movie-title")?.text()?.trim()
            ?: a.attr("title")
            ?: return null
        
        val img = this.selectFirst("img")
        val poster = fixUrlNull(img?.attr("data-src") ?: img?.attr("src") ?: img?.attr("data-lazy-src") ?: "")

        val type = if (fullHref.contains("/series/")) TvType.TvSeries else TvType.Movie

        return newMovieSearchResponse(
            name = title,
            url = fullHref,
            type = type
        ) {
            this.posterUrl = poster
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page > 1) "${request.data}page/$page/" else request.data
        val response = app.get(url)
        val document = response.document

        val homeList = document.select("article, .post, .item, .movie-item, .grid-item, .post-lst li").mapNotNull { it.toSearchResponse() }

        return newHomePageResponse(
            listOf(
                HomePageList(request.name, homeList, isHorizontalImages = false)
            )
        )
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        val url = "$mainUrl?s=${query}"
        val response = app.get(url)
        val document = response.document
        return document.select("article, .post, .item, .movie-item").mapNotNull { it.toSearchResponse() }
    }

    override suspend fun load(url: String): LoadResponse {
        val response = app.get(url)
        val document = response.document

        val title = document.selectFirst("h1")?.text()?.trim() ?: "Unknown"
        
        val img = document.selectFirst("img")
        val poster = fixUrlNull(img?.attr("data-src") ?: img?.attr("src") ?: img?.attr("data-lazy-src") ?: "")
        val backgroundPosterUrl = poster
        
        val plot = document.select(".entry-content p, .description, .sinopsis, .resumen").text().trim()
            .ifBlank { document.selectFirst("meta[name=description]")?.attr("content")?.trim() ?: "" }
        
        val tags = document.select("a[href*='/category/'], a[href*='/genero/']").map { it.text().trim() }

        val episodes = mutableListOf<Episode>()
        
        document.select("a[href*='/episodio/'], a[href*='/capitulo/'], a[href*='/temporada/']").forEach { ep ->
            val epHref = fixUrlNull(ep.attr("href")) ?: return@forEach
            val fullEpHref = if (epHref.startsWith("http")) epHref else "$mainUrl$epHref"
            val epText = ep.text().trim()
            val epTitle = epText.ifBlank { fullEpHref.substringAfterLast("/").replace("-", " ").replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } }
            val epNumber = Regex("""(\d+)""").find(epText)?.groupValues?.get(1)?.toIntOrNull() ?: 1

            episodes.add(
                newEpisode(fullEpHref) {
                    this.name = epTitle
                    this.episode = epNumber
                }
            )
        }

        val type = if (episodes.isNotEmpty()) TvType.TvSeries else TvType.Movie

        return if (type == TvType.TvSeries) {
            newTvSeriesLoadResponse(title, url, type, episodes) {
                this.posterUrl = poster
                this.backgroundPosterUrl = backgroundPosterUrl
                this.plot = plot
                this.tags = tags
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.backgroundPosterUrl = backgroundPosterUrl
                this.plot = plot
                this.tags = tags
            }
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
            val src = iframe.attr("src").trim()
            if (src.isNotBlank() && src.startsWith("http")) {
                extractVideoLink(src, "Server", callback)
            }
        }

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
