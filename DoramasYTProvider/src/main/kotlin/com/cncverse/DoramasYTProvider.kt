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

class DoramasYTProvider : MainAPI() {
    override var mainUrl = "https://www.doramasyt.com"
    override var name = "DoramasYT"
    override val hasMainPage = true
    override var lang = "es"
    override val supportedTypes = setOf(TvType.AsianDrama, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "$mainUrl/doramas" to "Recientes",
        "$mainUrl/emision" to "En Emision",
        "$mainUrl/peliculas" to "Peliculas",
        "$mainUrl/genero/k-drama" to "K-Drama",
        "$mainUrl/genero/c-drama" to "C-Drama",
        "$mainUrl/genero/j-drama" to "J-Drama",
        "$mainUrl/genero/thai-drama" to "Thai-Drama",
        "$mainUrl/genero/romance" to "Romance",
        "$mainUrl/genero/comedia" to "Comedia",
        "$mainUrl/genero/accion" to "Accion",
        "$mainUrl/genero/drama" to "Drama",
        "$mainUrl/genero/fantasia" to "Fantasia",
    )

    private fun Element.toSearchResponse(): SearchResponse? {
        val a = this.selectFirst("a") ?: return null
        val href = fixUrlNull(a.attr("href")) ?: return null
        val title = this.selectFirst("h3")?.text()?.trim() ?: return null
        val img = this.selectFirst("img")
        val poster = fixUrlNull(img?.attr("src") ?: img?.attr("data-src") ?: "")

        return newMovieSearchResponse(
            name = title,
            url = href,
            type = TvType.AsianDrama
        ) {
            this.posterUrl = poster
        }
    }

    private fun buildUrl(baseUrl: String, page: Int): String {
        return if (page <= 1) baseUrl else "$baseUrl?p=$page"
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = buildUrl(request.data, page)
        val response = app.get(url)
        val document = response.document

        // Latest episodes section
        val latestEpisodes = document.select("ul.row li").mapNotNull { it.toSearchResponse() }
        
        // Series recientes section
        val seriesRecientes = document.select("ul.row li").mapNotNull { it.toSearchResponse() }

        return newHomePageResponse(
            listOf(
                HomePageList(request.name, latestEpisodes, isHorizontalImages = false)
            )
        )
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        val url = "$mainUrl/buscar?q=${query}"
        val response = app.get(url)
        val document = response.document

        return document.select("li").mapNotNull { it.toSearchResponse() }
    }

