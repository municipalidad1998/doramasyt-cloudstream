@file:Suppress("unused","UNUSED_PARAMETER","FunctionName")
@file:JvmName("MainAPIKt")
package com.lagradost.cloudstream3

import com.lagradost.nicehttp.NiceResponse
import com.lagradost.nicehttp.Requests
import org.jsoup.nodes.Document

// ── Enums ──────────────────────────────────────────────────────────
enum class TvType {
    Movie, TvSeries, AsianDrama, Anime, OVA, AnimeMovie, Torrent,
    Documentary, TalkShow, Others, Live, NSFW
}
enum class ShowStatus { Completed, Ongoing }
enum class ProviderType { DirectLink, MetaProvider }
enum class VPNStatus { MightBeNeeded, Playback, None }
enum class SearchQuality { Cam, CamRip, HdCam, Telesync, WorkPrint, Telecine, HQ, HD, HDR, BluRay, DVD, SD, FHD, UHD, SQ, WebRip }

// ── Basic data classes ─────────────────────────────────────────────
data class Score(val value: Double? = null)
data class NextAiring(val episode: Int = 0, val unixTime: Long = 0L)
data class SeasonData(val season: Int = 0, val name: String? = null)

// ── SearchResponse ─────────────────────────────────────────────────
open class SearchResponse(
    open var name: String = "", open var url: String = "", open var apiName: String = "",
    open var type: TvType? = null, open var posterUrl: String? = null,
    open var id: Int? = null, open var quality: SearchQuality? = null,
    open var posterHeaders: Map<String,String>? = null, open var score: Score? = null
)
open class MovieSearchResponse(
    name: String="", url: String="", apiName: String="", type: TvType?=null,
    posterUrl: String?=null, var year: Int?=null, id: Int?=null,
    quality: SearchQuality?=null, posterHeaders: Map<String,String>?=null, score: Score?=null
) : SearchResponse(name, url, apiName, type, posterUrl, id, quality, posterHeaders, score)

open class TvSeriesSearchResponse(
    name: String="", url: String="", apiName: String="", type: TvType?=null,
    posterUrl: String?=null, var year: Int?=null, var episodes: Int?=null, id: Int?=null,
    quality: SearchQuality?=null, posterHeaders: Map<String,String>?=null, score: Score?=null
) : SearchResponse(name, url, apiName, type, posterUrl, id, quality, posterHeaders, score)

// ── LoadResponse ───────────────────────────────────────────────────
open class LoadResponse {
    open var name: String = ""; open var url: String = ""; open var apiName: String = ""
    open var type: TvType = TvType.TvSeries; open var posterUrl: String? = null
    open var year: Int? = null; open var plot: String? = null; open var score: Score? = null
    open var tags: List<String>? = null; open var duration: Int? = null
    open var backgroundPosterUrl: String? = null; open var trailers: List<Any> = emptyList()
    open var recommendations: List<SearchResponse>? = null; open var actors: List<Any>? = null
    open var comingSoon: Boolean = false; open var syncData: Map<String,String> = emptyMap()
    open var posterHeaders: Map<String,String>? = null; open var contentRating: String? = null
    open var uniqueUrl: String? = null; open var logoUrl: String? = null; open var rating: Int? = null
}
open class TvSeriesLoadResponse(
    override var name: String="", override var url: String="", override var apiName: String="",
    override var type: TvType=TvType.TvSeries, open var episodes: List<Episode>=emptyList(),
    override var posterUrl: String?=null, override var year: Int?=null, override var plot: String?=null,
    open var showStatus: ShowStatus?=null, override var score: Score?=null,
    override var tags: List<String>?=null, override var duration: Int?=null,
    override var recommendations: List<SearchResponse>?=null, override var actors: List<Any>?=null,
    override var trailers: List<Any>=emptyList(), override var comingSoon: Boolean=false,
    override var syncData: Map<String,String>=emptyMap(), open var latestEpisodes: Map<Any,Any>=emptyMap(),
    open var nextAiring: NextAiring?=null, open var seasonNames: List<Any>?=null,
    override var backgroundPosterUrl: String?=null, override var uniqueUrl: String?=null,
    override var logoUrl: String?=null, override var contentRating: String?=null
) : LoadResponse()

open class MovieLoadResponse(
    override var name: String="", override var url: String="", override var apiName: String="",
    override var type: TvType=TvType.Movie, open var dataUrl: String="",
    override var posterUrl: String?=null, override var year: Int?=null, override var plot: String?=null,
    override var score: Score?=null, override var tags: List<String>?=null,
    override var duration: Int?=null, override var recommendations: List<SearchResponse>?=null,
    override var actors: List<Any>?=null
) : LoadResponse()

// ── Episode ────────────────────────────────────────────────────────
data class Episode(
    var data: String="", var name: String?=null, var season: Int?=null,
    var episode: Int?=null, var posterUrl: String?=null, var score: Score?=null,
    var description: String?=null, var date: Long?=null, var runTime: Int?=null
)

// ── HomePage ───────────────────────────────────────────────────────
data class HomePageList(val name: String, val list: List<SearchResponse>, val isHorizontalImages: Boolean=false)
data class HomePageResponse(val items: List<HomePageList>, val hasNext: Boolean=false)
data class MainPageRequest(val name: String, val data: String, val horizontalImages: Boolean=false)
data class MainPageData(val name: String, val data: String, val horizontalImages: Boolean=false)
data class SubtitleFile(val lang: String, val url: String, val headers: Map<String,String> = emptyMap())

