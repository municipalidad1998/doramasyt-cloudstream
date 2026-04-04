package com.doramasyt

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import org.jsoup.nodes.Element

class DoramasYTProvider : MainAPI() {

    override var mainUrl = "https://www.doramasyt.com"
    override var name = "DoramasYT"
    override val supportedTypes = setOf(
        TvType.TvSeries,
        TvType.Movie,
        TvType.AsianDrama
    )
    override var lang = "es"
    override val hasMainPage = true
    override val hasSearch = true

    // ─── Páginas principales ────────────────────────────────────────────────

    override val mainPage = mainPageOf(
        "$mainUrl/doramas?page=" to "Catálogo Completo",
        "$mainUrl/emision?page=" to "En Emisión",
        "$mainUrl/peliculas?page=" to "Películas",
        "$mainUrl/genero/k-drama?page=" to "K-Drama",
        "$mainUrl/genero/c-drama?page=" to "C-Drama",
        "$mainUrl/genero/j-drama?page=" to "J-Drama",
        "$mainUrl/genero/thai-drama?page=" to "Thai-Drama",
        "$mainUrl/genero/romance?page=" to "Romance",
        "$mainUrl/genero/comedia?page=" to "Comedia",
        "$mainUrl/genero/accion?page=" to "Acción"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = request.data + page
        val document = app.get(url).document
        val items = document.select("div.col-item, div.anime-info").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, items)
    }

    // ─── Parsear tarjeta ────────────────────────────────────────────────────

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("h3.title, .anime-name")?.text()?.trim() ?: return null
        val href = fixUrl(this.selectFirst("a")?.attr("href") ?: return null)
        val posterUrl = this.selectFirst("img")?.let {
            it.attr("data-src").ifEmpty { it.attr("src") }
        }
        val type = if (href.contains("/pelicula/")) TvType.Movie else TvType.AsianDrama
        return newAnimeSearchResponse(title, href, type) {
            this.posterUrl = posterUrl
        }
    }

    // ─── Búsqueda ───────────────────────────────────────────────────────────

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/buscar?q=${query.replace(" ", "+")}"
        val document = app.get(url).document
        return document.select("div.col-item, div.anime-info").mapNotNull { it.toSearchResult() }
    }

    // ─── Detalle de serie/película ──────────────────────────────────────────

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst("h1.title, h1.anime-title")?.text()?.trim() ?: ""
        val poster = document.selectFirst("div.poster img, div.anime-img img")?.let {
            it.attr("data-src").ifEmpty { it.attr("src") }
        }
        val description = document.selectFirst("div.sinopsis p, .description p")?.text()?.trim()

        val genres = document.select("div.genres a, .genre-list a").map { it.text().trim() }
        val year = document.selectFirst("span.year, .year")?.text()?.trim()?.toIntOrNull()
        val status = when (document.selectFirst("span.status")?.text()?.trim()?.lowercase()) {
            "en emisión" -> ShowStatus.Ongoing
            "finalizado" -> ShowStatus.Completed
            else -> null
        }

        val isMov = url.contains("/pelicula/")

        if (isMov) {
            // Película
            val episodeUrl = url
            return newMovieLoadResponse(title, url, TvType.Movie, episodeUrl) {
                this.posterUrl = poster
                this.plot = description
                this.tags = genres
                this.year = year
            }
        }

        // Serie con episodios
        val episodes = document.select("ul.episodes-list li, div.episode-list .ep-item")
            .reversed()
            .mapIndexed { idx, el ->
                val epHref = fixUrl(el.selectFirst("a")?.attr("href") ?: return@mapIndexed null)
                val epNum = el.selectFirst("span.num, .ep-num")?.text()?.trim()?.toIntOrNull() ?: (idx + 1)
                val epName = el.selectFirst("span.name, .ep-title")?.text()?.trim()
                Episode(data = epHref, episode = epNum, name = epName)
            }.filterNotNull()

        return newAnimeLoadResponse(title, url, TvType.AsianDrama) {
            this.posterUrl = poster
            this.plot = description
            this.tags = genres
            this.year = year
            this.showStatus = status
            addEpisodes(DubStatus.Subbed, episodes)
        }
    }

    // ─── Extracción de links de video ───────────────────────────────────────

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document

        // Buscar iframes o reproductores embebidos
        val iframes = document.select("iframe[src], iframe[data-src]").map {
            it.attr("data-src").ifEmpty { it.attr("src") }
        }.filter { it.isNotBlank() }

        val videoLinks = document.select("div.player-container script").map { it.data() }

        // Intentar extraer enlace de video directo del JS inline
        videoLinks.forEach { script ->
            Regex("""file\s*:\s*["'](.+?)["']""").findAll(script).forEach { match ->
                val videoUrl = match.groupValues[1]
                if (videoUrl.contains(".m3u8") || videoUrl.contains(".mp4")) {
                    callback(
                        ExtractorLink(
                            source = name,
                            name = name,
                            url = videoUrl,
                            referer = mainUrl,
                            quality = Qualities.Unknown.value,
                            isM3u8 = videoUrl.contains(".m3u8")
                        )
                    )
                }
            }
        }

        // Cargar extractores para iframes
        iframes.forEach { iframeUrl ->
            loadExtractor(this@DoramasYTProvider.fixUrl(iframeUrl), subtitleCallback, callback)
        }

        // Buscar botones de servidor
        val servers = document.select("ul.servers li, div.servers-list a")
        for (server in servers) {
            val serverUrl = server.attr("data-url").ifEmpty { server.attr("href") }
            if (serverUrl.isNotBlank()) {
                loadExtractor(this@DoramasYTProvider.fixUrl(serverUrl), subtitleCallback, callback)
            }
        }

        return true
    }
}
