import { BASE_URL, request, clean, absoluteUrl } from "./http.js";
function normalize(value) {
  return clean(value).toLowerCase().normalize("NFD").replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-z0-9]+/g, " ").trim();
}
function slug(value) {
  return normalize(value).replace(/\s+/g, "-");
}
function titleMatch(text, title) {
  const a = normalize(text), b = normalize(title);
  if (!a || !b) return false;
  if (a.includes(b) || b.includes(a)) return true;
  const words = b.split(" ").filter(w => w.length > 2);
  const hits = words.filter(w => a.includes(w)).length;
  return words.length > 1 && hits >= Math.max(2, words.length - 1);
}
function parseAnchors(html) {
  const result = [];
  const re = /<a\b[^>]*href=["']([^"']+)["'][^>]*>([\s\S]*?)<\/a>/gi;
  let m;
  while (m = re.exec(html)) result.push({ href: absoluteUrl(m[1]), text: clean(m[2]) });
  return result;
}
export async function searchSoloLatino(title) {
  const queries = [...new Set([title, title.replace(/[:.!?]/g, " ")].map(clean).filter(Boolean))];
  for (const query of queries) {
    const direct = BASE_URL + "/serie/" + slug(query);
    try {
      const html = await request(direct);
      if (/<h1[^>]*>[\s\S]*<\/h1>/i.test(html) && titleMatch(html, query)) return direct;
    } catch (_) {}
    for (const url of [
      BASE_URL + "/?s=" + encodeURIComponent(query),
      BASE_URL + "/buscar?query=" + encodeURIComponent(query),
      BASE_URL + "/buscar?q=" + encodeURIComponent(query)
    ]) {
      try {
        const html = await request(url);
        const candidates = parseAnchors(html).filter(a =>
          a.href.startsWith(BASE_URL + "/") && titleMatch(a.text, query)
        );
        const content = candidates.find(a => /\/(?:pelicula|serie|anime|dorama)\//i.test(a.href)) || candidates[0];
        if (content) return content.href;
      } catch (_) {}
    }
  }
  throw new Error("SoloLatino title not found: " + title);
}
export function findEpisodeUrl(html, episode) {
  const wanted = Number(episode);
  if (!wanted) return "";
  const re = /<a\b[^>]*href=["']([^"']+)["'][^>]*>([\s\S]*?)<\/a>/gi;
  let m;
  while (m = re.exec(html)) {
    const text = clean(m[2] + " " + m[1]);
    const ep = text.match(/(?:T\s*\d+\s*)?E\s*0*(\d+)|(?:episodio|capitulo|capítulo|episode|ep)\s*0*(\d+)/i);
    if (ep && Number(ep[1] || ep[2]) === wanted) return absoluteUrl(m[1]);
  }
  return "";
}
