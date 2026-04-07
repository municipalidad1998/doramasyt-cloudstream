package com.cncverse

import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.nodes.Element

class DoramasiaProvider : MainAPI() {

    override var mainUrl  = "https://doramasia.com"
    override var name     = "Doramasia"
    override val hasMainPage = true
    override var lang     = "es"
    override val supportedTypes = setOf(TvType.AsianDrama, TvType.TvSeries)

    private val placeholders = listOf("anime.png","capblank","no-image","placeholder","default","noposter","noimg")

    override val mainPage = mainPageOf(
        "$mainUrl/doramas"        to "Doramas",
        "$mainUrl/genero/k-drama" to "K-Drama",
        "$mainUrl/genero/c-drama" to "C-Drama",
        "$mainUrl/genero/j-drama" to "J-Drama",
        "$mainUrl/genero/romance" to "Romance",
        "$mainUrl/genero/comedia" to "Comedia",
        "$mainUrl/genero/accion"  to "Acción",
        "$mainUrl/genero/drama"   to "Drama",
    )

    private fun String.isPlaceholder() = placeholders.any { this.contains(it) }

    private fun Element.realPoster(): String? {
        return listOf(
            this.selectFirst("img[src*='image.tmdb.org']")?.attr("src"),
            this.selectFirst("img[data-src*='image.tmdb.org']")?.attr("data-src"),
            this.selectFirst("img.lazy")?.attr("data-src"),
            this.selectFirst("img[data-src]")?.attr("data-src"),
            this.selectFirst("img[data-img]")?.attr("data-img"),
            this.selectFirst("img[data-original]")?.attr("data-original"),
            this.selectFirst("img:not([src^='data:'])")?.attr("src"),
        ).firstOrNull { !it.isNullOrBlank() && !it.isPlaceholder() }
    }

    private fun Element.toSearchResponse(): SearchResponse? {
        val a     = this.selectFirst("a[href]") ?: return null
        val href  = fixUrlNull(a.attr("href")) ?: return null
        val title = this.selectFirst("h2, h3, .titulo, .title, .nombre")
            ?.text()?.trim() ?: a.attr("title").trim().takeIf { it.isNotBlank() } ?: return null
        return newMovieSearchResponse(title, href, TvType.AsianDrama) {
            posterUrl = fixUrlNull(this@toSearchResponse.realPoster() ?: "")
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page <= 1) request.data else "${request.data}?p=$page"
        val doc = app.get(url).document
        val items = doc.select("li.ficha_efecto, .anime-item, article.post, .item, li[class*='anime'], li[class*='serie']")
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
            .select("li.ficha_efecto, .anime-item, article").mapNotNull { it.toSearchResponse() }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc   = app.get(url).document
        val title = doc.selectFirst("h1")?.text()?.trim() ?: "Sin título"
        val poster = doc.selectFirst("meta[property='og:image']")?.attr("content")?.takeIf { !it.isPlaceholder() }
            ?: doc.realPoster()
        val plot = doc.selectFirst(".sinopsis, .description, p.text-muted")?.text()?.trim() ?: ""
        val tags = doc.select("a[href*='/genero/']").map { it.text().trim() }
        val year = Regex("""(\d{4})""").find(doc.selectFirst(".year,.fecha,span.text-muted,.badge")?.text() ?: "")
            ?.groupValues?.get(1)?.toIntOrNull()
        val episodes = mutableListOf<Episode>()
        val ajaxUrl = doc.selectFirst("section.caplist,[data-ajax]")?.attr("data-ajax")
        val csrf    = doc.selectFirst("meta[name=csrf-token]")?.attr("content") ?: ""
        if (!ajaxUrl.isNullOrBlank() && csrf.isNotBlank()) {
            try {
                val json = org.json.JSONObject(app.post(ajaxUrl, data = mapOf("_token" to csrf),
                    headers = mapOf("X-Requested-With" to "XMLHttpRequest","Referer" to url)).text)
                json.optJSONArray("eps")?.let { eps ->
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
            for (a in doc.select("a[href*='/ver/'],a.episode-link,.episodes a")) {
                val epHref = fixUrlNull(a.attr("href")) ?: continue
                val num = Regex("""(\d+)""").find(a.text())?.groupValues?.get(1)?.toIntOrNull() ?: (episodes.size + 1)
                episodes.add(newEpisode(epHref) { name = "Episodio $num"; episode = num })
            }
        }
        return newTvSeriesLoadResponse(title, url, TvType.AsianDrama, episodes) {
            posterUrl = fixUrlNull(poster ?: ""); this.backgroundPosterUrl = posterUrl
            this.plot = plot; this.year = year; this.tags = tags
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val doc = app.get(data, headers = mapOf("Referer" to mainUrl)).document
        for (iframe in doc.select("iframe[src],iframe[data-src]")) {
            val src = (iframe.attr("src").takeIf { it.isNotBlank() } ?: iframe.attr("data-src")).trim()
            if (src.startsWith("http")) extractVideo(src, callback)
        }
        for (s in doc.select("script")) {
            Regex("""["'](https?://[^"']+\.(?:m3u8|mp4)[^"']*)["']""").findAll(s.data()).forEach {
                val v = it.groupValues[1]
                callback.invoke(newExtractorLink(name, name, v,
                    if (v.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO) {
                    referer = mainUrl; quality = Qualities.Unknown.value })
        }
        }
        return true
    }

    private suspend fun extractVideo(url: String, cb: (ExtractorLink) -> Unit) {
        try {
            val doc = app.get(url, headers = mapOf("Referer" to mainUrl)).document
            for (s in doc.select("script")) {
                Regex("""["'](https?://[^"']+\.(?:m3u8|mp4)[^"']*)["']""").findAll(s.data()).forEach {
                    val v = it.groupValues[1]
                    cb.invoke(newExtractorLink(name, name, v,
                        if (v.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO) {
                        referer = url; quality = Qualities.Unknown.value })
            }
            }
            for (iframe in doc.select("iframe[src]")) {
                val src = iframe.attr("src").trim()
                if (src.startsWith("http") && src != url) extractVideo(src, cb)
            }
        } catch (_: Exception) {}
    }
}

class DoramasiaPlugin : Plugin() {
    override fun load(context: android.content.Context) {
        registerMainAPI(com.cncverse.DoramasiaProvider())
    }
}
