import { getTmdbTitleVariants } from "./titles.js";
import { getTmdbTitle, searchDorama, getEpisodeUrl } from "./search.js";
import { extractStreams } from "./extract.js";
import { prepareStreams } from "./playback.js";

function normalizeMediaType(mediaType) {
  const type = String(mediaType || "").toLowerCase();
  if (type === "movie" || type === "film") return "movie";
  if (type === "tv" || type === "series" || type === "show" || type === "tvseries") return "tv";
  return "tv";
}

function uniqueTitles(values) {
  return [...new Set((Array.isArray(values) ? values : [])
    .map(value => String(value || "").trim())
    .filter(Boolean))];
}

async function getTitleVariantsSafe(tmdbId, type) {
  const variants = [];

  try {
    variants.push(...await getTmdbTitleVariants(tmdbId, type));
  } catch (error) {
    console.error("[DoramaYT] TMDB variants: " + error.message);
  }

  // Keep the original title resolver as a fallback so adding title variants
  // can never break the normal provider search.
  try {
    variants.push(await getTmdbTitle(tmdbId, type));
  } catch (error) {
    console.error("[DoramaYT] TMDB fallback title: " + error.message);
  }

  return uniqueTitles(variants).slice(0, 8);
}

async function tryVariant(variant, type, season, episode) {
  try {
    const detail = await searchDorama(variant);
    const pageUrl = type === "tv" && episode
      ? await getEpisodeUrl(detail, variant, episode)
      : detail;

    const streams = prepareStreams(await extractStreams(pageUrl));
    if (streams.length) {
      console.log("[DoramaYT] Streams found using title: " + variant);
      return streams;
    }
  } catch (error) {
    console.error("[DoramaYT] Variant '" + variant + "': " + error.message);
  }
  return [];
}

async function getStreams(tmdbId, mediaType, season, episode) {
  try {
    const type = normalizeMediaType(mediaType);
    const variants = await getTitleVariantsSafe(tmdbId, type);
    if (!variants.length) throw new Error("TMDB title not found");

    // Try every useful title sequentially. This is intentionally tolerant of
    // a small delay because the goal is to find a real playable stream.
    for (const variant of variants) {
      const streams = await tryVariant(variant, type, season, episode);
      if (streams.length) return streams;
    }

    console.error("[DoramaYT] No streams found for title variants: " + variants.join(" | "));
    return [];
  } catch (error) {
    console.error("[DoramaYT] " + error.message);
    return [];
  }
}

module.exports = { getStreams };
