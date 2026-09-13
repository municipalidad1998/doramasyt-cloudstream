/**
 * doramasyt - Built from src/doramasyt/
 * Generated: 2026-09-13T23:09:28.806Z
 */
var __defProp = Object.defineProperty;
var __defProps = Object.defineProperties;
var __getOwnPropDesc = Object.getOwnPropertyDescriptor;
var __getOwnPropDescs = Object.getOwnPropertyDescriptors;
var __getOwnPropNames = Object.getOwnPropertyNames;
var __getOwnPropSymbols = Object.getOwnPropertySymbols;
var __hasOwnProp = Object.prototype.hasOwnProperty;
var __propIsEnum = Object.prototype.propertyIsEnumerable;
var __defNormalProp = (obj, key, value) => key in obj ? __defProp(obj, key, { enumerable: true, configurable: true, writable: true, value }) : obj[key] = value;
var __spreadValues = (a, b) => {
  for (var prop in b || (b = {}))
    if (__hasOwnProp.call(b, prop))
      __defNormalProp(a, prop, b[prop]);
  if (__getOwnPropSymbols)
    for (var prop of __getOwnPropSymbols(b)) {
      if (__propIsEnum.call(b, prop))
        __defNormalProp(a, prop, b[prop]);
    }
  return a;
};
var __spreadProps = (a, b) => __defProps(a, __getOwnPropDescs(b));
var __export = (target, all) => {
  for (var name in all)
    __defProp(target, name, { get: all[name], enumerable: true });
};
var __copyProps = (to, from, except, desc) => {
  if (from && typeof from === "object" || typeof from === "function") {
    for (let key of __getOwnPropNames(from))
      if (!__hasOwnProp.call(to, key) && key !== except)
        __defProp(to, key, { get: () => from[key], enumerable: !(desc = __getOwnPropDesc(from, key)) || desc.enumerable });
  }
  return to;
};
var __toCommonJS = (mod) => __copyProps(__defProp({}, "__esModule", { value: true }), mod);
var __async = (__this, __arguments, generator) => {
  return new Promise((resolve, reject) => {
    var fulfilled = (value) => {
      try {
        step(generator.next(value));
      } catch (e) {
        reject(e);
      }
    };
    var rejected = (value) => {
      try {
        step(generator.throw(value));
      } catch (e) {
        reject(e);
      }
    };
    var step = (x) => x.done ? resolve(x.value) : Promise.resolve(x.value).then(fulfilled, rejected);
    step((generator = generator.apply(__this, __arguments)).next());
  });
};

// src/doramasyt/index.js
var index_exports = {};
__export(index_exports, {
  getStreams: () => getStreams
});
module.exports = __toCommonJS(index_exports);

