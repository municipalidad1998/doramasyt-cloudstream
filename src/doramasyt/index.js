import { getTmdbTitleVariants } from "./titles.js";
import { searchDorama, getEpisodeUrl } from "./search.js";
import { extractStreams } from "./extract.js";
import { prepareStreams } from "./playback.js";

function normalizeMediaType(mediaType) {
  const type = String(mediaType || "").toLowerCase();
  if (type === "movie" || type === "film") return "movie";
  if (type === "tv" || type === "series" || type === "show" || type === "tvseries") return "tv";
  return "tv";
}

async function findDoramaByVariants(variants) {
  let lastError = null;
  for (const title of variants) {
    try {
      return { detail: await searchDorama(title), title };
    } catch (error) {
      lastError = error;
      console.error("[DoramaYT] Search title '" + title + "': " + error.message);
    }
  }
  throw lastError || new Error("DoramaYT title not found");
}

async function getStreams(tmdbId, mediaType, season, episode) {
  try {
    const type = normalizeMediaType(mediaType);
    const variants = await getTmdbTitleVariants(tmdbId, type);
    if (!variants.length) throw new Error("TMDB title not found");

    const found = await findDoramaByVariants(variants);
    const detail = found.detail;
    const searchTitle = found.title;

    const pageUrl = type === "tv" && episode
      ? await getEpisodeUrl(detail, searchTitle, episode)
      : detail;

    const streams = await extractStreams(pageUrl);
    return prepareStreams(streams);
  } catch (error) {
    console.error("[DoramaYT] " + error.message);
    return [];
  }
}

module.exports = { getStreams };
