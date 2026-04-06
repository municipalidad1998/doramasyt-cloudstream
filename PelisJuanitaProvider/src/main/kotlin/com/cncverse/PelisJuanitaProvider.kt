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

    private val placeholders = listOf("anime.png","capblank","no-image","placeholder","default","noposter")

    override val mainPage = mainPageOf(
        "$mainUrl/doramas"        to "Doramas",
        "$mainUrl/peliculas"      to "Películas",
        "$mainUrl/genero/k-drama" to "K-Drama",
        "$mainUrl/genero/c-drama" to "C-Drama",
        "$mainUrl/genero/romance" to "Romance",
        "$mainUrl/genero/accion"  to "Acción",
        "$mainUrl/genero/comedia" to "Comedia",
        "$mainUrl/genero/drama"   to "Drama",
    )

    private fun String.isPlaceholder() = placeholders.any { this.contains(it) }

    private fun Element.realPoster(): String? = listOf(
        this.selectFirst("img[src*='image.tmdb.org']")?.attr("src"),
        this.selectFirst("img[data-src*='image.tmdb.org']")?.attr("data-src"),
        this.selectFirst("img.lazy")?.attr("data-src"),
        this.selectFirst("img[data-src]")?.attr("data-src"),
        this.selectFirst("img[data-img]")?.attr("data-img"),
        this.selectFirst("img:not([src^='data:'])")?.attr("src"),
    ).firstOrNull { !it.isNullOrBlank() && !it.isPlaceholder() }

    private fun Element.toSearchResponse(): SearchResponse? {
        val a     = this.selectFirst("a[href]") ?: return null
        val href  = fixUrlNull(a.attr("href")) ?: return null
        val title = this.selectFirst("h2,h3,.titulo,.title,.nombre")
            ?.text()?.trim() ?: a.attr("title").trim().takeIf { it.isNotBlank() } ?: return null
        val isMovie = href.contains("/peliculas") || href.contains("/pelicula")
        return newMovieSearchResponse(title, href, if (isMovie) TvType.Movie else TvType.AsianDrama) {
            posterUrl = fixUrlNull(this@toSearchResponse.realPoster() ?: "")
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page <= 1) request.data else "${request.data}?p=$page"
        val doc = app.get(url).document
        val items = doc.select("li.ficha_efecto,.anime-item,article,.item,li[class*='anime'],li[class*='serie']")
            .mapNotNull { it.toSearchResponse() }
        return newHomePageResponse(listOf(HomePageList(request.name, items)))
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        try {
            val csrf = app.get(mainUrl).document.selectFirst("meta[name=csrf-token]")?.attr("content") ?: ""
            if (csrf.isNotBlank()) {
                val arr = org.json.JSONArray(
                    app.post("$mainUrl/buscar_ajax",
                        data = mapOf("_token" to csrf, "q" to query),
                        headers = mapOf("X-Requested-With" to "XMLHttpRequest")).text
                )
                val results = mutableListOf<SearchResponse>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val img = o.optString("imagen","").let { if (it.isNotBlank() && !it.isPlaceholder()) fixUrl(it) else null }
                    results.add(newMovieSearchResponse(o.getString("nombre"), fixUrl(o.getString("url")), TvType.AsianDrama) { posterUrl = img })
                }
                if (results.isNotEmpty()) return results
            }
        } catch (_: Exception) {}
        return app.get("$mainUrl/buscar?q=$query").document
            .select("li.ficha_efecto,.anime-item,article").mapNotNull { it.toSearchResponse() }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc   = app.get(url).document
        val title = doc.selectFirst("h1")?.text()?.trim() ?: "Sin título"
        val poster = doc.selectFirst("meta[property='og:image']")?.attr("content")?.takeIf { !it.isPlaceholder() }
            ?: doc.selectFirst("img[src*='tmdb'],img[data-src]")?.let {
                it.attr("src").takeIf { s -> !s.isPlaceholder() } ?: it.attr("data-src").takeIf { s -> !s.isPlaceholder() }
            }
        val plot = doc.selectFirst(".sinopsis,.description,p.text-muted,.plot")?.text()?.trim() ?: ""
        val tags = doc.select("a[href*='/genero/'],a[href*='/category/']").map { it.text().trim() }
        val year = Regex("""(\d{4})""").find(doc.selectFirst(".year,.fecha,time,span.text-muted")?.text() ?: "")
            ?.groupValues?.get(1)?.toIntOrNull()

        val episodes = mutableListOf<Episode>()
        val ajaxUrl = doc.selectFirst("section.caplist,[data-ajax]")?.attr("data-ajax")
        val csrf    = doc.selectFirst("meta[name=csrf-token]")?.attr("content") ?: ""
        if (!ajaxUrl.isNullOrBlank() && csrf.isNotBlank()) {
            try {
                val json = org.json.JSONObject(app.post(ajaxUrl, data = mapOf("_token" to csrf),
                    headers = mapOf("X-Requested-With" to "XMLHttpRequest","Referer" to url)).text)
                (json.optJSONArray("eps") ?: json.optJSONArray("caps"))?.let { eps ->
                    for (i in 0 until eps.length()) {
                        val ep = eps.getJSONObject(i)
                        val epHref = fixUrlNull(ep.optString("url")) ?: continue
                        val num = ep.optInt("episodio", i + 1)
                        episodes.add(newEpisode(epHref) { name = "Episodio $num"; episode = num })
                    }
                }
            } catch (_: Exception) {}
        }
        if (episodes.isEmpty()) {
            doc.select("a[href*='/ver/'],a[href*='/episodio'],a[href*='/capitulo'],.episodes a").forEach { a ->
                val epHref = fixUrlNull(a.attr("href")) ?: return@forEach
                val num = Regex("""(\d+)""").find(a.text())?.groupValues?.get(1)?.toIntOrNull() ?: (episodes.size + 1)
                episodes.add(newEpisode(epHref) { name = a.text().trim().ifBlank { "Episodio $num" }; episode = num })
            }
        }
        val type = if (url.contains("/peliculas")) TvType.Movie else TvType.AsianDrama
        return newTvSeriesLoadResponse(title, url, type, episodes) {
            posterUrl = fixUrlNull(poster ?: ""); backgroundPosterUrl = posterUrl
            plot = plot; year = year; tags = tags
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val doc = app.get(data, headers = mapOf("Referer" to mainUrl)).document
        var token = ""
        doc.select("script").forEach { s ->
            Regex("""resource_token\s*=\s*['"]([^'"]+)['"]""").find(s.data())?.let { token = it.groupValues[1] }
        }
        val playerKey = doc.selectFirst(".player")?.attr("data-key") ?: "$mainUrl/reproductor?video="
        doc.select("button.play-video[data-player]").forEach { btn ->
            val enc = btn.attr("data-player")
            if (enc.isNotBlank()) extractEmbed("${playerKey}${enc}&token=$token", callback)
        }
        doc.select("iframe[src],iframe[data-src]").forEach { iframe ->
            val src = (iframe.attr("src").takeIf { it.isNotBlank() } ?: iframe.attr("data-src")).trim()
            if (src.startsWith("http")) extractEmbed(src, callback)
        }
        return true
    }

    private suspend fun extractEmbed(url: String, cb: (ExtractorLink) -> Unit) {
        try {
            val doc = app.get(url, headers = mapOf("Referer" to mainUrl,
                "User-Agent" to "Mozilla/5.0")).document
            doc.select("script").forEach { s ->
                Regex("""["'](https?://[^"']+\.(?:m3u8|mp4)[^"']*)["']""").findAll(s.data()).forEach {
                    val v = it.groupValues[1]
                    cb.invoke(newExtractorLink(name, name, v,
                        if (v.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO) {
                        referer = url; quality = Qualities.Unknown.value })
                }
            }
            doc.select("iframe[src]").forEach { iframe ->
                val src = iframe.attr("src").trim()
                if (src.startsWith("http") && src != url) extractEmbed(src, cb)
            }
        } catch (_: Exception) {}
    }
}
