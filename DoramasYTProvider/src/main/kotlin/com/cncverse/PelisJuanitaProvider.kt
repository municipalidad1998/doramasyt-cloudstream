package com.cncverse

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.nodes.Element

class PelisJuanitaProvider : MainAPI() {

    override var mainUrl  = "https://doramaexpress.com"
    override var name     = "DoramaExpress"
    override val hasMainPage = true
    override var lang     = "es"
    override val supportedTypes = setOf(TvType.AsianDrama, TvType.TvSeries, TvType.Movie)

    private val placeholders = listOf("anime.png", "capblank.png", "no-image", "placeholder", "default", "noposter")

    override val mainPage = mainPageOf(
        "$mainUrl/doramas"         to "Doramas",
        "$mainUrl/peliculas"       to "Películas",
        "$mainUrl/genero/k-drama"  to "K-Drama",
        "$mainUrl/genero/c-drama"  to "C-Drama",
        "$mainUrl/genero/romance"  to "Romance",
        "$mainUrl/genero/accion"   to "Acción",
        "$mainUrl/genero/comedia"  to "Comedia",
    )

    private fun String.isPlaceholder() = placeholders.any { this.contains(it) }

    private fun Element.bestPoster(): String? {
        val candidates = listOf(
            this.selectFirst("img[src*='image.tmdb.org']")?.attr("src"),
            this.selectFirst("img[data-src*='image.tmdb.org']")?.attr("data-src"),
            this.selectFirst("img.lazy, img[data-src]")?.attr("data-src"),
            this.selectFirst("img[data-img]")?.attr("data-img"),
            this.selectFirst("img[data-original]")?.attr("data-original"),
            this.selectFirst("img:not([src^='data:'])")?.attr("src"),
        )
        return candidates.firstOrNull { !it.isNullOrBlank() && !it.isPlaceholder() }
    }

    private fun Element.toSearchResponse(): SearchResponse? {
        val a     = this.selectFirst("a") ?: return null
        val href  = fixUrlNull(a.attr("href")) ?: return null
        val title = this.selectFirst("h2, h3, .title, .titulo, .nombre")?.text()?.trim()
            ?: return null
        val poster = fixUrlNull(this.bestPoster() ?: "")
        val isMovie = href.contains("/peliculas") || href.contains("/pelicula")
        return newMovieSearchResponse(title, href, if (isMovie) TvType.Movie else TvType.AsianDrama) {
            posterUrl = poster
        }
    }

