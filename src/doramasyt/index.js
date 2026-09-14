import { getTmdbTitle, searchDorama, getEpisodeUrl } from "./search.js";
import { extractStreams } from "./extract.js";
import { prepareStreams } from "./playback.js";

function normalizeMediaType(mediaType) {
  const type = String(mediaType || "").toLowerCase();
  if (type === "movie" || type === "film") return "movie";
  if (type === "tv" || type === "series" || type === "show" || type === "tvseries") return "tv";
  return "tv";
}

async function getStreams(tmdbId, mediaType, season, episode) {
  try {
    const type = normalizeMediaType(mediaType);
    const title = await getTmdbTitle(tmdbId, type);
    const detail = await searchDorama(title);

    const pageUrl = type === "tv" && episode
      ? await getEpisodeUrl(detail, title, episode)
      : detail;

    const streams = await extractStreams(pageUrl);
    return prepareStreams(streams);
  } catch (error) {
    console.error("[DoramaYT] " + error.message);
    return [];
  }
}

module.exports = { getStreams };
