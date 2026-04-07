@file:Suppress("unused","UNUSED_PARAMETER","FunctionName","MemberVisibilityCanBePrivate")
@file:JvmName("MainAPIKt")
package com.lagradost.cloudstream3

import com.lagradost.nicehttp.Requests

// ── Enums ──────────────────────────────────────────────────────────
enum class TvType { Movie, TvSeries, AsianDrama, Anime, OVA, AnimeMovie, Torrent, Documentary, TalkShow, Others, Live, NSFW }
enum class ShowStatus { Completed, Ongoing }
enum class ProviderType { DirectLink, MetaProvider }
enum class VPNStatus { MightBeNeeded, Playback, None }
enum class SearchQuality { Cam, CamRip, HdCam, Telesync, WorkPrint, Telecine, HQ, HD, HDR, BluRay, DVD, SD, FHD, UHD, SQ, WebRip }

data class Score(val value: Double? = null)
data class NextAiring(val episode: Int = 0, val unixTime: Long = 0L)
data class MainPageData(val name: String, val data: String, val horizontalImages: Boolean = false)
data class HomePageList(val name: String, val list: List<SearchResponse>, val isHorizontalImages: Boolean = false)
data class HomePageResponse(val items: List<HomePageList>, val hasNext: Boolean = false)
data class MainPageRequest(val name: String, val data: String, val horizontalImages: Boolean = false)
data class SubtitleFile(val lang: String, val url: String, val headers: Map<String,String> = emptyMap())

// ── SearchResponse ─────────────────────────────────────────────────
open class SearchResponse {
    var name: String = ""; var url: String = ""; var apiName: String = ""
    var type: TvType? = null; var posterUrl: String? = null
    var id: Int? = null; var quality: SearchQuality? = null
    var posterHeaders: Map<String,String>? = null; var score: Score? = null
    constructor()
    constructor(name: String, url: String, apiName: String, type: TvType?, posterUrl: String?) {
        this.name=name; this.url=url; this.apiName=apiName; this.type=type; this.posterUrl=posterUrl
    }
}
open class MovieSearchResponse : SearchResponse() {
    var year: Int? = null
    constructor() : super()
    constructor(name: String, url: String, apiName: String, type: TvType?, posterUrl: String?) : super(name,url,apiName,type,posterUrl)
}
open class TvSeriesSearchResponse : SearchResponse() {
    var year: Int? = null; var episodes: Int? = null
    constructor() : super()
    constructor(name: String, url: String, apiName: String, type: TvType?, posterUrl: String?) : super(name,url,apiName,type,posterUrl)
}

// ── LoadResponse ───────────────────────────────────────────────────
open class LoadResponse {
    var name: String = ""; var url: String = ""; var apiName: String = ""
    var type: TvType = TvType.TvSeries; var posterUrl: String? = null
    var year: Int? = null; var plot: String? = null; var score: Score? = null
    var tags: List<String>? = null; var duration: Int? = null
    var backgroundPosterUrl: String? = null; var trailers: List<Any> = emptyList()
    var recommendations: List<SearchResponse>? = null; var actors: List<Any>? = null
    var comingSoon: Boolean = false; var syncData: Map<String,String> = emptyMap()
    var posterHeaders: Map<String,String>? = null; var contentRating: String? = null
    var uniqueUrl: String? = null; var logoUrl: String? = null; var rating: Int? = null
}

open class TvSeriesLoadResponse : LoadResponse() {
    var episodes: List<Episode> = emptyList()
    var showStatus: ShowStatus? = null
    var latestEpisodes: Map<Any,Any> = emptyMap()
    var nextAiring: NextAiring? = null
    var seasonNames: List<Any>? = null
    constructor() : super()
    constructor(name: String, url: String, apiName: String, type: TvType, episodes: List<Episode>) : super() {
        this.name=name; this.url=url; this.apiName=apiName; this.type=type; this.episodes=episodes
    }
}

open class MovieLoadResponse : LoadResponse() {
    var dataUrl: String = ""
    constructor() : super()
    constructor(name: String, url: String, apiName: String, type: TvType, dataUrl: String) : super() {
        this.name=name; this.url=url; this.apiName=apiName; this.type=type; this.dataUrl=dataUrl
    }
}

data class Episode(
    var data: String="", var name: String?=null, var season: Int?=null,
    var episode: Int?=null, var posterUrl: String?=null, var score: Score?=null,
    var description: String?=null, var date: Long?=null, var runTime: Int?=null
)

