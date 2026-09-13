import { getTmdbTitle, searchDorama, getEpisodeUrl } from "./search.js";
import { extractStreams } from "./extract.js";

function normalizeMediaType(mediaType) {
  const type = String(mediaType || "").toLowerCase();
  if (type === "movie" || type === "film") return "movie";
  if (type === "tv" || type === "series" || type === "show" || type === "tvseries") return "tv";
  return type === "movie" ? "movie" : "tv";
}

export async function getStreams(tmdbId, mediaType, season, episode) {
  try {
    const type = normalizeMediaType(mediaType);
    const title = await getTmdbTitle(tmdbId, type);
    const detail = await searchDorama(title);

    // Nuvio represents both TV series and seasons as mediaType="tv".
    // Movies do not have an episode, so extract directly from the movie detail page.
    const pageUrl = type === "tv" && episode
      ? await getEpisodeUrl(detail, title, episode)
      : detail;

    const streams = await extractStreams(pageUrl);
    return streams;
  } catch (error) {
    console.error("[DoramaYT] " + error.message);
    return [];
  }
}

module.exports = { getStreams };
