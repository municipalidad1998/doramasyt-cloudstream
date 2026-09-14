import { BASE_URL, request, clean, absoluteUrl } from "./http.js";
function normalize(value) {
  return clean(value).toLowerCase().normalize("NFD").replace(/[\u0300-\u036f]/g, "").replace(/[^a-z0-9]+/g, " ").trim();
}
function slug(value) { return normalize(value).replace(/\s+/g, "-"); }
function titleMatch(text, title) {
  const a = normalize(text), b = normalize(title);
  if (!a || !b) return false;
  if (a.includes(b) || b.includes(a)) return true;
  const words = b.split(" ").filter(w => w.length > 2);
  const hits = words.filter(w => a.includes(w)).length;
  return words.length > 1 && hits >= Math.max(1, words.length - 1);
}
function parseAnchors(html) {
  const result = [];
  const re = /<a\b[^>]*href=["']([^"']+)["'][^>]*>([\s\S]*?)<\/a>/gi;
  let m;
  while (m = re.exec(html)) result.push({ href: absoluteUrl(m[1]), text: clean(m[2]) });
  return result;
}
export async function searchSoloLatino(title, mediaType = "tv") {
  const query = clean(title);
  const urls = [
    BASE_URL + "/?s=" + encodeURIComponent(query),
    BASE_URL + "/buscar?s=" + encodeURIComponent(query),
    BASE_URL + "/buscar/?s=" + encodeURIComponent(query),
    BASE_URL + "/buscar?query=" + encodeURIComponent(query),
    BASE_URL + "/buscar?q=" + encodeURIComponent(query)
  ];
  for (const url of urls) {
    try {
      const html = await request(url);
      const candidates = parseAnchors(html).filter(a => a.href.startsWith(BASE_URL + "/") && titleMatch(a.text, query) && !/\/temporada-\d+\/episodio-\d+/i.test(a.href));
      const preferred = mediaType === "movie" ? candidates.find(a => /\/pelicula\//i.test(a.href)) : candidates.find(a => /\/(?:serie|dorama|anime)\//i.test(a.href));
      if (preferred) return preferred.href;
      if (candidates[0]) return candidates[0].href;
    } catch (_) {}
  }
  const slugValue = slug(query);
  const paths = mediaType === "movie" ? ["/pelicula/" + slugValue, "/serie/" + slugValue] : ["/serie/" + slugValue, "/dorama/" + slugValue, "/anime/" + slugValue];
  for (const path of paths) {
    try {
      const url = BASE_URL + path;
      const html = await request(url);
      if (/<h1[^>]*>[\s\S]*<\/h1>/i.test(html) || /og:title/i.test(html)) return url;
    } catch (_) {}
  }
  throw new Error("SoloLatino title not found: " + title);
}
export function findEpisodeUrl(html, episode, season = 1) {
  const wanted = Number(episode), wantedSeason = Number(season || 1);
  if (!wanted) return "";
  const re = /<a\b[^>]*href=["']([^"']+)["'][^>]*>([\s\S]*?)<\/a>/gi;
  let m;
  while (m = re.exec(html)) {
    const text = clean(m[2] + " " + m[1]);
    const seasonMatch = text.match(/T\s*0*(\d+)\s*(?:E\s*0*\d+)?/i);
    const ep = text.match(/(?:T\s*\d+\s*)?E\s*0*(\d+)|(?:episodio|capitulo|capítulo|episode|ep)\s*0*(\d+)/i);
    if (ep && Number(ep[1] || ep[2]) === wanted && (!seasonMatch || Number(seasonMatch[1]) === wantedSeason)) return absoluteUrl(m[1]);
  }
  return "";
}
export function buildEpisodeUrl(detailUrl, season = 1, episode = 1) {
  if (!detailUrl || !episode || /\/temporada-\d+\/episodio-\d+\/?$/i.test(detailUrl)) return detailUrl;
  return detailUrl.replace(/\/+$/, "") + "/temporada-" + Number(season || 1) + "/episodio-" + Number(episode);
}
