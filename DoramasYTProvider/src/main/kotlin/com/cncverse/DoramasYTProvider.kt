package com.cncverse

import com.lagradost.cloudstream3.*
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

    private val placeholderImages = listOf("anime.png", "capblank.png", "capblank2.png")

    override val mainPage = mainPageOf(
        "$mainUrl/doramas"           to "Recientes",
        "$mainUrl/emision"           to "En Emision",
        "$mainUrl/peliculas"         to "Peliculas",
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

    private fun String.isPlaceholder(): Boolean =
        placeholderImages.any { this.contains(it) }

    private fun Element.realImageUrl(): String? {
        val img = this.selectFirst("img.lazy, img[data-src], img[data-img], img[data-original], img")
            ?: return null
        return listOf(
            img.attr("data-src"),
            img.attr("data-img"),
            img.attr("data-original"),
            img.attr("data-lazy-src"),
            img.attr("src")
        ).firstOrNull { it.isNotBlank() && !it.isPlaceholder() }
    }

    private fun Element.toSearchResponse(): SearchResponse? {
        val a     = this.selectFirst("a") ?: return null
        val href  = fixUrlNull(a.attr("href")) ?: return null
        val title = this.selectFirst("h3.titulo_cap, h3.title_cap, h3")
            ?.text()?.trim() ?: return null
        val poster = fixUrlNull(this.realImageUrl() ?: "")
        return newMovieSearchResponse(title, href, TvType.AsianDrama) { this.posterUrl = poster }
    }

    private fun buildUrl(baseUrl: String, page: Int) =
        if (page <= 1) baseUrl else "$baseUrl?p=$page"

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = app.get(buildUrl(request.data, page)).document
        val items = doc.select("li.ficha_efecto, div.ficha_efecto, .col-6")
            .mapNotNull { it.toSearchResponse() }
        return newHomePageResponse(listOf(HomePageList(request.name, items)))
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        try {
            val csrf = app.get(mainUrl).document
                .selectFirst("meta[name=csrf-token]")?.attr("content") ?: ""
            val ajax = app.post(
                "$mainUrl/buscar_ajax",
                data    = mapOf("_token" to csrf, "q" to query),
                headers = mapOf("Referer" to mainUrl, "X-Requested-With" to "XMLHttpRequest")
            )
            val arr = org.json.JSONArray(ajax.text)
            val results = mutableListOf<SearchResponse>()
            for (i in 0 until arr.length()) {
                val obj   = arr.getJSONObject(i)
                val title = obj.getString("nombre")
                val url2  = fixUrl(obj.getString("url"))
                val img   = obj.optString("imagen", "").let {
                    if (it.isNotBlank() && !it.isPlaceholder()) fixUrl(it) else null
                }
                results.add(newMovieSearchResponse(title, url2, TvType.AsianDrama) { posterUrl = img })
            }
            if (results.isNotEmpty()) return results
        } catch (_: Exception) {}

        return app.get("$mainUrl/buscar?q=$query").document
            .select("li.ficha_efecto, div.ficha_efecto")
            .mapNotNull { it.toSearchResponse() }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc   = app.get(url).document
        val title = doc.selectFirst("h1")?.text()?.trim() ?: "Unknown"

        // POSTER: OG image > data-src > src (sin placeholders)
        val ogImage = doc.selectFirst("meta[property='og:image']")
            ?.attr("content")?.takeIf { it.isNotBlank() && !it.isPlaceholder() }

        val imgEl = doc.selectFirst("img.lazy, img[data-src], img[data-img], img[data-original], img")
        val rawPoster = ogImage
            ?: imgEl?.attr("data-src")?.takeIf { it.isNotBlank() && !it.isPlaceholder() }
            ?: imgEl?.attr("data-img")?.takeIf  { it.isNotBlank() && !it.isPlaceholder() }
            ?: imgEl?.attr("data-original")?.takeIf { it.isNotBlank() && !it.isPlaceholder() }
            ?: imgEl?.attr("src")?.takeIf { it.isNotBlank() && !it.isPlaceholder() }
        val poster = fixUrlNull(rawPoster ?: "")

        // BACKGROUND: buscar imagen wide/landscape en meta og o twitter
        val bgPoster = listOf(
            doc.selectFirst("meta[property='og:image']")?.attr("content"),
            doc.selectFirst("meta[name='twitter:image']")?.attr("content"),
            doc.selectFirst("meta[property='og:image:secure_url']")?.attr("content"),
        ).firstOrNull { !it.isNullOrBlank() && !it.isPlaceholder() }
            ?.let { fixUrl(it) } ?: poster

        val plot = doc.selectFirst(".sinopsis, .description, .card-body p, p.text-muted")
            ?.text()?.trim() ?: ""
        val year = Regex("""(\d{4})""").find(
            doc.selectFirst("span.text-muted, .badge")?.text() ?: ""
        )?.groupValues?.get(1)?.toIntOrNull()
        val tags = doc.select("a[href*='/genero/']").map { it.text().trim() }

        val episodes  = mutableListOf<Episode>()
        val ajaxSec   = doc.selectFirst("section.caplist")
        val ajaxUrl   = ajaxSec?.attr("data-ajax")
        val csrf      = doc.selectFirst("meta[name=csrf-token]")?.attr("content") ?: ""
        var ajaxOk    = false

        if (!ajaxUrl.isNullOrBlank() && csrf.isNotBlank()) {
            try {
                val json = org.json.JSONObject(
                    app.post(ajaxUrl, data = mapOf("_token" to csrf),
                        headers = mapOf("Referer" to url, "X-Requested-With" to "XMLHttpRequest",
                            "Accept" to "application/json")).text
                )
                val eps = json.optJSONArray("eps")
                if (eps != null && eps.length() > 0) {
                    ajaxOk = true
                    for (i in 0 until eps.length()) {
                        val ep = eps.getJSONObject(i)
                        val epHref = fixUrlNull(ep.optString("url")) ?: continue
                        val num = ep.optInt("episodio", i + 1)
                        episodes.add(newEpisode(epHref) { name = "Episodio $num"; episode = num })
                    }
                    val paginateUrl = json.optString("paginate_url", "")
                    val perpage = json.optInt("perpage", 50)
                    val total   = json.optInt("total", eps.length())
                    if (paginateUrl.isNotBlank() && total > perpage) {
                        for (p in 2..(total + perpage - 1) / perpage) {
                            try {
                                val pJson = org.json.JSONObject(
                                    app.post(paginateUrl,
                                        data = mapOf("_token" to csrf, "p" to p.toString()),
                                        headers = mapOf("Referer" to url, "X-Requested-With" to "XMLHttpRequest",
                                            "Accept" to "application/json")).text
                                )
                                pJson.optJSONArray("caps")?.let { caps ->
                                    for (i in 0 until caps.length()) {
                                        val ep = caps.getJSONObject(i)
                                        val epHref = fixUrlNull(ep.optString("url")) ?: continue
                                        val num = ep.optInt("episodio", i + 1 + (p - 1) * perpage)
                                        episodes.add(newEpisode(epHref) { name = "Episodio $num"; episode = num })
                                    }
                                }
                            } catch (_: Exception) { break }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        if (!ajaxOk || episodes.isEmpty()) {
            var current  = fixUrlNull(doc.selectFirst("a[href*='/ver/']")?.attr("href"))
            var epNum    = 1
            val visited  = mutableSetOf<String>()
            while (current != null && epNum <= 200 && !visited.contains(current)) {
                visited.add(current)
                try {
                    val epDoc = app.get(current).document
                    episodes.add(newEpisode(current) { name = "Episodio $epNum"; episode = epNum })
                    val next = epDoc.select("a[href*='/ver/']")
                        .firstOrNull { it.text().contains("Siguiente", ignoreCase = true) }
                    val nextUrl = next?.let { fixUrlNull(it.attr("href")) }
                    current = if (nextUrl != null && nextUrl != current) { epNum++; nextUrl } else null
                } catch (_: Exception) { break }
            }
        }

        val type = if (episodes.size > 1) TvType.TvSeries else TvType.AsianDrama
        return newTvSeriesLoadResponse(title, url, type, episodes) {
            this.posterUrl = poster; this.backgroundPosterUrl = bgPoster
            this.plot = plot; this.year = year; this.tags = tags
        }
    }

    override suspend fun loadLinks(
        data: String, isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit
    ): Boolean {
        app.get(mainUrl)
        val doc = app.get(data).document
        var token = ""
        for (s in doc.select("script")) {
            Regex("""resource_token\s*=\s*['"]([^'"]+)['"]""").find(s.data())
                ?.let { token = it.groupValues[1] }
        }
        val playerKey = doc.selectFirst(".player")?.attr("data-key") ?: "$mainUrl/reproductor?video="
        for (btn in doc.select("button.play-video[data-player]")) {
            val enc  = btn.attr("data-player")
            val name2 = btn.text().trim()
            if (enc.isNotBlank()) {
                val pUrl = "${playerKey}${enc}&player=${java.net.URLEncoder.encode(name2,"UTF-8")}&token=$token"
                extractReproductor(pUrl, name2, callback)
        }
        }
        doc.select("a.btn[href*='gofile.io'],a.btn[href*='pixeldrain.com'],a.btn[href*='mega.nz']")
            .forEach { link ->
                val href = link.attr("href").trim()
                if (href.isNotBlank()) callback.invoke(
                    newExtractorLink("$name - ${link.text().trim()}", "$name - ${link.text().trim()}",
                        href, ExtractorLinkType.VIDEO) { referer = mainUrl; quality = Qualities.Unknown.value }
                )
            }
        return true
    }

    private suspend fun extractReproductor(url: String, sName: String, cb: (ExtractorLink) -> Unit) {
        try {
            val doc = app.get(url, headers = mapOf("Referer" to mainUrl,
                "User-Agent" to "Mozilla/5.0")).document
            for (iframe in doc.select("iframe")) {
                val src = iframe.attr("src").trim()
                if (src.startsWith("http")) extractVideoLink(src, sName, cb)
            }
            for (s in doc.select("script")) {
                Regex("""["'](https?://[^"']+\.(?:mp4|m3u8)[^"']*)["']""").findAll(s.data())
                    .forEach { m ->
                        val v = m.groupValues[1]
                        cb.invoke(newExtractorLink("$name - $sName", "$name - $sName", v,
                            if (v.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO) {
                            referer = mainUrl; quality = Qualities.Unknown.value
            })
                    }
            }
        } catch (_: Exception) {}
    }

    private suspend fun extractVideoLink(url: String, sName: String, cb: (ExtractorLink) -> Unit) {
        if (url.isBlank()) return
        val decoded = try { URLDecoder.decode(url, "UTF-8") } catch (_: Exception) { url }
        try {
            val doc = app.get(decoded, headers = mapOf("Referer" to mainUrl)).document
            for (s in doc.select("script")) {
                Regex("""["'](https?://[^"']+\.(?:mp4|m3u8)[^"']*)["']""").findAll(s.data())
                    .forEach { m ->
                        val v = m.groupValues[1]
                        cb.invoke(newExtractorLink("$name - $sName", "$name - $sName", v,
                            if (v.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO) {
                            referer = decoded; quality = Qualities.Unknown.value
            })
                    }
            }
            for (iframe in doc.select("iframe[src]")) {
                val src = iframe.attr("src").trim()
                if (src.startsWith("http") && src != decoded) extractVideoLink(src, sName, cb)
            }
        } catch (_: Exception) {}
    }

    private fun generateRandomString(length: Int = 10): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..length).map { chars.random() }.joinToString("")
    }
}
