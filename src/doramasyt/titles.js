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

function extractTitle(html) {
  const og = html.match(/<meta[^>]+property=["']og:title["'][^>]+content=["']([^"']+)/i);
  return clean((og ? og[1] : ((html.match(/<title>([^<]+)/i) || [])[1] || ""))
    .replace(/\s*\|\s*TMDB.*$/i, ""));
}

function extractOriginals(html) {
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
  return originals;
}

export async function getTmdbTitleVariants(tmdbId, mediaType) {
  const type = mediaType === "tv" || mediaType === "series" ? "tv" : "movie";
  const base = "https://www.themoviedb.org/" + type + "/" + tmdbId;
  const urls = [base, base + "?language=es-ES", base + "?language=en-US"];
  const pages = await Promise.all(urls.map(async url => {
    try {
      return await request(url);
    } catch (_) {
      return "";
    }
  }));

  const titles = [];
  for (const html of pages) {
    if (!html) continue;
    const title = extractTitle(html);
    if (title) titles.push(title);
    titles.push(...extractOriginals(html));
  }

  return unique(titles).slice(0, 10);
}