// src/doramasyt/http.js
var BASE_URL = "https://www.doramasyt.com";
var UA = "Mozilla/5.0 (Android 13) AppleWebKit/537.36 Chrome/122 Mobile Safari/537.36";
var HEADERS = {
  "User-Agent": UA,
  "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
  "Accept-Language": "es-ES,es;q=0.9,en;q=0.5",
  "Referer": BASE_URL + "/"
};
function request(_0) {
  return __async(this, arguments, function* (url, options = {}) {
    const response = yield fetch(url, {
      method: options.method || "GET",
      headers: __spreadValues(__spreadValues({}, HEADERS), options.headers || {}),
      body: options.body
    });
    if (!response.ok) throw new Error("HTTP " + response.status);
    return options.json ? response.json() : response.text();
  });
}
function clean(value) {
  return String(value || "").replace(/<[^>]+>/g, " ").replace(/&amp;/g, "&").replace(/&quot;/g, '"').replace(/&#39;/g, "'").replace(/\s+/g, " ").trim();
}
function absoluteUrl(href) {
  if (!href) return "";
  if (href.startsWith("//")) return "https:" + href;
  if (/^https?:\/\//i.test(href)) return href;
  if (href.startsWith("/")) return BASE_URL + href;
  return BASE_URL + "/" + href;
}

// src/doramasyt/search.js
var BASE = "https://www.doramasyt.com";
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
function getTmdbTitle(tmdbId, mediaType) {
  return __async(this, null, function* () {
    const type = mediaType === "tv" || mediaType === "series" ? "tv" : "movie";
    const html = yield request("https://www.themoviedb.org/" + type + "/" + tmdbId);
    const meta = html.match(/<meta[^>]+property=["']og:title["'][^>]+content=["']([^"']+)/i);
    const title = meta ? meta[1] : (html.match(/<title>([^<]+)/i) || [])[1] || "";
    const result = clean(title).replace(/\s*\|\s*TMDB.*$/i, "").trim();
    if (!result) throw new Error("TMDB title not found");
    return result;
  });
}
function parseAnchors(html) {
  const result = [];
  const re = /<a\b[^>]*href=["']([^"']+)["'][^>]*>([\s\S]*?)<\/a>/gi;
  let m;
  while (m = re.exec(html)) {
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
  const words = b.split(" ").filter((w) => w.length > 2);
  const hits = words.filter((w) => a.includes(w)).length;
  return words.length > 1 && hits >= Math.max(2, words.length - 1);
}
function searchEpisode(title, episode) {
  return __async(this, null, function* () {
    const episodeNumber = Number(episode);
    const queries = aliases(title);
    function scan(url) {
      return __async(this, null, function* () {
        const html = yield request(url);
        const anchors = parseAnchors(html);
        const wantedEpisode = new RegExp("(?:cap[i\xED]tulo|episodio|episode|ep)[^0-9]{0,10}0*" + episodeNumber + "(?:\\D|$)", "i");
        const exact = anchors.filter((a) => titleMatch(a.text, title) && wantedEpisode.test(a.text));
        if (exact.length) return exact[0].href;
        const byNumber = anchors.filter((a) => wantedEpisode.test(a.text) || new RegExp("(?:^|[^0-9])0*" + episodeNumber + "(?:[^0-9]|$)").test(a.text));
        const matching = byNumber.filter((a) => titleMatch(a.text, title));
        if (matching.length) return matching[0].href;
        return null;
      });
    }
    for (const q of queries) {
      try {
        const found = yield scan(BASE + "/?s=" + encodeURIComponent(q));
        if (found) return found;
      } catch (_) {
      }
    }
    try {
      const found = yield scan(BASE + "/");
      if (found) return found;
    } catch (_) {
    }
    return null;
  });
}
function searchDorama(title) {
  return __async(this, null, function* () {
    const queries = aliases(title);
    let best = null;
    for (const q of queries) {
      try {
        const html = yield request(BASE + "/?s=" + encodeURIComponent(q));
        const anchors = parseAnchors(html);
        const candidates = anchors.filter((a) => /\/dorama\//i.test(a.href));
        candidates.sort((a, b) => {
          const score = (x) => titleMatch(x.text, q) ? 0 : slug(x.href).includes(slug(q)) ? 1 : 5;
          return score(a) - score(b);
        });
        if (candidates.length) return candidates[0].href;
        if (!best) {
          const fallback = anchors.filter((a) => titleMatch(a.text, q));
          if (fallback.length) best = fallback[0].href;
        }
      } catch (_) {
      }
    }
    if (best) return best;
    throw new Error("DoramaYT title not found: " + title);
  });
}
function getEpisodeUrl(detailUrl, title, episode) {
  return __async(this, null, function* () {
    if (!episode) return detailUrl;
    const fromListing = yield searchEpisode(title, episode);
    if (fromListing) return fromListing;
    try {
      const html = yield request(detailUrl);
      const anchors = parseAnchors(html);
      const wantedEpisode = new RegExp("(?:cap[i\xED]tulo|episodio|episode|ep)[^0-9]{0,10}0*" + episode + "(?:\\D|$)", "i");
      const candidates = anchors.filter((a) => wantedEpisode.test(a.text) || wantedEpisode.test(a.href));
      if (candidates.length) return candidates[0].href;
    } catch (_) {
    }
    return detailUrl;
  });
}

// src/doramasyt/extract.js
function decode(value) {
  return String(value || "").replace(/&amp;/gi, "&").replace(/&quot;|&#34;|&#x22;/gi, '"').replace(/&#39;|&#x27;/gi, "'").replace(/\\u002F/gi, "/").replace(/\\\//g, "/");
}
function addUrl(out, seen, url, referer) {
  if (!url) return;
  let u = decode(url.trim());
  if (u.startsWith("//")) u = "https:" + u;
  if (!/^https?:\/\//i.test(u) || seen.has(u)) return;
  seen.add(u);
  out.push({
    name: "DoramaYT",
    title: "Servidor",
    url: u,
    quality: "Auto",
    headers: __spreadProps(__spreadValues({}, HEADERS), { Referer: referer })
  });
}
function collectRawCandidates(html) {
  const out = [];
  const re = /(?:src|href|file|source|data-src|data-file|data-video|data-player|data-embed)=["']([^"']+)["']/gi;
  let m;
  while (m = re.exec(html)) out.push(m[1]);
  const iframe = /<iframe[^>]+src=["']([^"']+)["']/gi;
  while (m = iframe.exec(html)) out.push(m[1]);
  const jsonish = /"(?:file|url|src|stream|source|playlist|hls|dash)"\s*:\s*"([^"]+)"/gi;
  while (m = jsonish.exec(html)) out.push(m[1]);
  return out;
}
function isLikelyMedia(url) {
  return /\.(m3u8|mpd|mp4|mkv|webm|m4v|mov|ts|avi|flv|3gp|mpeg|mpg|ogv)(?:$|[?#])/i.test(url);
}
function extractStreams(_0) {
  return __async(this, arguments, function* (pageUrl, depth = 0, visited = /* @__PURE__ */ new Set()) {
    if (depth > 3 || visited.has(pageUrl)) return [];
    visited.add(pageUrl);
    const html = yield request(pageUrl);
    const direct = [];
    const nested = [];
    const seen = /* @__PURE__ */ new Set();
    for (const raw of collectRawCandidates(html)) {
      const u = absoluteUrl(decode(raw));
      if (!u) continue;
      if (isLikelyMedia(u)) {
        addUrl(direct, seen, u, pageUrl);
      } else if (depth < 3) {
        nested.push(u);
      }
    }
    for (const nestedUrl of nested.slice(0, 8)) {
      try {
        const more = yield extractStreams(nestedUrl, depth + 1, visited);
        for (const stream of more) {
          if (!seen.has(stream.url)) {
            seen.add(stream.url);
            direct.push(stream);
          }
        }
      } catch (_) {
      }
    }
    return direct;
  });
}

// src/doramasyt/index.js
function getStreams(tmdbId, mediaType, season, episode) {
  return __async(this, null, function* () {
    try {
      const title = yield getTmdbTitle(tmdbId, mediaType);
      const detail = yield searchDorama(title);
      const pageUrl = mediaType === "tv" && episode ? yield getEpisodeUrl(detail, episode) : detail;
      const streams = yield extractStreams(pageUrl);
      return streams;
    } catch (error) {
      console.error("[DoramaYT] " + error.message);
      return [];
    }
  });
}
module.exports = { getStreams };
