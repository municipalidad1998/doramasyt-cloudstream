import { getTmdbTitleVariants } from "./titles.js";
import { searchSoloLatino, findEpisodeUrl, buildEpisodeUrl } from "./search.js";
import { extractStreams } from "./extract.js";
import { request } from "./http.js";
function normalizeType(type) {
  const value = String(type || "").toLowerCase();
  return value === "movie" || value === "film" ? "movie" : "tv";
}
async function getStreams(tmdbId, mediaType, season = 1, episode) {
  try {
    const type = normalizeType(mediaType);
    const variants = await getTmdbTitleVariants(tmdbId, type);
    for (const title of variants) {
      try {
        const detail = await searchSoloLatino(title, type);
        let pageUrl = detail;
        if (type === "tv" && episode) {
          pageUrl = buildEpisodeUrl(detail, season, episode);
          try {
            const html = await request(detail);
            const scraped = findEpisodeUrl(html, episode, season);
            if (scraped) pageUrl = scraped;
          } catch (_) {}
        }
        const streams = await extractStreams(pageUrl);
        if (streams.length) return streams;
      } catch (error) {
        console.error("[SoloLatino] Variant '" + title + "': " + error.message);
      }
    }
    return [];
  } catch (error) {
    console.error("[SoloLatino] " + error.message);
    return [];
  }
}
module.exports = { getStreams };
