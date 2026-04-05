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

class PelisJuanitaProvider : MainAPI() {
    override var mainUrl = "https://pelisjuanita.com"
    override var name = "PelisJuanita"
    override val hasMainPage = true
    override var lang = "es"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "$mainUrl/movies/estrenos/" to "Estrenos",
        "$mainUrl/movies/populares/" to "Populares",
        "$mainUrl/movies/ultimas-agregadas/" to "Ultimas Agregadas",
        "$mainUrl/series/estrenos/" to "Series Estrenos",
        "$mainUrl/series/populares/" to "Series Populares",
    )

    private fun Element.toSearchResponse(): SearchResponse? {
        val a = this.selectFirst("a") ?: return null
        val href = fixUrlNull(a.attr("href")) ?: return null
        val title = this.selectFirst("h2, h3, .title, .texto-tira")?.text()?.trim() ?: return null
        val img = this.selectFirst("img")
        val poster = fixUrlNull(img?.attr("data-src") ?: img?.attr("src") ?: "")

        val type = if (href.contains("/series/")) TvType.TvSeries else TvType.Movie

        return newMovieSearchResponse(
            name = title,
            url = href,
            type = type
        ) {
            this.posterUrl = poster
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page > 1) "${request.data}page/$page/" else request.data
        val response = app.get(url)
        val homeList = response.document.select(".item-tira, .grid-item, article, .card").mapNotNull { it.toSearchResponse() }

        return newHomePageResponse(
            listOf(
                HomePageList(request.name, homeList, isHorizontalImages = false)
            )
        )
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        val url = "$mainUrl/movies/?s=${query}"
        val response = app.get(url)
        return response.document.select(".grid-item, article, .card").mapNotNull { it.toSearchResponse() }
    }

    override suspend fun load(url: String): LoadResponse {
        val response = app.get(url)
        val document = response.document

        val title = document.selectFirst("h1")?.text()?.trim() ?: "Unknown"
        val poster = fixUrlNull(document.selectFirst("img")?.attr("data-src") ?: document.selectFirst("img")?.attr("src") ?: "")
        val backgroundPosterUrl = poster
        val plot = document.select(".description, .sinopsis, p").text().trim()
        val yearText = Regex("""(\d{4})""").find(document.text())?.groupValues?.get(1)?.toIntOrNull()
        val tags = document.select("a[href*='/genero/'], a[href*='/categoria/']").map { it.text().trim() }

        val episodes = mutableListOf<Episode>()
        
        // Check if it's a series
        if (url.contains("/series/")) {
            document.select("a[href*='/episodio/'], a[href*='/temporada/']").forEach { ep ->
                val epHref = fixUrlNull(ep.attr("href")) ?: return@forEach
                val epText = ep.text().trim()
                val epTitle = epText.ifBlank { epHref.substringAfterLast("/").replace("-", " ") }
                val epNumber = Regex("""(\d+)""").find(epText)?.groupValues?.get(1)?.toIntOrNull() ?: 1

                episodes.add(
                    newEpisode(epHref) {
                        this.name = epTitle
                        this.episode = epNumber
                    }
                )
            }
        }

        val type = if (episodes.isNotEmpty()) TvType.TvSeries else TvType.Movie

        return if (type == TvType.TvSeries) {
            newTvSeriesLoadResponse(title, url, type, episodes) {
                this.posterUrl = poster
                this.backgroundPosterUrl = backgroundPosterUrl
                this.plot = plot
                this.year = yearText
                this.tags = tags
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.backgroundPosterUrl = backgroundPosterUrl
                this.plot = plot
                this.year = yearText
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
