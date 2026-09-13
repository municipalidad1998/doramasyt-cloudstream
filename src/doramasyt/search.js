import { request, clean, absoluteUrl, BASE_URL } from "./http.js";

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
  if (n === "eres mi sol") out.push("tan resplandeciente como el sol", "shine on me");
  return [...new Set(out)];
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
    if (!href.startsWith(BASE_URL + "/")) continue;
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

async function postForm(url, body, referer) {
  return request(url, {
    method: "POST",
    headers: {
      "Accept": "application/json, text/javascript, */*; q=0.01",
      "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
      "Origin": BASE_URL,
      "Referer": referer,
      "X-Requested-With": "XMLHttpRequest"
    },
    body,
    json: true
  });
}

function extractEpisodeApi(html, detailUrl) {
  const tokenMatch = html.match(/<meta[^>]+name=["']csrf-token["'][^>]+content=["']([^"']+)/i);
  const ajaxMatch = html.match(/class=["'][^"']*caplist[^"']*["'][^>]+data-ajax=["']([^"']+)/i) ||
    html.match(/data-ajax=["']([^"']+)["'][^>]*class=["'][^"']*caplist/i);
  if (!tokenMatch || !ajaxMatch) return null;
  return {
    token: tokenMatch[1],
    ajax: absoluteUrl(ajaxMatch[1]),
    referer: detailUrl
  };
}

async function findEpisodeFromApi(detailUrl, episode) {
  const html = await request(detailUrl);
  const api = extractEpisodeApi(html, detailUrl);
  if (!api) return null;

  const first = await postForm(api.ajax, "_token=" + encodeURIComponent(api.token), api.referer);
  if (!first || typeof first !== "object") return null;

  const total = Array.isArray(first.eps) ? first.eps.length : 0;
  const perPage = Number(first.perpage || total || 1);
  const pages = Math.max(1, Math.ceil(total / perPage));
  const paginateUrl = absoluteUrl(first.paginate_url || api.ajax);

  // The first AJAX response is metadata (eps/perpage/paginate_url).
  // The actual episode URLs are returned by the pagination endpoint, including page 1.
  for (let page = 1; page <= pages; page++) {
    const data = await postForm(
      paginateUrl,
      "_token=" + encodeURIComponent(api.token) + "&p=" + encodeURIComponent(page),
      api.referer
    );
    const caps = data && Array.isArray(data.caps) ? data.caps : [];
    for (const cap of caps) {
      if (Number(cap.episodio) === Number(episode) && cap.url) {
        return absoluteUrl(cap.url);
      }
    }
  }
  return null;
}

export async function searchDorama(title) {
  const queries = aliases(title);
  let best = null;
  for (const q of queries) {
    try {
      const html = await request(BASE_URL + "/buscar?q=" + encodeURIComponent(q));
      const anchors = parseAnchors(html);
      const candidates = anchors.filter(a => /\/dorama\//i.test(a.href));
      candidates.sort((a, b) => {
        const score = x => titleMatch(x.text, q) ? 0 : slug(x.href).includes(slug(q)) ? 1 : 5;
        return score(a) - score(b);
      });
      if (candidates.length) return candidates[0].href;
      if (!best) {
        const fallback = anchors.filter(a => titleMatch(a.text, q));
        if (fallback.length) best = fallback[0];
      }
    } catch (_) {}
  }
  if (best) return best.href;
  throw new Error("DoramaYT title not found: " + title);
}

export async function getEpisodeUrl(detailUrl, title, episode) {
  if (!episode) return detailUrl;
  try {
    const apiUrl = await findEpisodeFromApi(detailUrl, episode);
    if (apiUrl) return apiUrl;
  } catch (error) {
    console.error("[DoramaYT] Episode API: " + error.message);
  }

  const queries = aliases(title);
  try {
    const html = await request(BASE_URL + "/emision");
    const anchors = parseAnchors(html);
    const wanted = new RegExp("(?:cap[ií]tulo|episodio|episode|ep)[^0-9]{0,10}0*" + Number(episode) + "(?:\\D|$)", "i");
    const found = anchors.find(a => queries.some(q => titleMatch(a.text, q)) && wanted.test(a.text));
    if (found) return found.href;
  } catch (_) {}

  return detailUrl;
}