    override suspend fun load(url: String): LoadResponse {
        val response = app.get(url)
        val document = response.document

        val title = document.selectFirst("h1")?.text()?.trim() ?: "Unknown"
        val poster = fixUrlNull(document.selectFirst(".aspecto, img")?.attr("data-src") 
            ?: document.selectFirst(".aspecto, img")?.attr("src") ?: "")
        val backgroundPosterUrl = poster
        val plot = document.select(".sinopsis p, .description p, p:contains(sinopsis)").text().trim()
            .ifBlank { document.selectFirst("meta[name=description]")?.attr("content")?.trim() ?: "" }
        val yearText = document.selectFirst("span:contains(202), span:contains(201)")?.text()?.trim()?.takeLast(4)?.toIntOrNull()
        val tags = document.select(".genres a, .generos a, a[href*='/genero/']").map { it.text().trim() }

        val episodes = mutableListOf<Episode>()
        
        // Get episodes from AJAX endpoint
        val ajaxSection = document.selectFirst("section.caplist")
        val ajaxUrl = ajaxSection?.attr("data-ajax")
        val csrfToken = document.selectFirst("meta[name=csrf-token]")?.attr("content") ?: ""
        
        var ajaxSuccess = false
        
        if (ajaxUrl != null && ajaxUrl.isNotBlank() && csrfToken.isNotBlank()) {
            try {
                val ajaxResponse = app.post(
                    ajaxUrl,
                    data = mapOf("_token" to csrfToken),
                    headers = mapOf(
                        "Referer" to url,
                        "X-Requested-With" to "XMLHttpRequest",
                        "Accept" to "application/json"
                    )
                )
                
                val json = org.json.JSONObject(ajaxResponse.text)
                val epsArray = json.optJSONArray("eps")
                
                if (epsArray != null && epsArray.length() > 0) {
                    ajaxSuccess = true
                    for (i in 0 until epsArray.length()) {
                        val ep = epsArray.getJSONObject(i)
                        val epHref = fixUrlNull(ep.optString("url")) ?: continue
                        val epNumber = ep.optInt("episodio", i + 1)
                        val epTitle = "Episodio $epNumber"

                        episodes.add(
                            newEpisode(epHref) {
                                this.name = epTitle
                                this.episode = epNumber
                            }
                        )
                    }
                    
                    // Get additional pages
                    val paginateUrl = json.optString("paginate_url", "")
                    val perpage = json.optInt("perpage", 50)
                    val totalEps = json.optInt("total", epsArray.length())
                    
                    if (paginateUrl.isNotBlank() && totalEps > perpage) {
                        val totalPages = (totalEps + perpage - 1) / perpage
                        for (page in 2..totalPages) {
                            try {
                                val pageResponse = app.post(
                                    paginateUrl,
                                    data = mapOf("_token" to csrfToken, "p" to page.toString()),
                                    headers = mapOf(
                                        "Referer" to url,
                                        "X-Requested-With" to "XMLHttpRequest",
                                        "Accept" to "application/json"
                                    )
                                )
                                
                                val pageJson = org.json.JSONObject(pageResponse.text)
                                val capsArray = pageJson.optJSONArray("caps")
                                
                                if (capsArray != null) {
                                    for (i in 0 until capsArray.length()) {
                                        val ep = capsArray.getJSONObject(i)
                                        val epHref = fixUrlNull(ep.optString("url")) ?: continue
                                        val epNumber = ep.optInt("episodio", i + 1 + (page - 1) * perpage)
                                        val epTitle = "Episodio $epNumber"

                                        episodes.add(
                                            newEpisode(epHref) {
                                                this.name = epTitle
                                                this.episode = epNumber
                                            }
                                        )
                                    }
                                }
                            } catch (e: Exception) {
                                break
                            }
                        }
                    }
                }
            } catch (e: Exception) {}
        }
        
        // Fallback: parse links from the page
        if (!ajaxSuccess || episodes.isEmpty()) {
            document.select("a[href*='/ver/']").forEach { ep ->
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

        // Extract direct download links (Gofile, Pixeldrain, Mega) - these work without Cloudflare
        document.select("a[href*='gofile.io'], a[href*='pixeldrain.com'], a[href*='mega.nz'], a[href*='mega.co.nz']").forEach { link ->
            val href = link.attr("href").trim()
            val serverName = link.text().trim()
            
            if (href.isNotBlank()) {
                callback.invoke(
                    newExtractorLink(
                        source = "$name - $serverName",
                        name = "$name - $serverName",
                        url = href,
                        type = ExtractorLinkType.VIDEO
                    ) {
                        this.referer = mainUrl
                        this.quality = Qualities.Unknown.value
                    }
                )
            }
        }

        // Try to get player links from buttons
        document.select("button.play-video[data-player]").forEach { btn ->
            val serverName = btn.text().trim()
            val encryptedData = btn.attr("data-player")
            
            if (encryptedData.isNotBlank()) {
                try {
                    val playerKey = document.selectFirst(".player")?.attr("data-key") ?: "$mainUrl/reproductor?video="
                    val playerUrl = "${playerKey}$encryptedData&player=$serverName"
                    
                    val playerResponse = app.get(
                        playerUrl,
                        headers = mapOf(
                            "Referer" to data,
                            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                        )
                    )
                    val playerDoc = playerResponse.document
                    
                    playerDoc.select("iframe").forEach { iframe ->
                        val src = iframe.attr("src").trim()
                        if (src.isNotBlank() && src.startsWith("http")) {
                            extractVideoLink(src, serverName, callback)
                        }
                    }
                } catch (e: Exception) {
                    // Ignore player errors
                }
            }
        }

        return true
    }

    private suspend fun extractVideoLink(url: String, serverName: String, callback: (ExtractorLink) -> Unit) {
        if (url.isBlank()) return
        
        val decodedUrl = try { URLDecoder.decode(url, "UTF-8") } catch (e: Exception) { url }
        
        when {
            decodedUrl.contains("mega.nz") || decodedUrl.contains("mega.co.nz") -> {
                callback.invoke(
                    newExtractorLink(
                        source = "$name - Mega",
                        name = "$name - Mega",
                        url = decodedUrl,
                        type = ExtractorLinkType.VIDEO
                    ) {
                        this.referer = "https://mega.nz"
                        this.quality = Qualities.Unknown.value
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
                            source = "$name - Filemoon",
                            name = "$name - Filemoon",
                            url = videoUrl,
                            type = if (videoUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
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
                    ?: Regex("""sources:\s*\[\s*\{\s*src:\s*["']([^"']+)["']""").find(scriptData)
                if (videoUrlMatch != null) {
                    val videoUrl = videoUrlMatch.groupValues[1]
                    callback.invoke(
                        newExtractorLink(
                            source = "$name - Voe",
                            name = "$name - Voe",
                            url = videoUrl,
                            type = if (videoUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
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
                    val videoUrl = videoUrlMatch.groupValues[1]
                    callback.invoke(
                        newExtractorLink(
                            source = "$name - Lulu",
                            name = "$name - Lulu",
                            url = videoUrl,
                            type = if (videoUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                        ) {
                            this.referer = "https://lulustream.com"
                            this.quality = Qualities.Unknown.value
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
                            source = "$name - MxDrop",
                            name = "$name - MxDrop",
                            url = videoUrl,
                            type = if (videoUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                        ) {
                            this.referer = "https://mxdrop.net"
                            this.quality = Qualities.Unknown.value
                        }
                    )
                }
            }
            decodedUrl.contains("gofile.io") -> {
                callback.invoke(
                    newExtractorLink(
                        source = "$name - Gofile",
                        name = "$name - Gofile",
                        url = decodedUrl,
                        type = ExtractorLinkType.VIDEO
                    ) {
                        this.referer = "https://gofile.io"
                        this.quality = Qualities.Unknown.value
                    }
                )
            }
            decodedUrl.contains("pixeldrain") -> {
                callback.invoke(
                    newExtractorLink(
                        source = "$name - Pixeldrain",
                        name = "$name - Pixeldrain",
                        url = decodedUrl,
                        type = ExtractorLinkType.VIDEO
                    ) {
                        this.referer = "https://pixeldrain.com"
                        this.quality = Qualities.Unknown.value
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
                        this.referer = mainUrl
                        this.quality = Qualities.Unknown.value
                    }
                )
            }
        }
    }
}