// ── MainAPI ────────────────────────────────────────────────────────
abstract class MainAPI {
    open var mainUrl = ""; open var name = ""; open val hasMainPage = false
    open var lang = ""; open val supportedTypes = setOf<TvType>()
    open val mainPage = listOf<MainPageData>()
    open val instantLinkLoading = false; open val hasDownloadSupport = false
    open val hasChromecastSupport = false; open val hasQuickSearch = false
    open val usesWebView = false; open val sequentialMainPage = false
    open val sequentialMainPageDelay = 0L; open val sequentialMainPageScrollDelay = 0L
    open val providerType = ProviderType.DirectLink; open val vpnStatus = VPNStatus.None
    open val canBeOverridden = false; open var sourcePlugin: String? = null
    open var storedCredentials: String? = null; open var lastHomepageRequest = 0L
    open val getMainPageTimeoutMs: Long? = null; open val loadTimeoutMs: Long? = null
    open val loadLinksTimeoutMs: Long? = null; open val searchTimeoutMs: Long? = null
    open val quickSearchTimeoutMs: Long? = null; open val supportedSyncNames = setOf<Any>()
    open val getGetMainPageTimeoutMs: Long? = null

    open suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? = null
    open suspend fun search(query: String): List<SearchResponse>? = null
    open suspend fun search(query: String, page: Int): List<SearchResponse>? = null
    open suspend fun load(url: String): LoadResponse? = null
    open suspend fun loadLinks(
        data: String, isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (com.lagradost.cloudstream3.utils.ExtractorLink) -> Unit
    ): Boolean = false
    open suspend fun quickSearch(query: String): List<SearchResponse>? = null
    open fun init() {}
}

// ── Top-level helpers → compiled as MainAPIKt ──────────────────────
fun MainAPI.fixUrl(url: String): String =
    if (url.startsWith("http")) url else "$mainUrl${if (url.startsWith("/")) url else "/$url"}"
fun MainAPI.fixUrlNull(url: String?): String? =
    if (url.isNullOrBlank()) null else fixUrl(url)
fun mainPageOf(vararg pairs: Pair<String,String>): List<MainPageData> =
    pairs.map { MainPageData(it.first, it.second) }
fun mainPageOf(vararg data: MainPageData): List<MainPageData> = data.toList()
fun mainPage(name: String, data: String, horizontalImages: Boolean = false) =
    MainPageData(name, data, horizontalImages)

fun MainAPI.newEpisode(data: Any, block: Episode.() -> Unit = {}): Episode =
    Episode(data.toString()).apply(block)
fun MainAPI.newEpisode(data: String, block: Episode.() -> Unit = {}, addToIndex: Boolean = false): Episode =
    Episode(data).apply(block)

fun newHomePageResponse(list: List<HomePageList>, hasNext: Boolean? = null) =
    HomePageResponse(list, hasNext ?: false)
fun newHomePageResponse(name: String, list: List<SearchResponse>, hasNext: Boolean? = null) =
    HomePageResponse(listOf(HomePageList(name, list)), hasNext ?: false)
fun newHomePageResponse(request: MainPageRequest, list: List<SearchResponse>, hasNext: Boolean? = null) =
    HomePageResponse(listOf(HomePageList(request.name, list)), hasNext ?: false)
fun newHomePageResponse(pageList: HomePageList, hasNext: Boolean? = null) =
    HomePageResponse(listOf(pageList), hasNext ?: false)

fun MainAPI.newMovieSearchResponse(
    name: String, url: String, type: TvType,
    addToHome: Boolean = false, block: MovieSearchResponse.() -> Unit = {}
): MovieSearchResponse = MovieSearchResponse(name, fixUrl(url), this.name, type).apply(block)

fun MainAPI.newTvSeriesSearchResponse(
    name: String, url: String, type: TvType,
    addToHome: Boolean = false, block: TvSeriesSearchResponse.() -> Unit = {}
): TvSeriesSearchResponse = TvSeriesSearchResponse(name, fixUrl(url), this.name, type).apply(block)

suspend fun MainAPI.newTvSeriesLoadResponse(
    name: String, url: String, type: TvType, episodes: List<Episode>,
    block: suspend TvSeriesLoadResponse.() -> Unit = {}
): TvSeriesLoadResponse = TvSeriesLoadResponse(name, fixUrl(url), this.name, type, episodes).also { block(it) }

suspend fun MainAPI.newMovieLoadResponse(
    name: String, url: String, type: TvType, dataUrl: String,
    block: suspend MovieLoadResponse.() -> Unit = {}
): MovieLoadResponse = MovieLoadResponse(name, fixUrl(url), this.name, type, dataUrl).also { block(it) }

suspend fun MainAPI.newMovieLoadResponse(
    name: String, url: String, type: TvType, dataUrl: Any?,
    block: suspend MovieLoadResponse.() -> Unit = {}
): MovieLoadResponse = MovieLoadResponse(name, fixUrl(url), this.name, type, dataUrl?.toString() ?: "").also { block(it) }

fun MainAPI.updateUrl(url: String): String = fixUrl(url)
fun toRatingInt(rating: String?): Int? = rating?.toDoubleOrNull()?.times(10)?.toInt()