// ── MainAPI ────────────────────────────────────────────────────────
abstract class MainAPI {
    open var mainUrl = ""; open var name = ""; open val hasMainPage = false
    open var lang = ""; open val supportedTypes = setOf<TvType>()
    open val mainPage = listOf<MainPageData>()
    open val instantLinkLoading = false; open val hasDownloadSupport = false
    open val sequentialMainPage = false; open val sequentialMainPageDelay = 0L
    open val sequentialMainPageScrollDelay = 0L
    open val providerType = ProviderType.DirectLink; open val vpnStatus = VPNStatus.None
    open suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? = null
    open suspend fun search(query: String): List<SearchResponse>? = null
    open suspend fun search(query: String, page: Int): List<SearchResponse>? = null
    open suspend fun load(url: String): LoadResponse? = null
    open suspend fun loadLinks(data: String, isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (com.lagradost.cloudstream3.utils.ExtractorLink) -> Unit): Boolean = false
    open suspend fun quickSearch(query: String): List<SearchResponse>? = null
    open fun init() {}
}

// ── Top-level helpers (compiled as MainAPIKt) ──────────────────────
fun MainAPI.fixUrl(url: String): String =
    if (url.startsWith("http")) url else "$mainUrl${if (url.startsWith("/")) url else "/$url"}"
fun MainAPI.fixUrlNull(url: String?): String? = if (url.isNullOrBlank()) null else fixUrl(url)
fun mainPageOf(vararg pairs: Pair<String,String>): List<MainPageData> = pairs.map { MainPageData(it.first, it.second) }
fun mainPageOf(vararg data: MainPageData): List<MainPageData> = data.toList()
fun mainPage(name: String, data: String, horizontalImages: Boolean = false) = MainPageData(name, data, horizontalImages)
fun MainAPI.newEpisode(data: Any, block: Episode.() -> Unit = {}): Episode = Episode(data.toString()).apply(block)
fun MainAPI.newEpisode(data: String, block: Episode.() -> Unit = {}, addToIndex: Boolean = false): Episode = Episode(data).apply(block)
fun newHomePageResponse(list: List<HomePageList>, hasNext: Boolean? = null) = HomePageResponse(list, hasNext ?: false)
fun newHomePageResponse(name: String, list: List<SearchResponse>, hasNext: Boolean? = null) = HomePageResponse(listOf(HomePageList(name, list)), hasNext ?: false)
fun newHomePageResponse(request: MainPageRequest, list: List<SearchResponse>, hasNext: Boolean? = null) = HomePageResponse(listOf(HomePageList(request.name, list)), hasNext ?: false)
fun newHomePageResponse(pageList: HomePageList, hasNext: Boolean? = null) = HomePageResponse(listOf(pageList), hasNext ?: false)
fun MainAPI.newMovieSearchResponse(name: String, url: String, type: TvType, addToHome: Boolean = false, block: MovieSearchResponse.() -> Unit = {}): MovieSearchResponse =
    MovieSearchResponse(name, fixUrl(url), this.name, type, null).apply(block)
fun MainAPI.newTvSeriesSearchResponse(name: String, url: String, type: TvType, addToHome: Boolean = false, block: TvSeriesSearchResponse.() -> Unit = {}): TvSeriesSearchResponse =
    TvSeriesSearchResponse(name, fixUrl(url), this.name, type, null).apply(block)
suspend fun MainAPI.newTvSeriesLoadResponse(name: String, url: String, type: TvType, episodes: List<Episode>, block: suspend TvSeriesLoadResponse.() -> Unit = {}): TvSeriesLoadResponse =
    TvSeriesLoadResponse(name, fixUrl(url), this.name, type, episodes).also { block(it) }
suspend fun MainAPI.newMovieLoadResponse(name: String, url: String, type: TvType, dataUrl: String, block: suspend MovieLoadResponse.() -> Unit = {}): MovieLoadResponse =
    MovieLoadResponse(name, fixUrl(url), this.name, type, dataUrl).also { block(it) }
suspend fun MainAPI.newMovieLoadResponse(name: String, url: String, type: TvType, dataUrl: Any?, block: suspend MovieLoadResponse.() -> Unit = {}): MovieLoadResponse =
    MovieLoadResponse(name, fixUrl(url), this.name, type, dataUrl?.toString() ?: "").also { block(it) }
fun toRatingInt(rating: String?): Int? = rating?.toDoubleOrNull()?.times(10)?.toInt()
fun MainAPI.updateUrl(url: String): String = fixUrl(url)

