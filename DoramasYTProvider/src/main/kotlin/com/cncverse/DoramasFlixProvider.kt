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

    override val mainPage = mainPageOf(
        "$mainUrl/doramas-online"    to "Doramas",
        "$mainUrl/peliculas-online"  to "Películas",
        "$mainUrl/variedades-online" to "Variedades",
        "$mainUrl/generos/romance"   to "Romance",
        "$mainUrl/generos/comedia"   to "Comedia",
        "$mainUrl/generos/drama"     to "Drama",
        "$mainUrl/generos/accion"    to "Acción",
        "$mainUrl/generos/thriller"  to "Thriller",
    )

    // Imagen real: segunda img (después del GIF placeholder) o la que apunta a TMDB
    private fun Element.tmdbPoster(): String? {
        return this.select("img[src*='image.tmdb.org'], img[src*='tmdb.org']")
            .firstOrNull()?.attr("src")?.takeIf { it.isNotBlank() }
    }

    private fun Element.toSearchResponse(): SearchResponse? {
        val a     = this.selectFirst("a[href]") ?: return null
        val href  = fixUrlNull(a.attr("href")) ?: return null
        val title = this.selectFirst("h2, h3, .title")?.text()?.trim()
            ?: a.attr("title").takeIf { it.isNotBlank() }
            ?: return null
        val poster = this.tmdbPoster() ?: fixUrlNull(
            this.selectFirst("img:not([src^='data:'])")?.attr("src") ?: ""
        )
        val isMovie = href.contains("/peliculas-online/")
        return newMovieSearchResponse(title, href, if (isMovie) TvType.Movie else TvType.AsianDrama) {
            this.posterUrl = poster
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page <= 1) request.data else "${request.data}?page=$page"
        val doc = app.get(url, headers = mapOf("Referer" to mainUrl)).document
        val items = doc.select("ul > li, .list-item, .anime-item, article")
            .mapNotNull { it.toSearchResponse() }
        return newHomePageResponse(listOf(HomePageList(request.name, items)))
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        // Intentar búsqueda via AJAX
        try {
            val doc = app.get(mainUrl).document
            val csrf = doc.selectFirst("meta[name=csrf-token]")?.attr("content") ?: ""
            val ajax = app.post(
                "$mainUrl/buscar_ajax",
                data = mapOf("_token" to csrf, "q" to query),
                headers = mapOf("X-Requested-With" to "XMLHttpRequest", "Referer" to mainUrl)
            )
            val arr = org.json.JSONArray(ajax.text)
            val results = mutableListOf<SearchResponse>()
            for (i in 0 until arr.length()) {
                val obj   = arr.getJSONObject(i)
                val title = obj.optString("nombre").takeIf { it.isNotBlank() } ?: continue
                val url2  = fixUrl(obj.optString("url"))
                val img   = obj.optString("imagen").let { if (it.isNotBlank()) fixUrl(it) else null }
                results.add(newMovieSearchResponse(title, url2, TvType.AsianDrama) { posterUrl = img })
            }
            if (results.isNotEmpty()) return results
        } catch (_: Exception) {}

        // Fallback: página de búsqueda HTML
        val doc = app.get("$mainUrl/buscar?q=$query").document
        return doc.select("ul > li, .list-item, article").mapNotNull { it.toSearchResponse() }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc   = app.get(url).document
        val title = doc.selectFirst("h1, h2.title")?.text()?.trim() ?: "Sin título"

        // Poster: OG > TMDB directo
        val poster = doc.selectFirst("meta[property='og:image']")?.attr("content")?.takeIf { it.isNotBlank() }
            ?: doc.selectFirst("img[src*='image.tmdb.org']")?.attr("src")
            ?: doc.selectFirst("img:not([src^='data:'])")?.attr("src")

        val plot = doc.selectFirst(".sinopsis, .description, .plot, p.desc")?.text()?.trim() ?: ""
        val tags = doc.select("a[href*='/generos/']").map { it.text().trim() }
        val year = Regex("""(\d{4})""").find(
            doc.selectFirst(".year, .fecha, span.anio, .info")?.text() ?: ""
        )?.groupValues?.get(1)?.toIntOrNull()

        val episodes = mutableListOf<Episode>()
        // Intentar lista de episodios desde la página
        doc.select("a[href*='/ver/'], a[href*='/episodio'], a.episode-link").forEach { ep ->
            val epHref = fixUrlNull(ep.attr("href")) ?: return@forEach
            val epNum  = Regex("""(\d+)""").find(ep.text())?.groupValues?.get(1)?.toIntOrNull() ?: (episodes.size + 1)
            episodes.add(newEpisode(epHref) { name = "Episodio $epNum"; episode = epNum })
        }
        if (episodes.isEmpty()) {
            doc.select("a.episode, a[href*='episodio-'], .seasons a").forEach { ep ->
                val epHref = fixUrlNull(ep.attr("href")) ?: return@forEach
                val epNum  = Regex("""episodio[- _]?(\d+)""", RegexOption.IGNORE_CASE)
                    .find(epHref)?.groupValues?.get(1)?.toIntOrNull() ?: (episodes.size + 1)
                episodes.add(newEpisode(epHref) { name = "Episodio $epNum"; episode = epNum })
            }
        }
        val type = if (url.contains("/peliculas-online/")) TvType.Movie else TvType.AsianDrama
        return newTvSeriesLoadResponse(title, url, type, episodes) {
            posterUrl = poster; plot = plot; tags = tags; year = year
        }
    }

    override suspend fun loadLinks(
        data: String, isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data, headers = mapOf("Referer" to mainUrl)).document
        doc.select("iframe[src]").forEach { iframe ->
            val src = iframe.attr("src").trim()
            if (src.startsWith("http")) loadEmbedLink(src, callback)
        }
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

    private suspend fun loadEmbedLink(url: String, callback: (ExtractorLink) -> Unit) {
        try {
            val doc = app.get(url, headers = mapOf("Referer" to mainUrl)).document
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
                if (src.startsWith("http") && src != url) loadEmbedLink(src, callback)
            }
        } catch (_: Exception) {}
    }
}
