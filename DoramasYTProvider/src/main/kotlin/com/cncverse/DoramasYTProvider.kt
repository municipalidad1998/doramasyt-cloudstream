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

    // Placeholders que hay que ignorar
    private val placeholderImages = listOf("anime.png", "capblank.png", "capblank2.png")

    override val mainPage = mainPageOf(
        "$mainUrl/doramas"      to "Recientes",
        "$mainUrl/emision"      to "En Emision",
        "$mainUrl/peliculas"    to "Peliculas",
        "$mainUrl/genero/k-drama"    to "K-Drama",
        "$mainUrl/genero/c-drama"    to "C-Drama",
        "$mainUrl/genero/j-drama"    to "J-Drama",
        "$mainUrl/genero/thai-drama" to "Thai-Drama",
        "$mainUrl/genero/romance"    to "Romance",
        "$mainUrl/genero/comedia"    to "Comedia",
        "$mainUrl/genero/accion"     to "Accion",
        "$mainUrl/genero/drama"      to "Drama",
        "$mainUrl/genero/fantasia"   to "Fantasia",
    )

    // ---------------------------------------------------------------
    // HELPERS DE IMAGEN
    // ---------------------------------------------------------------

    /** Devuelve true si la URL es una imagen placeholder del sitio */
    private fun String.isPlaceholder(): Boolean =
        placeholderImages.any { this.contains(it) }

    /**
     * Extrae la URL real de la imagen probando varios atributos data-*
     * y filtrando los placeholders.
     */
    private fun Element.realImageUrl(): String? {
        val img = this.selectFirst("img.lazy, img[data-src], img[data-img], img[data-original], img") ?: return null
        val candidates = listOf(
            img.attr("data-src"),
            img.attr("data-img"),
            img.attr("data-original"),
            img.attr("data-lazy-src"),
            img.attr("src")
        )
        return candidates.firstOrNull { it.isNotBlank() && !it.isPlaceholder() }
    }

    // ---------------------------------------------------------------
    // toSearchResponse
    // ---------------------------------------------------------------

    private fun Element.toSearchResponse(): SearchResponse? {
        val a = this.selectFirst("a") ?: return null
        val href = fixUrlNull(a.attr("href")) ?: return null
        val title = this.selectFirst("h3.titulo_cap, h3.title_cap, h3")
            ?.text()?.trim() ?: return null

        val poster = fixUrlNull(this.realImageUrl() ?: "")

        return newMovieSearchResponse(
            name  = title,
            url   = href,
            type  = TvType.AsianDrama
        ) {
            this.posterUrl = poster
        }
    }

    // ---------------------------------------------------------------
    // buildUrl
    // ---------------------------------------------------------------

    private fun buildUrl(baseUrl: String, page: Int): String =
        if (page <= 1) baseUrl else "$baseUrl?p=$page"

    // ---------------------------------------------------------------
    // getMainPage
    // ---------------------------------------------------------------

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = buildUrl(request.data, page)
        val response = app.get(url)
        val homeList = response.document
            .select("li.ficha_efecto, div.ficha_efecto, .col-6, .anime_programing")
            .mapNotNull { it.toSearchResponse() }

        return newHomePageResponse(
            listOf(HomePageList(request.name, homeList, isHorizontalImages = false))
        )
    }

    // ---------------------------------------------------------------
    // search
    // ---------------------------------------------------------------

    override suspend fun search(query: String): List<SearchResponse>? {
        // 1) Intentar AJAX (devuelve imagen real en el JSON)
        try {
            val mainResponse = app.get(mainUrl)
            val csrfToken = mainResponse.document
                .selectFirst("meta[name=csrf-token]")?.attr("content") ?: ""

            val ajaxResponse = app.post(
                "$mainUrl/buscar_ajax",
                data    = mapOf("_token" to csrfToken, "q" to query),
                headers = mapOf(
                    "Referer"           to mainUrl,
                    "X-Requested-With"  to "XMLHttpRequest"
                )
            )

            val json    = org.json.JSONArray(ajaxResponse.text)
            val results = mutableListOf<SearchResponse>()

            for (i in 0 until json.length()) {
                val item  = json.getJSONObject(i)
                val title = item.getString("nombre")
                val href  = item.getString("url")
                // El AJAX ya devuelve la URL real de la imagen
                val imagen = item.optString("imagen", "").takeIf { !it.isPlaceholder() }

                results.add(
                    newMovieSearchResponse(
                        name = title,
                        url  = fixUrl(href),
                        type = TvType.AsianDrama
                    ) {
                        this.posterUrl = imagen?.let { fixUrl(it) }
                    }
                )
            }
            if (results.isNotEmpty()) return results
        } catch (e: Exception) { /* fallback al HTML */ }

        // 2) Fallback: búsqueda HTML
        val url      = "$mainUrl/buscar?q=${query}"
        val response = app.get(url, headers = mapOf("Referer" to mainUrl))
        return response.document
            .select("li.ficha_efecto, div.ficha_efecto")
            .mapNotNull { it.toSearchResponse() }
    }

    // ---------------------------------------------------------------
    // load  (página de detalle del dorama)
    // ---------------------------------------------------------------

    override suspend fun load(url: String): LoadResponse {
        val response = app.get(url)
        val document = response.document

        val title = document.selectFirst("h1")?.text()?.trim() ?: "Unknown"

        // --- POSTER: prioridad OG > data-src > src (sin placeholders) ---
        val ogImage = document.selectFirst("meta[property='og:image']")?.attr("content")
            ?.takeIf { it.isNotBlank() && !it.isPlaceholder() }

        val imgElement = document.selectFirst(
            "img.lazy, img[data-src], img[data-img], img[data-original], .card-img-top, img"
        )
        val rawPoster = ogImage
            ?: imgElement?.attr("data-src")?.takeIf { it.isNotBlank() && !it.isPlaceholder() }
            ?: imgElement?.attr("data-img")?.takeIf { it.isNotBlank() && !it.isPlaceholder() }
            ?: imgElement?.attr("data-original")?.takeIf { it.isNotBlank() && !it.isPlaceholder() }
            ?: imgElement?.attr("src")?.takeIf { it.isNotBlank() && !it.isPlaceholder() }

        val poster = fixUrlNull(rawPoster ?: "")

        // --- BACKGROUND: preferir og:image o twitter:image ---
        val bgPoster = document.selectFirst("meta[property='og:image'], meta[name='twitter:image']")
            ?.attr("content")?.takeIf { !it.isPlaceholder() }
            ?.let { fixUrl(it) } ?: poster

        val plot = document.selectFirst(
            ".sinopsis, .description, .card-body p, p.text-muted"
        )?.text()?.trim() ?: ""

        val yearText = document.selectFirst("span.text-muted, .badge")?.text()?.trim()
            ?.let { Regex("""(\d{4})""").find(it)?.groupValues?.get(1)?.toIntOrNull() }

        val tags = document.select("a[href*='/genero/']").map { it.text().trim() }

        // ---------------------------------------------------------------
        // EPISODIOS
        // ---------------------------------------------------------------
        val episodes   = mutableListOf<Episode>()
        val ajaxSection = document.selectFirst("section.caplist")
        val ajaxUrl     = ajaxSection?.attr("data-ajax")
        val csrfToken   = document.selectFirst("meta[name=csrf-token]")?.attr("content") ?: ""
        var ajaxSuccess = false

        if (ajaxUrl != null && ajaxUrl.isNotBlank() && csrfToken.isNotBlank()) {
            try {
                val ajaxResponse = app.post(
                    ajaxUrl,
                    data    = mapOf("_token" to csrfToken),
                    headers = mapOf(
                        "Referer"           to url,
                        "X-Requested-With"  to "XMLHttpRequest",
                        "Accept"            to "application/json"
                    )
                )
                val json     = org.json.JSONObject(ajaxResponse.text)
                val epsArray = json.optJSONArray("eps")

                if (epsArray != null && epsArray.length() > 0) {
                    ajaxSuccess = true
                    for (i in 0 until epsArray.length()) {
                        val ep      = epsArray.getJSONObject(i)
                        val epHref  = fixUrlNull(ep.optString("url")) ?: continue
                        val epNumber = ep.optInt("episodio", i + 1)
                        episodes.add(newEpisode(epHref) {
                            this.name    = "Episodio $epNumber"
                            this.episode = epNumber
                        })
                    }

                    val paginateUrl = json.optString("paginate_url", "")
                    val perpage     = json.optInt("perpage", 50)
                    val totalEps    = json.optInt("total", epsArray.length())

                    if (paginateUrl.isNotBlank() && totalEps > perpage) {
                        val totalPages = (totalEps + perpage - 1) / perpage
                        for (p in 2..totalPages) {
                            try {
                                val pageResponse = app.post(
                                    paginateUrl,
                                    data    = mapOf("_token" to csrfToken, "p" to p.toString()),
                                    headers = mapOf(
                                        "Referer"           to url,
                                        "X-Requested-With"  to "XMLHttpRequest",
                                        "Accept"            to "application/json"
                                    )
                                )
                                val pageJson  = org.json.JSONObject(pageResponse.text)
                                val capsArray = pageJson.optJSONArray("caps")
                                if (capsArray != null) {
                                    for (i in 0 until capsArray.length()) {
                                        val ep      = capsArray.getJSONObject(i)
                                        val epHref  = fixUrlNull(ep.optString("url")) ?: continue
                                        val epNumber = ep.optInt("episodio", i + 1 + (p - 1) * perpage)
                                        episodes.add(newEpisode(epHref) {
                                            this.name    = "Episodio $epNumber"
                                            this.episode = epNumber
                                        })
                                    }
                                }
                            } catch (e: Exception) { break }
                        }
                    }
                }
            } catch (e: Exception) { }
        }

        // Fallback: seguir links de episodios
        if (!ajaxSuccess || episodes.isEmpty()) {
            val verAhoraLink = document.selectFirst("a[href*='/ver/']")
            if (verAhoraLink != null) {
                var currentEpUrl  = fixUrlNull(verAhoraLink.attr("href"))
                var epNumber      = 1
                val visitedUrls   = mutableSetOf<String>()

                while (currentEpUrl != null && epNumber <= 200 && !visitedUrls.contains(currentEpUrl)) {
                    visitedUrls.add(currentEpUrl)
                    try {
                        val epDoc = app.get(currentEpUrl).document
                        episodes.add(newEpisode(currentEpUrl) {
                            this.name    = "Episodio $epNumber"
                            this.episode = epNumber
                        })
                        val siguiente = epDoc.select("a[href*='/ver/']")
                            .firstOrNull { it.text().contains("Siguiente", ignoreCase = true) }
                        val nextUrl = siguiente?.let { fixUrlNull(it.attr("href")) }
                        currentEpUrl = if (nextUrl != null && nextUrl != currentEpUrl) {
                            epNumber++; nextUrl
                        } else null
                    } catch (e: Exception) { break }
                }
            }
        }

        val type = if (episodes.size > 1) TvType.TvSeries else TvType.AsianDrama

        return newTvSeriesLoadResponse(title, url, type, episodes) {
            this.posterUrl           = poster
            this.backgroundPosterUrl = bgPoster
            this.plot                = plot
            this.year                = yearText
            this.tags                = tags
        }
    }

    // ---------------------------------------------------------------
    // loadLinks
    // ---------------------------------------------------------------

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        app.get(mainUrl)  // Establecer sesión
        val response  = app.get(data)
        val document  = response.document

        var resourceToken = ""
        document.select("script").forEach { script ->
            val scriptData = script.data()
            val match = Regex("""resource_token\s*=\s*['"]([^'"]+)['"]""").find(scriptData)
            if (match != null) resourceToken = match.groupValues[1]
        }

        val playerKey = document.selectFirst(".player")?.attr("data-key")
            ?: "$mainUrl/reproductor?video="

        document.select("button.play-video[data-player]").forEach { btn ->
            val serverName    = btn.text().trim()
            val encryptedData = btn.attr("data-player")
            if (encryptedData.isNotBlank()) {
                val playerUrl = "${playerKey}${encryptedData}" +
                    "&player=${java.net.URLEncoder.encode(serverName, "UTF-8")}" +
                    "&token=$resourceToken"
                extractFromReproductor(playerUrl, serverName, callback)
            }
        }

        // Links directos (Gofile, Pixeldrain, Mega)
        document.select(
            "a.btn[href*='gofile.io'], a.btn[href*='pixeldrain.com'], " +
            "a.btn[href*='mega.nz'], a.btn[href*='mega.co.nz']"
        ).forEach { link ->
            val href       = link.attr("href").trim()
            val serverName = link.text().trim()
            if (href.isNotBlank()) {
                callback.invoke(newExtractorLink(
                    source = "$name - $serverName",
                    name   = "$name - $serverName",
                    url    = href,
                    type   = ExtractorLinkType.VIDEO
                ) {
                    this.referer = mainUrl
                    this.quality = Qualities.Unknown.value
                })
            }
        }
        return true
    }

    // ---------------------------------------------------------------
    // extractFromReproductor
    // ---------------------------------------------------------------

    private suspend fun extractFromReproductor(
        url: String,
        serverName: String,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val document = app.get(url, headers = mapOf(
                "Referer"    to mainUrl,
                "Accept"     to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            )).document

            document.select("iframe").forEach { iframe ->
                val src = iframe.attr("src").trim()
                if (src.isNotBlank() && src.startsWith("http"))
                    extractVideoLink(src, serverName, callback)
            }

            document.select("script").forEach { script ->
                val scriptData = script.data()
                Regex("""["'](https?://[^"']+\.(?:mp4|m3u8|mkv)[^"']*)["']""")
                    .findAll(scriptData).forEach { match ->
                        val videoUrl = match.groupValues[1]
                        callback.invoke(newExtractorLink(
                            source = "$name - $serverName",
                            name   = "$name - $serverName",
                            url    = videoUrl,
                            type   = if (videoUrl.contains(".m3u8")) ExtractorLinkType.M3U8
                                     else ExtractorLinkType.VIDEO
                        ) {
                            this.referer = mainUrl
                            this.quality = Qualities.Unknown.value
                        })
                    }
            }
        } catch (e: Exception) { }
    }

    // ---------------------------------------------------------------
    // extractVideoLink
    // ---------------------------------------------------------------

    private suspend fun extractVideoLink(
        url: String,
        serverName: String,
        callback: (ExtractorLink) -> Unit
    ) {
        if (url.isBlank()) return
        val decodedUrl = try { URLDecoder.decode(url, "UTF-8") } catch (e: Exception) { url }

        when {
            decodedUrl.contains("mega.nz") || decodedUrl.contains("mega.co.nz") -> {
                callback.invoke(newExtractorLink("$name - Mega", "$name - Mega", decodedUrl, ExtractorLinkType.VIDEO) {
                    this.referer = "https://mega.nz"; this.quality = Qualities.Unknown.value
                })
            }
            decodedUrl.contains("filemoon") || decodedUrl.contains("filemoon.sx") -> {
                val scriptData = app.get(decodedUrl, headers = mapOf("Referer" to mainUrl))
                    .document.select("script").joinToString("\n") { it.data() }
                val match = Regex("""file:\s*["']([^"']+\.(?:m3u8|mp4)[^"']*)["']""").find(scriptData)
                    ?: Regex("""sources\s*=\s*\[\s*\{\s*file:\s*["']([^"']+)["']""").find(scriptData)
                if (match != null) {
                    val v = match.groupValues[1]
                    callback.invoke(newExtractorLink("$name - Filemoon", "$name - Filemoon", v,
                        if (v.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO) {
                        this.referer = "https://filemoon.sx"; this.quality = Qualities.Unknown.value
                    })
                }
            }
            decodedUrl.contains("dood") || decodedUrl.contains("doodstream") -> {
                val scriptData = app.get(decodedUrl).document.select("script").joinToString("\n") { it.data() }
                val dsMatch = Regex("""/pass_md5/([^"']*)""").find(scriptData)
                if (dsMatch != null) {
                    val token = app.get("https://doodstream.com/pass_md5/${dsMatch.groupValues[1]}").text
                    if (token.isNotBlank()) {
                        callback.invoke(newExtractorLink("$name - Doodstream", "$name - Doodstream",
                            "$token${generateRandomString()}", ExtractorLinkType.VIDEO) {
                            this.referer  = "https://doodstream.com/"
                            this.quality  = Qualities.Unknown.value
                            this.headers  = mapOf("Referer" to "https://doodstream.com/")
                        })
                    }
                }
            }
            decodedUrl.contains("streamtape") -> {
                val scriptData = app.get(decodedUrl).document.select("script").joinToString("\n") { it.data() }
                val match = Regex("""innerHTML\s*=\s*["']([^"']+)["']""").find(scriptData)
                if (match != null) {
                    callback.invoke(newExtractorLink("$name - Streamtape", "$name - Streamtape",
                        match.groupValues[1], ExtractorLinkType.VIDEO) {
                        this.referer = "https://streamtape.com"; this.quality = Qualities.Unknown.value
                    })
                }
            }
            decodedUrl.contains("voe") || decodedUrl.contains("voe.sx") -> {
                val scriptData = app.get(decodedUrl, headers = mapOf("Referer" to mainUrl))
                    .document.select("script").joinToString("\n") { it.data() }
                val match = Regex("""hls":\s*["']([^"']+)["']""").find(scriptData)
                    ?: Regex("""sources:\s*\[\s*\{\s*src:\s*["']([^"']+)["']""").find(scriptData)
                if (match != null) {
                    val v = match.groupValues[1]
                    callback.invoke(newExtractorLink("$name - Voe", "$name - Voe", v,
                        if (v.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO) {
                        this.referer = "https://voe.sx"; this.quality = Qualities.Unknown.value
                    })
                }
            }
            decodedUrl.contains("lulu") || decodedUrl.contains("lulustream") -> {
                val scriptData = app.get(decodedUrl, headers = mapOf("Referer" to mainUrl))
                    .document.select("script").joinToString("\n") { it.data() }
                val match = Regex("""file:\s*["']([^"']+\.(?:m3u8|mp4)[^"']*)["']""").find(scriptData)
                if (match != null) {
                    val v = match.groupValues[1]
                    callback.invoke(newExtractorLink("$name - Lulu", "$name - Lulu", v,
                        if (v.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO) {
                        this.referer = "https://lulustream.com"; this.quality = Qualities.Unknown.value
                    })
                }
            }
            decodedUrl.contains("mxdrop") -> {
                val scriptData = app.get(decodedUrl, headers = mapOf("Referer" to mainUrl))
                    .document.select("script").joinToString("\n") { it.data() }
                val match = Regex("""url:\s*["']([^"']+\.(?:m3u8|mp4)[^"']*)["']""").find(scriptData)
                if (match != null) {
                    val v = match.groupValues[1]
                    callback.invoke(newExtractorLink("$name - MxDrop", "$name - MxDrop", v,
                        if (v.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO) {
                        this.referer = "https://mxdrop.net"; this.quality = Qualities.Unknown.value
                    })
                }
            }
            decodedUrl.contains("gofile.io") -> {
                callback.invoke(newExtractorLink("$name - Gofile", "$name - Gofile", decodedUrl, ExtractorLinkType.VIDEO) {
                    this.referer = "https://gofile.io"; this.quality = Qualities.Unknown.value
                })
            }
            decodedUrl.contains("pixeldrain") -> {
                callback.invoke(newExtractorLink("$name - Pixeldrain", "$name - Pixeldrain", decodedUrl, ExtractorLinkType.VIDEO) {
                    this.referer = "https://pixeldrain.com"; this.quality = Qualities.Unknown.value
                })
            }
            decodedUrl.startsWith("http") -> {
                callback.invoke(newExtractorLink("$name - $serverName", "$name - $serverName", decodedUrl,
                    if (decodedUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO) {
                    this.referer = mainUrl; this.quality = Qualities.Unknown.value
                })
            }
        }
    }

    private fun generateRandomString(length: Int = 10): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..length).map { chars.random() }.joinToString("")
    }
}
