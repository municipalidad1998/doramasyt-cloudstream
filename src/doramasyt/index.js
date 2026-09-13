import { getTmdbTitle, searchDorama, getEpisodeUrl } from "./search.js";
import { extractStreams } from "./extract.js";

export async function getStreams(tmdbId, mediaType, season, episode) {
  try {
    const title = await getTmdbTitle(tmdbId, mediaType);
    const detail = await searchDorama(title);
    const pageUrl = mediaType === "tv" && episode
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
