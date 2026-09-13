package com.municipalidad1998.doramasyt

import org.jsoup.Jsoup

/**
 * Minimal CloudStream-style scraper core.
 *
 * This class deliberately keeps the network/parser layer independent from a
 * particular CloudStream fork so it can be wired to the host's current
 * Provider API without coupling this repository to undocumented internals.
 */
class DoramaYTScraper {
    companion object {
        const val MAIN_URL = "https://www.doramasyt.com"
    }

    data class SearchResult(
        val title: String,
        val url: String
    )

    fun parseSearch(html: String): List<SearchResult> {
        val doc = Jsoup.parse(html)
        return doc.select("a[href]").mapNotNull { a ->
            val href = a.absUrl("href").ifBlank { a.attr("href") }
            val title = a.text().trim()
            if (href.isBlank() || title.isBlank()) null else SearchResult(title, href)
        }.distinctBy { it.url }
    }
}
