package com.cncverse

import com.lagradost.cloudstream3.plugins.Plugin
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
        "$mainUrl/peliculas/"                to "Películas",
        "$mainUrl/series/"                   to "Series",
        "$mainUrl/peliculas/page/2/"         to "Películas p2",
        "$mainUrl/category/accion/"          to "Acción",
        "$mainUrl/category/drama/"           to "Drama",
        "$mainUrl/category/comedia/"         to "Comedia",
        "$mainUrl/category/terror/"          to "Terror",
        "$mainUrl/category/animacion/"       to "Animación",
        "$mainUrl/category/ciencia-ficcion/" to "Ciencia Ficción",
        "$mainUrl/category/romance/"         to "Romance",
    )

    // Cada item es un <li> que contiene h2 (título) + img TMDB + link "Ver pelicula/serie"
    private fun Element.toSearchResponse(): SearchResponse? {
        val title  = this.selectFirst("h2")?.text()?.trim() ?: return null
        val poster = this.selectFirst("img[src*='image.tmdb.org'], img[src*='tmdb.org']")
            ?.attr("src")?.takeIf { it.isNotBlank() }
        // El link puede ser el wrapper del item o el "Ver pelicula/serie" button
        val href = this.selectFirst(
            "a[href*='/peliculas/'], a[href*='/series/'], a[href*='/category/']"
        )?.attr("href")?.takeIf { it.isNotBlank() } ?: return null
        val isMovie = href.contains("/peliculas/")
        return newMovieSearchResponse(title, fixUrl(href),
            if (isMovie) TvType.Movie else TvType.TvSeries) {
            this.posterUrl = poster
        }
    }

    private fun buildPage(base: String, page: Int): String =
        if (page <= 1) base else base.trimEnd('/') + "/page/$page/"

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = buildPage(request.data, page)
        val doc = app.get(url).document
        // Seleccionamos solo <li> que tengan img TMDB (= cards de contenido real)
        val items = doc.select("li").toList().filter {
            it.selectFirst("img[src*='tmdb']") != null && it.selectFirst("h2") != null
        }.mapNotNull { it.toSearchResponse() }
        return newHomePageResponse(listOf(HomePageList(request.name, items)))
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        val doc = app.get("$mainUrl/?s=${java.net.URLEncoder.encode(query, "UTF-8")}").document
        return doc.select("li").toList().filter {
            it.selectFirst("img[src*='tmdb']") != null && it.selectFirst("h2") != null
        }.mapNotNull { it.toSearchResponse() }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc    = app.get(url).document
        val title  = doc.selectFirst("h1")?.text()?.trim() ?: "Sin título"
        val poster = doc.selectFirst("meta[property='og:image']")?.attr("content")
            ?.takeIf { it.isNotBlank() }
            ?: doc.selectFirst("img[src*='image.tmdb.org/t/p/w185'], img[src*='image.tmdb.org/t/p/w500']")
                ?.attr("src")
        val bgPoster = doc.selectFirst("img[src*='image.tmdb.org/t/p/w1280'], img[src*='original']")
            ?.attr("src") ?: poster
        val plot = doc.selectFirst(".entry-content > p, .description, .sinopsis")
            ?.text()?.trim() ?: ""
        val year = Regex("""(\d{4})""").find(
            doc.selectFirst("time, .year, span[class*='year']")?.text() ?: doc.title()
        )?.groupValues?.get(1)?.toIntOrNull()
        val tags = doc.select("a[href*='/category/'], a[rel='tag']").map { it.text().trim() }

        val episodes = mutableListOf<Episode>()
        val isMovie  = url.contains("/peliculas/")

        if (!isMovie) {
            // Series: episodios listados con imágenes TMDB en <li>
            var epNum = 1
            val episodeLis = doc.select("li").toList().filter {
                it.text().contains("x", ignoreCase = true) || it.text().matches(Regex(".*\\d+x\\d+.*"))
            }
            for (li in episodeLis) {
                val epLink = li.selectFirst("a[href]") ?: continue
                val epHref = fixUrlNull(epLink.attr("href")) ?: continue
                val epText = li.selectFirst("h2, h3, .episodiotitle")?.text()?.trim()
                    ?: epLink.text().trim().ifBlank { "Episodio $epNum" }
                val seasonMatch = Regex("""(\d+)x(\d+)""").find(li.text())
                val season = seasonMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1
                val epNumber = seasonMatch?.groupValues?.get(2)?.toIntOrNull() ?: epNum
                val epPoster = li.selectFirst("img[src*='tmdb']")?.attr("src")
                episodes.add(newEpisode(epHref) {
                    name = epText; episode = epNumber; this.season = season
                    this.posterUrl = epPoster
                })
                epNum++
            }
        }

        val type = if (isMovie) TvType.Movie else TvType.TvSeries
        return newTvSeriesLoadResponse(title, url, type, episodes) {
            this.posterUrl = poster; this.backgroundPosterUrl = bgPoster
            this.plot = plot; this.year = year; this.tags = tags
        }
    }

    override suspend fun loadLinks(
        data: String, isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document
        for (iframe in doc.select("iframe[src], iframe[data-src]")) {
            val src = (iframe.attr("src").takeIf { it.isNotBlank() }
                ?: iframe.attr("data-src")).trim()
            if (src.startsWith("http")) extractEmbed(src, callback)
        }
        for (s in doc.select("script")) {
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

    private suspend fun extractEmbed(url: String, cb: (ExtractorLink) -> Unit) {
        try {
            val doc = app.get(url, headers = mapOf("Referer" to mainUrl)).document
            for (s in doc.select("script")) {
                Regex("""["'](https?://[^"']+\.(?:m3u8|mp4)[^"']*)["']""").findAll(s.data()).forEach {
                    val v = it.groupValues[1]
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

class PeliCineHDPlugin : Plugin() {
    override fun load(context: android.content.Context) {
        com.lagradost.cloudstream3.plugins.registerMainAPI(com.cncverse.PeliCineHDProvider())
    }
}
