package com.cncverse

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.nodes.Element

class DoramasFlixProvider : MainAPI() {

    override var mainUrl  = "https://doramasflix.in"
    override var name     = "DoramasFlix"
    override val hasMainPage = true
    override var lang     = "es"
    override val supportedTypes = setOf(TvType.AsianDrama, TvType.TvSeries, TvType.Movie)

    // El homepage carga secciones en HTML; catálogo/genero son JS-rendered
    // Usamos letras y búsqueda para acceso real al contenido
    override val mainPage = mainPageOf(
        "$mainUrl/letras/a" to "Series A",
        "$mainUrl/letras/b" to "Series B",
        "$mainUrl/letras/c" to "Series C",
        "$mainUrl/letras/d" to "Series D",
        "$mainUrl/letras/e" to "Series E",
        "$mainUrl/letras/s" to "Series S",
        "$mainUrl/letras/t" to "Series T",
        "$mainUrl/letras/m" to "Series M",
        "$mainUrl/letras/l" to "Series L",
        "$mainUrl/letras/k" to "Series K",
    )

    // Imagen real: la segunda img (después del GIF placeholder) apunta a TMDB
    private fun Element.tmdbPoster(): String? =
        this.selectFirst("img[src*='image.tmdb.org'], img[src*='tmdb.org']")
            ?.attr("src")?.takeIf { it.isNotBlank() }
        ?: this.select("img").lastOrNull { 
            !it.attr("src").startsWith("data:") && it.attr("src").isNotBlank()
        }?.attr("src")

    private fun Element.toSearchResponse(): SearchResponse? {
        val a     = this.selectFirst("a[href*='/doramas-online/'], a[href*='/peliculas-online/'], a[href]")
            ?: return null
        val href  = fixUrlNull(a.attr("href")) ?: return null
        if (!href.contains(mainUrl) && !href.startsWith("/")) return null
        val title = this.selectFirst("h2, h3, .title, p")?.text()?.trim()
            ?: a.attr("title").trim().takeIf { it.isNotBlank() }
            ?: return null
        val poster = this.tmdbPoster()
        val isMovie = href.contains("/peliculas-online/")
        return newMovieSearchResponse(title, fixUrl(href), 
            if (isMovie) TvType.Movie else TvType.AsianDrama) {
            this.posterUrl = poster
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page <= 1) request.data else "${request.data}?page=$page"
        val doc = app.get(url, headers = mapOf("Referer" to mainUrl)).document
        // Las páginas /letras/X tienen lista de doramas en HTML
        val items = doc.select("li, .anime-card, .serie-card, article, .item")
            .mapNotNull { it.toSearchResponse() }
        return newHomePageResponse(listOf(HomePageList(request.name, items)))
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        // Búsqueda directa en la página de resultados
        val searchUrl = "$mainUrl/buscar?q=${java.net.URLEncoder.encode(query, "UTF-8")}"
        val doc = app.get(searchUrl, headers = mapOf("Referer" to mainUrl)).document

        // Intentar selectores de resultados
        val results = doc.select("li, .anime-card, article, .search-item, .item")
            .mapNotNull { it.toSearchResponse() }
        if (results.isNotEmpty()) return results

        // Alternativa: buscar en el HTML del homepage por título
        val homeDoc = app.get(mainUrl).document
        return homeDoc.select("li").toList().filter {
            it.text().contains(query, ignoreCase = true)
        }.mapNotNull { it.toSearchResponse() }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc   = app.get(url, headers = mapOf("Referer" to mainUrl)).document
        val title = doc.selectFirst("h1, h2.title")?.text()?.trim() ?: "Sin título"

        // Poster: OG meta > TMDB img directo
        val poster = doc.selectFirst("meta[property='og:image']")?.attr("content")
            ?.takeIf { it.isNotBlank() }
            ?: doc.selectFirst("img[src*='image.tmdb.org']")?.attr("src")
            ?: doc.select("img").lastOrNull { 
                !it.attr("src").startsWith("data:") && it.attr("src").isNotBlank() 
            }?.attr("src")

        val bgPoster = doc.selectFirst("meta[property='og:image']")?.attr("content")
            ?.takeIf { it.isNotBlank() } ?: poster

        val plot = doc.selectFirst(".sinopsis, .description, p")?.text()?.trim() ?: ""
        val tags = doc.select("a[href*='/generos/']").map { it.text().trim() }
        val year = Regex("""(\d{4})""").find(
            doc.selectFirst(".year, .fecha, .info")?.text() ?: doc.title()
        )?.groupValues?.get(1)?.toIntOrNull()

        val episodes = mutableListOf<Episode>()
        // Buscar episodios con varios selectores
        val epLinks = doc.select("a[href*='/ver/'], a.episode, a.ep-link, .seasons a[href]")
        epLinks.forEach { ep ->
            val epHref = fixUrlNull(ep.attr("href")) ?: continue
            val num = Regex("""(\d+)""").find(ep.text() + epHref)
                ?.groupValues?.get(1)?.toIntOrNull() ?: (episodes.size + 1)
            episodes.add(newEpisode(epHref) { 
                name = ep.text().trim().ifBlank { "Episodio $num" }; episode = num 
            })
        }

        val isMovie = url.contains("/peliculas-online/")
        val type = if (isMovie) TvType.Movie else if (episodes.size > 1) TvType.TvSeries else TvType.AsianDrama
        return newTvSeriesLoadResponse(title, url, type, episodes) {
            this.posterUrl = poster; this.backgroundPosterUrl = bgPoster
            this.plot = plot; this.year = year; this.tags = tags
        }
    }

    override suspend fun loadLinks(
        data: String, isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data, headers = mapOf("Referer" to mainUrl)).document
        for (iframe in doc.select("iframe[src], iframe[data-src]")) {
            val src = (iframe.attr("src").takeIf { it.isNotBlank() }
                ?: iframe.attr("data-src")).trim()
            if (src.startsWith("http")) extractEmbed(src, callback)
        }
        for (v in doc.select("source[src], video[src]")) {
            val src = v.attr("src").trim()
            if (src.startsWith("http")) callback.invoke(
                newExtractorLink(name, name, src,
                    if (src.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO) {
                    referer = mainUrl; quality = Qualities.Unknown.value
        })
        }
        return true
    }

    private suspend fun extractEmbed(url: String, cb: (ExtractorLink) -> Unit) {
        try {
            val doc = app.get(url, headers = mapOf("Referer" to mainUrl)).document
            for (s in doc.select("script")) {
                Regex("""["'](https?://[^"']+\.(?:m3u8|mp4)[^"']*)["']""").findAll(s.data())
                    .forEach { m ->
                        val v = m.groupValues[1]
                        cb.invoke(newExtractorLink(name, name, v,
                            if (v.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO) {
                            referer = url; quality = Qualities.Unknown.value
            })
                    }
            }
            for (iframe in doc.select("iframe[src]")) {
                val src = iframe.attr("src").trim()
                if (src.startsWith("http") && src != url) extractEmbed(src, cb)
            }
        } catch (_: Exception) {}
    }
}
