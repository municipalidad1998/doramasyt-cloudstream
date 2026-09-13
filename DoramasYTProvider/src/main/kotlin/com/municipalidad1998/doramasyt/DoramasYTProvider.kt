package com.municipalidad1998.doramasyt

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import java.util.EnumSet

class DoramasYTProvider : MainAPI() {
    override var mainUrl = "https://doramasyt.com"
    override var name = "DoramaYT"
    override var lang = "es"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.AsianDrama)

    private fun dubStatus(title: String) =
        if (title.contains("Latino", true) || title.contains("Castellano", true))
            DubStatus.Dubbed
        else DubStatus.Subbed

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val sections = listOf(
            "$mainUrl/emision" to "En emisión",
            "$mainUrl/doramas" to "Doramas",
            "$mainUrl/doramas?categoria=pelicula&genero=false&fecha=false&letra=false" to "Películas",
            "$mainUrl/doramas?categoria=live-action&genero=false&fecha=false&letra=false" to "Live Action"
        )

        val pages = sections.mapNotNull { (url, title) ->
            runCatching {
                val items = app.get(url, timeout = 120).document.select(".col-6").mapNotNull { item ->
                    val href = item.selectFirst("a")?.attr("href") ?: return@mapNotNull null
                    val itemTitle = item.selectFirst(".animedtls p")?.text()?.trim() ?: return@mapNotNull null
                    val poster = item.selectFirst(".anithumb img")?.attr("src")
                    newAnimeSearchResponse(itemTitle, fixUrl(href), TvType.AsianDrama) {
                        posterUrl = poster?.let(::fixUrl)
                        addDubStatus(dubStatus(itemTitle))
                    }
                }
                if (items.isEmpty()) null else HomePageList(title, items)
            }.getOrNull()
        }

        if (pages.isEmpty()) throw ErrorLoadingException()
        return HomePageResponse(pages)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return app.get("$mainUrl/buscar?q=${query.urlEncode()}", timeout = 120)
            .document.select(".col-6").mapNotNull { item ->
                val title = item.selectFirst(".animedtls p")?.text()?.trim() ?: return@mapNotNull null
                val href = item.selectFirst("a")?.attr("href") ?: return@mapNotNull null
                val image = item.selectFirst(".animes img")?.attr("src")
                AnimeSearchResponse(
                    title,
                    fixUrl(href),
                    name,
                    TvType.AsianDrama,
                    image?.let(::fixUrl),
                    null,
                    EnumSet.of(dubStatus(title))
                )
            }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, timeout = 120).document
        val title = doc.selectFirst("h1")?.text()?.trim() ?: throw ErrorLoadingException()
        val poster = doc.selectFirst("div.flimimg img.img1")?.attr("src")
        val type = doc.selectFirst("h4")?.text()?.trim().orEmpty()
        val description = doc.selectFirst("p.textComplete")?.text()?.replace("Ver menos", "")?.trim()
        val genres = doc.select(".nobel a").map { it.text().trim() }.filter { it.isNotEmpty() }

        val episodes = doc.select(".heromain .col-item").mapNotNull { item ->
            val href = item.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val epName = item.selectFirst(".dtlsflim p")?.text()?.trim() ?: href
            Episode(
                fixUrl(href),
                epName,
                posterUrl = item.selectFirst(".flimimg img.img1")?.attr("src")?.let(::fixUrl)
            )
        }

        val tvType = if (type.contains("Pelicula", true)) TvType.Movie else TvType.TvSeries
        return newAnimeLoadResponse(title, url, tvType) {
            posterUrl = poster?.let(::fixUrl)
            plot = description
            tags = genres
            addEpisodes(DubStatus.Subbed, episodes)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        app.get(data, timeout = 120).document.select("div.playother p").apmap { item ->
            val encoded = item.attr("data-player")
            if (encoded.isBlank()) return@apmap
            runCatching {
                val decoded = base64Decode(encoded)
                val target = decoded.replace("https://doramasyt.com/reproductor?url=", "")
                loadExtractor(target, mainUrl, subtitleCallback, callback)
            }
        }
        return true
    }
}
