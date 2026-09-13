import { request, clean, absoluteUrl } from "./http.js";

const BASE = "https://www.doramasyt.com";

function normalize(value) {
  return clean(value).toLowerCase().replace(/[^a-z0-9áéíóúüñ]+/gi, " ").trim();
}

function slug(value) {
  const n = normalize(value);
  return (n.normalize ? n.normalize("NFD").replace(/[\u0300-\u036f]/g, "") : n).replace(/[^a-z0-9]+/g, "-");
}

function aliases(title) {
  const out = [title];
  const n = normalize(title);
  if (n === "shine on me") out.push("tan resplandeciente como el sol");
  if (n === "tan resplandeciente como el sol") out.push("shine on me");
  return out;
}

export async function getTmdbTitle(tmdbId, mediaType) {
  const type = mediaType === "tv" || mediaType === "series" ? "tv" : "movie";
  const html = await request("https://www.themoviedb.org/" + type + "/" + tmdbId);
  const meta = html.match(/<meta[^>]+property=["']og:title["'][^>]+content=["']([^"']+)/i);
  const title = meta ? meta[1] : ((html.match(/<title>([^<]+)/i) || [])[1] || "");
  const result = clean(title).replace(/\s*\|\s*TMDB.*$/i, "").trim();
  if (!result) throw new Error("TMDB title not found");
  return result;
}

function parseAnchors(html) {
  const result = [];
  const re = /<a\b[^>]*href=["']([^"']+)["'][^>]*>([\s\S]*?)<\/a>/gi;
  let m;
  while ((m = re.exec(html))) {
    const href = absoluteUrl(m[1]).split("#")[0];
    if (!href.startsWith(BASE + "/")) continue;
    if (/\/(category|tag|page|author|feed|wp-|login|register|contacto|dmca)\//i.test(href)) continue;
    result.push({ href, text: clean(m[2]) });
  }
  return result;
}

function titleMatch(text, title) {
  const a = normalize(text);
  const b = normalize(title);
  if (!a || !b) return false;
  if (a.includes(b) || b.includes(a)) return true;
  const words = b.split(" ").filter(w => w.length > 2);
  const hits = words.filter(w => a.includes(w)).length;
  return words.length > 1 && hits >= Math.max(2, words.length - 1);
}

export async function searchEpisode(title, episode) {
  const episodeNumber = Number(episode);
  const queries = aliases(title);

  async function scan(url) {
    const html = await request(url);
    const anchors = parseAnchors(html);
    const wantedEpisode = new RegExp("(?:cap[ií]tulo|episodio|episode|ep)[^0-9]{0,10}0*" + episodeNumber + "(?:\\D|$)", "i");
    const exact = anchors.filter(a => titleMatch(a.text, title) && wantedEpisode.test(a.text));
    if (exact.length) return exact[0].href;
    const byNumber = anchors.filter(a => wantedEpisode.test(a.text) || new RegExp("(?:^|[^0-9])0*" + episodeNumber + "(?:[^0-9]|$)").test(a.text));
    const matching = byNumber.filter(a => titleMatch(a.text, title));
    if (matching.length) return matching[0].href;
    return null;
  }

  for (const q of queries) {
    try {
      const found = await scan(BASE + "/?s=" + encodeURIComponent(q));
      if (found) return found;
    } catch (_) {}
  }
  try {
    const found = await scan(BASE + "/");
    if (found) return found;
  } catch (_) {}
  return null;
}

export async function searchDorama(title) {
  const queries = aliases(title);
  let best = null;
  for (const q of queries) {
    try {
      const html = await request(BASE + "/?s=" + encodeURIComponent(q));
      const anchors = parseAnchors(html);
      const candidates = anchors.filter(a => /\/dorama\//i.test(a.href));
      candidates.sort((a, b) => {
        const score = x => titleMatch(x.text, q) ? 0 : slug(x.href).includes(slug(q)) ? 1 : 5;
        return score(a) - score(b);
      });
      if (candidates.length) return candidates[0].href;
      if (!best) {
        const fallback = anchors.filter(a => titleMatch(a.text, q));
        if (fallback.length) best = fallback[0].href;
      }
    } catch (_) {}
  }
  if (best) return best;
  throw new Error("DoramaYT title not found: " + title);
}

export async function getEpisodeUrl(detailUrl, title, episode) {
  if (!episode) return detailUrl;
  const fromListing = await searchEpisode(title, episode);
  if (fromListing) return fromListing;
  try {
    const html = await request(detailUrl);
    const anchors = parseAnchors(html);
    const wantedEpisode = new RegExp("(?:cap[ií]tulo|episodio|episode|ep)[^0-9]{0,10}0*" + episode + "(?:\\D|$)", "i");
    const candidates = anchors.filter(a => wantedEpisode.test(a.text) || wantedEpisode.test(a.href));
    if (candidates.length) return candidates[0].href;
  } catch (_) {}
  return detailUrl;
}
