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
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject
import java.util.Base64

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
        val title = this.selectFirst("h3.title_cap")?.text()?.trim()
            ?: this.selectFirst("h3")?.text()?.trim()
            ?: return null
        val img = this.selectFirst("img.lazy, img[data-src], img")
        val poster = fixUrlNull(img?.attr("data-src") ?: img?.attr("src") ?: "")

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
        val homeList = response.document.select("li.ficha_efecto").mapNotNull { it.toSearchResponse() }

        return newHomePageResponse(
            listOf(
                HomePageList(request.name, homeList, isHorizontalImages = false)
            )
        )
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        // Try AJAX search first (like the site does)
        try {
            val csrfToken = app.get(mainUrl).document.selectFirst("meta[name=csrf-token]")?.attr("content") ?: ""
            val ajaxResponse = app.post(
                "$mainUrl/buscar_ajax",
                data = mapOf("_token" to csrfToken, "q" to query),
                headers = mapOf(
                    "Referer" to mainUrl,
                    "X-Requested-With" to "XMLHttpRequest"
                )
            )
            
            // The AJAX response is JSON with search results
            val json = org.json.JSONArray(ajaxResponse.text)
            val results = mutableListOf<SearchResponse>()
            
            for (i in 0 until json.length()) {
                val item = json.getJSONObject(i)
                val title = item.getString("nombre")
                val href = item.getString("url")
                val poster = item.getString("imagen")
                
                results.add(
                    newMovieSearchResponse(
                        name = title,
                        url = fixUrl(href),
                        type = TvType.AsianDrama
                    ) {
                        this.posterUrl = fixUrl(poster)
                    }
                )
            }
            
            if (results.isNotEmpty()) return results
        } catch (e: Exception) {
            // Fallback to regular search
        }
        
        // Fallback: regular search
        val url = "$mainUrl/buscar?q=${query}"
        val response = app.get(url, headers = mapOf("Referer" to mainUrl))
        return response.document.select("li.ficha_efecto").mapNotNull { it.toSearchResponse() }
    }

    override suspend fun load(url: String): LoadResponse {
        val response = app.get(url)
        val document = response.document

        val title = document.selectFirst("h1")?.text()?.trim() ?: "Unknown"
        
        val imgElement = document.selectFirst("img.lazy, img[data-src], .background, .card-img-top, img")
        val poster = fixUrlNull(imgElement?.attr("data-src") ?: imgElement?.attr("src") ?: "")
        val backgroundPosterUrl = poster
        
        val plot = document.selectFirst(".sinopsis, .description, .card-body p, p.text-muted")?.text()?.trim() ?: ""
        
        val yearText = document.selectFirst("span.text-muted, .badge")?.text()?.trim()
            ?.let { Regex("""(\d{4})""").find(it)?.groupValues?.get(1)?.toIntOrNull() }
        
        val tags = document.select("a[href*='/genero/']").map { it.text().trim() }

        val episodes = mutableListOf<Episode>()
        
        // Get episodes from AJAX endpoint
        val ajaxSection = document.selectFirst("section.caplist")
        val ajaxUrl = ajaxSection?.attr("data-ajax")
        
        if (ajaxUrl != null && ajaxUrl.isNotBlank()) {
            try {
                val ajaxResponse = app.post(ajaxUrl, headers = mapOf(
                    "Referer" to url,
                    "X-Requested-With" to "XMLHttpRequest"
                ))
                val ajaxDoc = ajaxResponse.document
                
                ajaxDoc.select("a[href*='/ver/'], li a[href*='/ver/']").forEach { ep ->
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
            } catch (e: Exception) {
                // Fallback below
            }
        }
        
        // Fallback: parse links from the page
        if (episodes.isEmpty()) {
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

        // Get the player key from the page
        val playerKey = document.selectFirst(".player")?.attr("data-key") ?: "$mainUrl/reproductor?video="
        
        // Extract encrypted player data from buttons
        document.select("button.play-video[data-player]").forEach { btn ->
            val serverName = btn.text().trim().lowercase()
            val encryptedData = btn.attr("data-player")
            
            if (encryptedData.isNotBlank()) {
                // Build the reproductor URL with encrypted data
                val videoUrl = "${playerKey}$encryptedData&player=$serverName"
                extractFromReproductor(videoUrl, serverName, callback)
            }
        }

        // Also check for iframes directly
        document.select("iframe").forEach { iframe ->
            val src = iframe.attr("src").trim()
            if (src.isNotBlank() && src.startsWith("http")) {
                extractVideoLink(src, "Server", callback)
            }
        }

        return true
    }

    private suspend fun extractFromReproductor(url: String, serverName: String, callback: (ExtractorLink) -> Unit) {
        try {
            val response = app.get(url, headers = mapOf("Referer" to mainUrl))
            val document = response.document
            
            // Look for iframe in the reproductor page
            document.select("iframe").forEach { iframe ->
                val src = iframe.attr("src").trim()
                if (src.isNotBlank() && src.startsWith("http")) {
                    extractVideoLink(src, serverName, callback)
                }
            }
            
            // Look for video URLs in scripts
            document.select("script").forEach { script ->
                val scriptData = script.data()
                Regex("""["'](https?://[^"']+\.(?:mp4|m3u8|mkv)[^"']*)["']""").findAll(scriptData).forEach { match ->
                    val videoUrl = match.groupValues[1]
                    callback.invoke(
                        newExtractorLink(
                            source = "$name - $serverName",
                            name = "$name - $serverName",
                            url = videoUrl,
                            type = if (videoUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                        ) {
                            this.quality = Qualities.Unknown.value
                            this.referer = mainUrl
                        }
                    )
                }
            }
        } catch (e: Exception) {
            // Ignore errors
        }
    }

    private suspend fun extractVideoLink(url: String, serverName: String, callback: (ExtractorLink) -> Unit) {
        if (url.isBlank()) return
        
        val decodedUrl = try { URLDecoder.decode(url, "UTF-8") } catch (e: Exception) { url }
        
        when {
            decodedUrl.contains("mega.nz") -> {
                callback.invoke(
                    newExtractorLink(
                        source = "$name - Mega",
                        name = "$name - Mega",
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
                            source = "$name - Filemoon",
                            name = "$name - Filemoon",
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
                                source = "$name - Doodstream",
                                name = "$name - Doodstream",
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
                            source = "$name - Streamtape",
                            name = "$name - Streamtape",
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
                            source = "$name - Voe",
                            name = "$name - Voe",
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
                            source = "$name - Lulu",
                            name = "$name - Lulu",
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
                            source = "$name - MxDrop",
                            name = "$name - MxDrop",
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
                        source = "$name - Gofile",
                        name = "$name - Gofile",
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
                        source = "$name - Pixeldrain",
                        name = "$name - Pixeldrain",
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
