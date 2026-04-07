@file:Suppress("UNUSED_PARAMETER", "unused", "FunctionName")
@file:JvmName("MainAPIKt")
package com.lagradost.cloudstream3

import org.jsoup.nodes.Document
import kotlin.coroutines.Continuation

// ── Enums ──────────────────────────────────────────────────────────────
enum class TvType {
    Movie, TvSeries, AsianDrama, Anime, OVA, AnimeMovie, Torrent,
    Documentary, TalkShow, Others, Live, NSFW
}

// ── Data classes ──────────────────────────────────────────────────────
open class SearchResponse(
    open val name: String = "", open val url: String = "",
    open val apiName: String = "", open val type: TvType? = null,
    open var posterUrl: String? = null
)

open class MovieSearchResponse(
    name: String = "", url: String = "", apiName: String = "", type: TvType? = null,
    posterUrl: String? = null
) : SearchResponse(name, url, apiName, type, posterUrl) {
    fun setPosterUrl(v: String?) { posterUrl = v }
}

open class TvSeriesSearchResponse(
    name: String = "", url: String = "", apiName: String = "", type: TvType? = null,
    posterUrl: String? = null
) : SearchResponse(name, url, apiName, type, posterUrl)

open class LoadResponse {
    open var name: String = ""; open var url: String = ""; open var apiName: String = ""
    open var type: TvType = TvType.TvSeries; open var posterUrl: String? = null
    open var year: Int? = null; open var plot: String? = null; open var tags: List<String>? = null
    open var backgroundPosterUrl: String? = null
}

open class TvSeriesLoadResponse(
    override var name: String = "", override var url: String = "",
    override var apiName: String = "", override var type: TvType = TvType.TvSeries,
    open var episodes: List<Episode> = emptyList()
) : LoadResponse() {
    fun setPosterUrl(v: String?) { posterUrl = v }
    fun setBackgroundPosterUrl(v: String?) { backgroundPosterUrl = v }
    fun setPlot(v: String?) { plot = v }
    fun setYear(v: Int?) { year = v }
    fun setTags(v: List<String>?) { tags = v }
}

open class MovieLoadResponse(
    override var name: String = "", override var url: String = "",
    override var apiName: String = "", override var type: TvType = TvType.Movie,
    open var dataUrl: String = ""
) : LoadResponse()

data class Episode(
    val data: String = "",
    var name: String? = null,
    var season: Int? = null,
    var episode: Int? = null,
    var posterUrl: String? = null
) {
    fun setName(v: String?) { name = v }
    fun setEpisode(v: Int?) { episode = v }
    fun setSeason(v: Int?) { season = v }
}

data class HomePageList(val name: String, val list: List<SearchResponse>, val isHorizontalImages: Boolean = false)
data class HomePageResponse(val items: List<HomePageList>, val hasNext: Boolean = false)
data class MainPageRequest(val name: String, val data: String, val page: Int) {
    fun getName() = name
    fun getData() = data
}
data class SubtitleFile(val lang: String, val url: String)

// NiceResponse en el mismo paquete (stub compatible)
open class NiceResponse {
    open val text: String get() = ""
    open val document: Document get() = Document("")
    fun getText() = text
    fun getDocument() = document
}

// ── app object (stub de nicehttp.Requests) ───────────────────────────
object app {
    suspend fun get(url: String, headers: Map<String, String> = emptyMap(),
        referer: String? = null, params: Map<String, String> = emptyMap()
    ): NiceResponse = NiceResponse()
    
    suspend fun post(url: String, data: Map<String, String> = emptyMap(),
        headers: Map<String, String> = emptyMap(), referer: String? = null
    ): NiceResponse = NiceResponse()
}

// ── MainAPI ───────────────────────────────────────────────────────────
abstract class MainAPI {
    open var mainUrl = ""; open var name = ""; open val hasMainPage = false
    open var lang = ""; open val supportedTypes = setOf<TvType>()
    open val mainPage = listOf<Pair<String, String>>()
    
    open suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? = null
    open suspend fun search(query: String): List<SearchResponse>? = null
    open suspend fun load(url: String): LoadResponse? = null
    open suspend fun loadLinks(data: String, isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (com.lagradost.cloudstream3.utils.ExtractorLink) -> Unit
    ): Boolean = false
}

// ── Top-level functions (compiled as MainAPIKt) ───────────────────────
fun MainAPI.fixUrl(url: String): String =
    if (url.startsWith("http")) url else "$mainUrl${if (url.startsWith("/")) url else "/$url"}"

fun MainAPI.fixUrlNull(url: String?): String? =
    if (url.isNullOrBlank()) null else fixUrl(url)

fun mainPageOf(vararg pairs: Pair<String, String>): List<Pair<String, String>> = pairs.toList()

fun MainAPI.newEpisode(data: Any, block: Episode.() -> Unit = {}): Episode =
    Episode(data.toString()).apply(block)

fun newHomePageResponse(
    list: List<HomePageList>,
    hasNext: Boolean = false
): HomePageResponse = HomePageResponse(list, hasNext)

fun MainAPI.newMovieSearchResponse(
    name: String, url: String, type: TvType,
    addToHome: Boolean = false,
    block: MovieSearchResponse.() -> Unit = {}
): MovieSearchResponse = MovieSearchResponse(name, fixUrl(url), this.name, type).apply(block)

suspend fun MainAPI.newTvSeriesLoadResponse(
    name: String, url: String, type: TvType, episodes: List<Episode>,
    block: suspend TvSeriesLoadResponse.() -> Unit = {}
): TvSeriesLoadResponse = TvSeriesLoadResponse(name, fixUrl(url), this.name, type, episodes).also {
    block(it)
}
