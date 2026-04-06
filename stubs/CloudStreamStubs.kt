@file:Suppress("UNUSED_PARAMETER", "MemberVisibilityCanBePrivate", "unused", "RedundantVisibilityModifier")
package com.lagradost.cloudstream3

import org.jsoup.nodes.Document

enum class TvType { Movie, TvSeries, AsianDrama, Anime, OVA, AnimeMovie, Torrent, Documentary, Others, Live }

open class SearchResponse(open val name: String, open val url: String, open val apiName: String = "",
    open val type: TvType?, open var posterUrl: String?, open var id: Int? = null)

open class MovieSearchResponse(name: String, url: String, apiName: String = "", type: TvType?,
    posterUrl: String? = null, var year: Int? = null) : SearchResponse(name, url, apiName, type, posterUrl)

open class TvSeriesSearchResponse(name: String, url: String, apiName: String = "", type: TvType?,
    posterUrl: String? = null, var year: Int? = null) : SearchResponse(name, url, apiName, type, posterUrl)

open class LoadResponse {
    open var name: String = ""; open var url: String = ""; open var apiName: String = ""
    open var type: TvType = TvType.TvSeries; open var posterUrl: String? = null
    open var year: Int? = null; open var plot: String? = null; open var tags: List<String>? = null
    open var backgroundPosterUrl: String? = null
}

open class TvSeriesLoadResponse(override var name: String, override var url: String,
    override var apiName: String = "", override var type: TvType,
    open var episodes: List<Episode>, override var posterUrl: String? = null,
    override var year: Int? = null, override var plot: String? = null,
    override var tags: List<String>? = null, override var backgroundPosterUrl: String? = null) : LoadResponse()

open class MovieLoadResponse(override var name: String, override var url: String,
    override var apiName: String = "", override var type: TvType, open var dataUrl: String,
    override var posterUrl: String? = null, override var year: Int? = null,
    override var plot: String? = null) : LoadResponse()

data class Episode(val data: String, var name: String? = null, var season: Int? = null,
    var episode: Int? = null, var posterUrl: String? = null)

data class HomePageList(val name: String, val list: List<SearchResponse>, val isHorizontalImages: Boolean = false)
data class HomePageResponse(val items: List<HomePageList>, val hasNext: Boolean = false)
data class MainPageRequest(val name: String, val data: String, val page: Int)
data class SubtitleFile(val lang: String, val url: String)

class NiceResponse { val text: String = ""; val document: Document = Document("") }

object app {
    suspend fun get(url: String, headers: Map<String, String> = emptyMap(), referer: String? = null,
        params: Map<String, String> = emptyMap()): NiceResponse = NiceResponse()
    suspend fun post(url: String, data: Map<String, String> = emptyMap(),
        headers: Map<String, String> = emptyMap(), referer: String? = null): NiceResponse = NiceResponse()
}

abstract class MainAPI {
    open var mainUrl = ""; open var name = ""; open val hasMainPage = false
    open var lang = ""; open val supportedTypes = setOf<TvType>()
    open val mainPage = listOf<Pair<String, String>>()
    suspend fun app_get(url: String, headers: Map<String, String> = emptyMap()) = app.get(url, headers)
    fun fixUrl(url: String): String = if (url.startsWith("http")) url else "$mainUrl$url"
    fun fixUrlNull(url: String?): String? = url?.let { if (it.isBlank()) null else fixUrl(it) }
    open suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? = null
    open suspend fun search(query: String): List<SearchResponse>? = null
    open suspend fun load(url: String): LoadResponse? = null
    open suspend fun loadLinks(data: String, isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit, callback: (com.lagradost.cloudstream3.utils.ExtractorLink) -> Unit): Boolean = false
}

fun mainPageOf(vararg pairs: Pair<String, String>) = pairs.toList()

fun newMovieSearchResponse(name: String, url: String, type: TvType,
    block: MovieSearchResponse.() -> Unit = {}): MovieSearchResponse =
    MovieSearchResponse(name, url, type = type).apply(block)

fun newTvSeriesLoadResponse(name: String, url: String, type: TvType, episodes: List<Episode>,
    block: TvSeriesLoadResponse.() -> Unit = {}): TvSeriesLoadResponse =
    TvSeriesLoadResponse(name, url, type = type, episodes = episodes).apply(block)

fun newEpisode(url: String, block: Episode.() -> Unit = {}): Episode =
    Episode(url).apply(block)

fun newHomePageResponse(list: List<HomePageList>, hasNext: Boolean = false) = HomePageResponse(list, hasNext)
