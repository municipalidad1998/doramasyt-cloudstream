import { request, clean } from "./http.js";

function decodeHtml(value) {
  return clean(String(value || ""))
    .replace(/&amp;/gi, "&")
    .replace(/&#39;/gi, "'")
    .replace(/&quot;/gi, '"')
    .replace(/&apos;/gi, "'")
    .replace(/&lt;/gi, "<")
    .replace(/&gt;/gi, ">");
}

function unique(values) {
  return [...new Set(values.map(v => clean(v)).filter(Boolean))];
}

export async function getTmdbTitleVariants(tmdbId, mediaType) {
  const type = mediaType === "tv" || mediaType === "series" ? "tv" : "movie";
  const html = await request("https://www.themoviedb.org/" + type + "/" + tmdbId);

  const og = html.match(/<meta[^>]+property=["']og:title["'][^>]+content=["']([^"']+)/i);
  const title = clean((og ? og[1] : ((html.match(/<title>([^<]+)/i) || [])[1] || ""))
    .replace(/\s*\|\s*TMDB.*$/i, ""));

  const originals = [];
  const patterns = [
    /["']original_(?:name|title)["']\s*:\s*["']([^"']+)["']/gi,
    /["']originalTitle["']\s*:\s*["']([^"']+)["']/gi,
    /["']originalName["']\s*:\s*["']([^"']+)["']/gi
  ];

  for (const pattern of patterns) {
    let match;
    while ((match = pattern.exec(html))) originals.push(decodeHtml(match[1]));
  }

  return unique([title, ...originals]).slice(0, 6);
}
