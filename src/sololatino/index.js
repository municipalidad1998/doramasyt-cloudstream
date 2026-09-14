import { getTmdbTitleVariants } from "./titles.js";
import { searchSoloLatino, findEpisodeUrl } from "./search.js";
import { extractStreams } from "./extract.js";
import { request } from "./http.js";

function normalizeType(type) {
  const value = String(type || "").toLowerCase();
  return value === "movie" || value === "film" ? "movie" : "tv";
}
async function getStreams(tmdbId, mediaType, season, episode) {
  try {
    const type = normalizeType(mediaType);
    const variants = await getTmdbTitleVariants(tmdbId, type);
    for (const title of variants) {
      try {
        const detail = await searchSoloLatino(title);
        let pageUrl = detail;
        if (type === "tv" && episode) {
          const html = await request(detail);
          const epUrl = findEpisodeUrl(html, episode);
          if (epUrl) pageUrl = epUrl;
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
