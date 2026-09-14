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

function episodeNumber(value) {
  const text = String(value == null ? "" : value).trim();
  if (!text) return 0;
  const direct = Number(text);
  if (Number.isFinite(direct) && direct > 0) return Math.floor(direct);
  const match = text.match(/(?:S\d+\s*)?E\s*(\d+)|(?:episode|episodio|cap[ií]tulo|ep)\s*\.?\s*(\d+)/i);
  if (match) return Number(match[1] || match[2]);
  const numbers = text.match(/\d+/g);
  return numbers && numbers.length ? Number(numbers[numbers.length - 1]) : 0;
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

function episodeMatch(item, title, episode) {
  const ep = episodeNumber(episode);
  if (!ep) return false;
  const text = normalize(item.text + " " + item.href);
  if (!titleMatch(text, title)) return false;
  const wanted = new RegExp("(?:cap[ií]tulo|episodio|episode|ep|e|x)[^0-9]{0,10}0*" + ep + "(?:\\D|$)", "i");
  return wanted.test(text) || new RegExp("(?:1x|s0*1e|e)0*" + ep + "(?:\\D|$)", "i").test(text);
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
  const tokenMatch = html.match(/<meta[^>]+name=["']csrf-token["'][^>]+content=["']([^"']+)/i) ||
    html.match(/name=["']csrf-token["'][^>]+content=["']([^"']+)/i);
  const ajaxMatch = html.match(/class=["'][^"']*caplist[^"']*["'][^>]+data-ajax=["']([^"']+)/i) ||
    html.match(/data-ajax=["']([^"']+)["'][^>]*class=["'][^"']*caplist/i) ||
    html.match(/data-ajax=["']([^"']+)["']/i);
  if (!ajaxMatch) return null;
  return { token: tokenMatch ? tokenMatch[1] : "", ajax: absoluteUrl(ajaxMatch[1]), referer: detailUrl };
}

async function findEpisodeFromApi(detailUrl, episode) {
  const wantedEpisode = episodeNumber(episode);
  if (!wantedEpisode) return null;
  const html = await request(detailUrl);
  const api = extractEpisodeApi(html, detailUrl);
  if (!api) return null;

  const body = api.token ? "_token=" + encodeURIComponent(api.token) : "";
  const first = await postForm(api.ajax, body, api.referer);
  if (!first || typeof first !== "object") return null;

  const total = Array.isArray(first.eps) ? first.eps.length : 0;
  const perPage = Number(first.perpage || total || 1);
  const pages = Math.max(1, Math.ceil(total / perPage));
  const paginateUrl = absoluteUrl(first.paginate_url || api.ajax);

  for (let page = 1; page <= pages; page++) {
    const pageBody = (api.token ? "_token=" + encodeURIComponent(api.token) + "&" : "") + "p=" + encodeURIComponent(page);
    const data = await postForm(paginateUrl, pageBody, api.referer);
    const caps = data && Array.isArray(data.caps) ? data.caps : [];
    for (const cap of caps) {
      if (episodeNumber(cap.episodio) === wantedEpisode && cap.url) return absoluteUrl(cap.url);
    }
  }
  return null;
}

async function findEpisodeByDeterministicUrl(detailUrl, episode) {
  const wanted = episodeNumber(episode);
  if (!wanted) return null;
  const match = detailUrl.match(/\/dorama\/([^/?#]+)(?:[/?#]|$)/i);
  if (!match) return null;
  const baseSlug = match[1].replace(/-+$/, "");
  const candidates = [
    BASE_URL + "/ver/" + baseSlug + "-episodio-" + wanted,
    BASE_URL + "/ver/" + baseSlug + "-capitulo-" + wanted
  ];
  const results = await Promise.all(candidates.map(async candidate => {
    try {
      const html = await request(candidate);
      return /<(?:title|h1)[^>]*>[\s\S]*?(?:episodio|cap[ií]tulo|e\s*\d+)/i.test(html) || /data-player=/i.test(html) ? candidate : null;
    } catch (_) {
      return null;
    }
  }));
  return results.find(Boolean) || null;
}

async function findEpisodeFromSearch(title, episode) {
  const queries = aliases(title);
  const results = await Promise.all(queries.map(async q => {
    try {
      const html = await request(BASE_URL + "/buscar?q=" + encodeURIComponent(q));
      return parseAnchors(html).filter(a => episodeMatch(a, q, episode));
    } catch (_) {
      return [];
    }
  }));
  const matches = [];
  for (const group of results) for (const item of group) matches.push(item);
  if (!matches.length) return null;
  matches.sort((a, b) => {
    const wanted = episodeNumber(episode);
    const aEpisode = episodeNumber(a.text + " " + a.href);
    const bEpisode = episodeNumber(b.text + " " + b.href);
    return Math.abs(aEpisode - wanted) - Math.abs(bEpisode - wanted);
  });
  return matches[0].href;
}

async function findEpisodeFromEmission(title, episode) {
  const wanted = episodeNumber(episode);
  if (!wanted) return null;
  try {
    const html = await request(BASE_URL + "/emision");
    const anchors = parseAnchors(html);
    const matches = anchors.filter(a => episodeMatch(a, title, wanted));
    if (matches.length) return matches[0].href;
  } catch (_) {}
  return null;
}

export async function searchDorama(title) {
  const queries = aliases(title);
  const results = await Promise.all(queries.map(async q => {
    try {
      const html = await request(BASE_URL + "/buscar?q=" + encodeURIComponent(q));
      const anchors = parseAnchors(html);
      const candidates = anchors.filter(a => /\/dorama\//i.test(a.href));
      const fallback = anchors.filter(a => titleMatch(a.text, q));
      return { query: q, candidates, fallback };
    } catch (_) {
      return { query: q, candidates: [], fallback: [] };
    }
  }));

  let best = null;
  for (const result of results) {
    result.candidates.sort((a, b) => {
      const score = x => titleMatch(x.text, result.query) ? 0 : slug(x.href).includes(slug(result.query)) ? 1 : 5;
      return score(a) - score(b);
    });
    if (result.candidates.length && (!best || titleMatch(result.candidates[0].text, title))) best = result.candidates[0];
  }
  if (best) return best.href;

  for (const result of results) if (result.fallback.length) return result.fallback[0].href;
  throw new Error("DoramaYT title not found: " + title);
}

export async function getEpisodeUrl(detailUrl, title, episode) {
  const wanted = episodeNumber(episode);
  if (!wanted) return detailUrl;

  try {
    const deterministic = await findEpisodeByDeterministicUrl(detailUrl, wanted);
    if (deterministic) return deterministic;
  } catch (error) {
    console.error("[DoramaYT] Deterministic episode URL: " + error.message);
  }

  try {
    const apiUrl = await findEpisodeFromApi(detailUrl, wanted);
    if (apiUrl) return apiUrl;
  } catch (error) {
    console.error("[DoramaYT] Episode API: " + error.message);
  }

  try {
    const searchUrl = await findEpisodeFromSearch(title, wanted);
    if (searchUrl) return searchUrl;
  } catch (error) {
    console.error("[DoramaYT] Episode search: " + error.message);
  }

  try {
    const emissionUrl = await findEpisodeFromEmission(title, wanted);
    if (emissionUrl) return emissionUrl;
  } catch (error) {
    console.error("[DoramaYT] Emission fallback: " + error.message);
  }

  return detailUrl;
}
