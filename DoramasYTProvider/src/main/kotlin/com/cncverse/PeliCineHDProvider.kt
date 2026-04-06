package com.cncverse

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.nodes.Element

class PeliCineHDProvider : MainAPI() {

    override var mainUrl  = "https://pelicinehd.com"
    override var name     = "PeliCineHD"
    override val hasMainPage = true
    override var lang     = "es"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "$mainUrl/peliculas/"             to "Películas",
        "$mainUrl/series/"                to "Series",
        "$mainUrl/release/2025/"          to "Estrenos 2025",
        "$mainUrl/category/accion/"       to "Acción",
        "$mainUrl/category/drama/"        to "Drama",
        "$mainUrl/category/comedia/"      to "Comedia",
        "$mainUrl/category/terror/"       to "Terror",
        "$mainUrl/category/animacion/"    to "Animación",
        "$mainUrl/category/ciencia-ficcion/" to "Ciencia Ficción",
        "$mainUrl/category/romance/"      to "Romance",
        "$mainUrl/category/crimen/"       to "Crimen",
    )

    // En PeliCineHD las imágenes TMDB están directamente en el src
    private fun Element.tmdbPoster(): String? =
        this.selectFirst("img[src*='image.tmdb.org'], img[src*='tmdb.org'], img.wp-post-image, img[src*='wp-content']")
            ?.attr("src")?.takeIf { it.isNotBlank() }

    private fun Element.toSearchResponse(): SearchResponse? {
        // WordPress: article o post wrapper
        val link  = this.selectFirst("a[href*='/peliculas/'], a[href*='/series/'], a.lnk-blk, h2 a, h3 a, a[href]")
            ?: return null
        val href  = fixUrlNull(link.attr("href")) ?: return null
        if (!href.contains(mainUrl)) return null
        val title = this.selectFirst("h2, h3, .entry-title, .post-title")?.text()?.trim()
            ?: link.text().trim().takeIf { it.isNotBlank() } ?: return null
        val poster = this.tmdbPoster()
        val isMovie = href.contains("/peliculas/")
        return newMovieSearchResponse(title, href, if (isMovie) TvType.Movie else TvType.TvSeries) {
            posterUrl = poster
        }
    }

    private fun buildPageUrl(base: String, page: Int): String =
        if (page <= 1) base else "${base.trimEnd('/')}/page/$page/"

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = buildPageUrl(request.data, page)
        val doc = app.get(url, headers = mapOf("Referer" to mainUrl)).document
        val items = doc.select("article, .post-item, .item, .card, li.post")
            .mapNotNull { it.toSearchResponse() }
        return newHomePageResponse(listOf(HomePageList(request.name, items)))
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        val doc = app.get("$mainUrl/?s=${java.net.URLEncoder.encode(query, "UTF-8")}").document
        return doc.select("article, .post-item, .item, li.post")
            .mapNotNull { it.toSearchResponse() }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc   = app.get(url).document
        val title = doc.selectFirst("h1.entry-title, h1.post-title, h1")?.text()?.trim() ?: "Sin título"

        // Poster: OG meta > TMDB img > wp-content img
        val poster = doc.selectFirst("meta[property='og:image']")?.attr("content")?.takeIf { it.isNotBlank() }
            ?: doc.selectFirst("img[src*='image.tmdb.org/t/p/w185'], img[src*='image.tmdb.org/t/p/w500']")?.attr("src")
            ?: doc.selectFirst("img.wp-post-image, img[src*='wp-content/uploads']")?.attr("src")

        val bgPoster = doc.selectFirst("img[src*='image.tmdb.org/t/p/w1280'], img[src*='image.tmdb.org/t/p/original']")
            ?.attr("src") ?: poster

        val plot = doc.selectFirst(".entry-content p, .description, .sinopsis, .plot")?.text()?.trim() ?: ""
        val year = Regex("""(\d{4})""").find(
            doc.selectFirst(".year, .fecha, span.year, time")?.text() ?: doc.title()
        )?.groupValues?.get(1)?.toIntOrNull()
        val tags = doc.select("a[href*='/category/'], a[rel='tag']").map { it.text().trim() }

        val episodes = mutableListOf<Episode>()
        val isMovie  = url.contains("/peliculas/")

        if (!isMovie) {
            // Series: buscar episodios organizados por temporada
            // Estructura WordPress: div/ul con links por temporada
            var epNum = 1
            doc.select(".episodes a, .ep-list a, li a[href*='/ver-'], li a[href*='/episodio'], a.episode-link").forEach { ep ->
                val epHref = fixUrlNull(ep.attr("href")) ?: return@forEach
                val num = Regex("""[xXeE](\d+)|episodio[- ]?(\d+)|cap[ií]tulo[- ]?(\d+)""", RegexOption.IGNORE_CASE)
                    .find(epHref + " " + ep.text())?.groupValues?.drop(1)?.firstOrNull { it.isNotBlank() }
                    ?.toIntOrNull() ?: epNum
                val season = Regex("""[Ss](\d+)[xXeE]""").find(ep.text())?.groupValues?.get(1)?.toIntOrNull() ?: 1
                episodes.add(newEpisode(epHref) {
                    name = ep.text().trim().ifBlank { "Episodio $num" }
                    episode = num; this.season = season
                })
                epNum++
            }
            // Si no encontró nada, buscar iframes (película con un solo reproductor)
            if (episodes.isEmpty()) {
                doc.select("a[href*='ver-'], a[href*='watch'], a.play-btn").forEach { a ->
                    val epHref = fixUrlNull(a.attr("href")) ?: return@forEach
                    episodes.add(newEpisode(epHref) { name = "Ver"; episode = 1 })
                }
            }
        }

        val type = if (isMovie) TvType.Movie else TvType.TvSeries
        return newTvSeriesLoadResponse(title, url, type, episodes) {
            posterUrl = poster; backgroundPosterUrl = bgPoster
            plot = plot; year = year; tags = tags
        }
    }

    override suspend fun loadLinks(
        data: String, isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data, headers = mapOf("Referer" to mainUrl)).document
        // Iframes embebidos
        doc.select("iframe[src], iframe[data-src]").forEach { iframe ->
            val src = (iframe.attr("src").takeIf { it.isNotBlank() }
                ?: iframe.attr("data-src")).trim()
            if (src.startsWith("http")) extractEmbed(src, callback)
        }
        // Videos directos
        doc.select("source[src], video[src]").forEach { v ->
            val src = v.attr("src").trim()
            if (src.startsWith("http")) callback.invoke(
                newExtractorLink(name, name, src,
                    if (src.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO) {
                    referer = mainUrl; quality = Qualities.Unknown.value
                }
            )
        }
        // Scripts con URLs de video
        doc.select("script").forEach { s ->
            Regex("""["'](https?://[^"']+\.(?:m3u8|mp4)[^"']*)["']""").findAll(s.data()).forEach {
                val v = it.groupValues[1]
                callback.invoke(newExtractorLink(name, name, v,
                    if (v.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO) {
                    referer = mainUrl; quality = Qualities.Unknown.value
                })
            }
        }
        return true
    }

    private suspend fun extractEmbed(url: String, callback: (ExtractorLink) -> Unit) {
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
                if (src.startsWith("http") && src != url) extractEmbed(src, callback)
            }
        } catch (_: Exception) {}
    }
}
