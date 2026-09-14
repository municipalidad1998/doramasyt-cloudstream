import { request, clean } from "./http.js";
function unique(values) { return [...new Set(values.map(clean).filter(Boolean))]; }
function extractTitle(html) {
  const og = html.match(/<meta[^>]+property=["']og:title["'][^>]+content=["']([^"']+)/i);
  return clean((og ? og[1] : (html.match(/<title>([^<]+)/i) || [])[1] || "")
    .replace(/\s*\|\s*TMDB.*$/i, ""));
}
export async function getTmdbTitleVariants(tmdbId, mediaType) {
  const type = mediaType === "movie" ? "movie" : "tv";
  const urls = [
    `https://www.themoviedb.org/${type}/${tmdbId}`,
    `https://www.themoviedb.org/${type}/${tmdbId}?language=es-ES`,
    `https://www.themoviedb.org/${type}/${tmdbId}?language=en-US`
  ];
  const titles = [];
  for (const url of urls) {
    try { titles.push(extractTitle(await request(url))); } catch (_) {}
  }
  return unique(titles).slice(0, 8);
}