    private fun buildUrl(base: String, page: Int) =
        if (page <= 1) base else "$base?p=$page"

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = app.get(buildUrl(request.data, page),
            headers = mapOf("Referer" to mainUrl)).document
        val items = doc.select(
            "li.ficha_efecto, .anime-item, article, .post-item, .item, li.item, .card"
        ).mapNotNull { it.toSearchResponse() }
        return newHomePageResponse(listOf(HomePageList(request.name, items)))
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        // Intento 1: AJAX
        try {
            val csrf = app.get(mainUrl).document
                .selectFirst("meta[name=csrf-token]")?.attr("content") ?: ""
            val ajax = app.post(
                "$mainUrl/buscar_ajax",
                data = mapOf("_token" to csrf, "q" to query),
                headers = mapOf("X-Requested-With" to "XMLHttpRequest", "Referer" to mainUrl)
            )
            val arr = org.json.JSONArray(ajax.text)
            val results = mutableListOf<SearchResponse>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val title = obj.optString("nombre").takeIf { it.isNotBlank() } ?: continue
                val url2  = fixUrl(obj.optString("url"))
                val img   = obj.optString("imagen").let {
                    if (it.isNotBlank() && !it.isPlaceholder()) fixUrl(it) else null
                }
                results.add(newMovieSearchResponse(title, url2, TvType.AsianDrama) { posterUrl = img })
            }
            if (results.isNotEmpty()) return results
        } catch (_: Exception) {}

        // Intento 2: HTML búsqueda
        return app.get("$mainUrl/buscar?q=$query").document
            .select("li.ficha_efecto, .anime-item, article, .item")
            .mapNotNull { it.toSearchResponse() }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc   = app.get(url).document
        val title = doc.selectFirst("h1, .titulo-principal")?.text()?.trim() ?: "Sin título"

        // Poster: OG meta > TMDB > data-src > src
        val poster = doc.selectFirst("meta[property='og:image']")?.attr("content")
            ?.takeIf { !it.isBlank() && !it.isPlaceholder() }
            ?: doc.selectFirst("img[src*='image.tmdb.org']")?.attr("src")
            ?: doc.selectFirst("img[data-src*='image.tmdb.org']")?.attr("data-src")
            ?: doc.selectFirst("img.lazy, img[data-src]")?.attr("data-src")
                ?.takeIf { !it.isPlaceholder() }
            ?: doc.selectFirst("img:not([src^='data:'])")?.attr("src")
                ?.takeIf { !it.isPlaceholder() }

        val plot = doc.selectFirst(".sinopsis, .description, p.text-muted, .plot")?.text()?.trim() ?: ""
        val year = Regex("""(\d{4})""").find(
            doc.selectFirst(".year, .fecha, span.anio, time")?.text() ?: doc.title()
        )?.groupValues?.get(1)?.toIntOrNull()
        val tags = doc.select("a[href*='/genero/'], a[href*='/category/']").map { it.text().trim() }

        val episodes = mutableListOf<Episode>()

        // AJAX episodes
        val ajaxSection = doc.selectFirst("section.caplist, [data-ajax]")
        val ajaxUrl     = ajaxSection?.attr("data-ajax")
        val csrf        = doc.selectFirst("meta[name=csrf-token]")?.attr("content") ?: ""

        if (!ajaxUrl.isNullOrBlank() && csrf.isNotBlank()) {
            try {
                val json = org.json.JSONObject(
                    app.post(ajaxUrl, data = mapOf("_token" to csrf),
                        headers = mapOf("X-Requested-With" to "XMLHttpRequest", "Referer" to url)).text
                )
                val eps = json.optJSONArray("eps") ?: json.optJSONArray("caps")
                if (eps != null) {
                    for (i in 0 until eps.length()) {
                        val ep     = eps.getJSONObject(i)
                        val epHref = fixUrlNull(ep.optString("url")) ?: continue
                        val num    = ep.optInt("episodio", i + 1)
                        episodes.add(newEpisode(epHref) { name = "Episodio $num"; episode = num })
                    }
                }
            } catch (_: Exception) {}
        }

        // Fallback HTML
        if (episodes.isEmpty()) {
            doc.select(
                "a[href*='/ver/'], a[href*='/episodio'], a[href*='/capitulo'], a.episode-link, .episodes a"
            ).forEach { a ->
                val epHref = fixUrlNull(a.attr("href")) ?: return@forEach
                val num = Regex("""(\d+)""").find(a.text())?.groupValues?.get(1)?.toIntOrNull()
                    ?: (episodes.size + 1)
                episodes.add(newEpisode(epHref) { name = a.text().trim().ifBlank { "Episodio $num" }; episode = num })
            }
        }

        val type = if (url.contains("/peliculas") || url.contains("/pelicula")) TvType.Movie
                   else TvType.AsianDrama

        return newTvSeriesLoadResponse(title, url, type, episodes) {
            posterUrl = fixUrlNull(poster ?: "")
            backgroundPosterUrl = posterUrl; plot = plot; year = year; tags = tags
        }
    }

    override suspend fun loadLinks(
        data: String, isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data, headers = mapOf("Referer" to mainUrl)).document

        // Resource token si el sitio lo usa
        var token = ""
        doc.select("script").forEach { s ->
            Regex("""resource_token\s*=\s*['"]([^'"]+)['"]""").find(s.data())
                ?.let { token = it.groupValues[1] }
        }

        // Botones de servidor
        doc.select("button.play-video[data-player], a[data-src]").forEach { btn ->
            val encoded = btn.attr("data-player").takeIf { it.isNotBlank() }
                ?: btn.attr("data-src")
            if (!encoded.isNullOrBlank()) {
                val playerBase = doc.selectFirst(".player")?.attr("data-key")
                    ?: "$mainUrl/reproductor?video="
                extractEmbed("${playerBase}${encoded}&token=$token", callback)
            }
        }

        // Iframes directos
        doc.select("iframe[src], iframe[data-src]").forEach { iframe ->
            val src = (iframe.attr("src").takeIf { it.isNotBlank() }
                ?: iframe.attr("data-src")).trim()
            if (src.startsWith("http")) extractEmbed(src, callback)
        }

        // Links directos a video
        doc.select("source[src], video[src]").forEach { v ->
            val src = v.attr("src").trim()
            if (src.startsWith("http")) callback.invoke(
                newExtractorLink(name, name, src,
                    if (src.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO) {
                    referer = mainUrl; quality = Qualities.Unknown.value
                }
            )
        }
        return true
    }

    private suspend fun extractEmbed(url: String, callback: (ExtractorLink) -> Unit) {
        try {
            val doc = app.get(url, headers = mapOf("Referer" to mainUrl,
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")).document
            doc.select("script").forEach { s ->
                Regex("""["'](https?://[^"']+\.(?:m3u8|mp4)[^"']*)["']""").findAll(s.data()).forEach {
                    val v = it.groupValues[1]
                    callback.invoke(newExtractorLink(name, name, v,
                        if (v.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO) {
                        referer = url; quality = Qualities.Unknown.value
                    })
                }
            }
            doc.select("iframe[src]").forEach { iframe ->
                val src = iframe.attr("src").trim()
                if (src.startsWith("http") && src != url) extractEmbed(src, callback)
            }
        } catch (_: Exception) {}
    }
}
