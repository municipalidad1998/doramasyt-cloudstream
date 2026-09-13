/**
 * doramasyt - Built from src/doramasyt/
 * Generated: 2026-09-13T23:26:46.333Z
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
  const words = b.split(" ").filter((w) => w.length > 2);
  const hits = words.filter((w) => a.includes(w)).length;
  return words.length > 1 && hits >= Math.max(2, words.length - 1);
}
function postForm(url, body, referer) {
  return __async(this, null, function* () {
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
  });
}
function extractEpisodeApi(html, detailUrl) {
  const tokenMatch = html.match(/<meta[^>]+name=["']csrf-token["'][^>]+content=["']([^"']+)/i);
  const ajaxMatch = html.match(/class=["'][^"']*caplist[^"']*["'][^>]+data-ajax=["']([^"']+)/i) || html.match(/data-ajax=["']([^"']+)["'][^>]*class=["'][^"']*caplist/i);
  if (!tokenMatch || !ajaxMatch) return null;
  return {
    token: tokenMatch[1],
    ajax: absoluteUrl(ajaxMatch[1]),
    referer: detailUrl
  };
}
function findEpisodeFromApi(detailUrl, episode) {
  return __async(this, null, function* () {
    const html = yield request(detailUrl);
    const api = extractEpisodeApi(html, detailUrl);
    if (!api) return null;
    const first = yield postForm(api.ajax, "_token=" + encodeURIComponent(api.token), api.referer);
    if (!first || typeof first !== "object") return null;
    const total = Array.isArray(first.eps) ? first.eps.length : 0;
    const perPage = Number(first.perpage || total || 1);
    const pages = Math.max(1, Math.ceil(total / perPage));
    const paginateUrl = absoluteUrl(first.paginate_url || api.ajax);
    for (let page = 1; page <= pages; page++) {
      const data = yield postForm(
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
  });
}
function searchDorama(title) {
  return __async(this, null, function* () {
    const queries = aliases(title);
    let best = null;
    for (const q of queries) {
      try {
        const html = yield request(BASE_URL + "/buscar?q=" + encodeURIComponent(q));
        const anchors = parseAnchors(html);
        const candidates = anchors.filter((a) => /\/dorama\//i.test(a.href));
        candidates.sort((a, b) => {
          const score = (x) => titleMatch(x.text, q) ? 0 : slug(x.href).includes(slug(q)) ? 1 : 5;
          return score(a) - score(b);
        });
        if (candidates.length) return candidates[0].href;
        if (!best) {
          const fallback = anchors.filter((a) => titleMatch(a.text, q));
          if (fallback.length) best = fallback[0];
        }
      } catch (_) {
      }
    }
    if (best) return best.href;
    throw new Error("DoramaYT title not found: " + title);
  });
}
function getEpisodeUrl(detailUrl, title, episode) {
  return __async(this, null, function* () {
    if (!episode) return detailUrl;
    try {
      const apiUrl = yield findEpisodeFromApi(detailUrl, episode);
      if (apiUrl) return apiUrl;
    } catch (error) {
      console.error("[DoramaYT] Episode API: " + error.message);
    }
    const queries = aliases(title);
    try {
      const html = yield request(BASE_URL + "/emision");
      const anchors = parseAnchors(html);
      const wanted = new RegExp("(?:cap[i\xED]tulo|episodio|episode|ep)[^0-9]{0,10}0*" + Number(episode) + "(?:\\D|$)", "i");
      const found = anchors.find((a) => queries.some((q) => titleMatch(a.text, q)) && wanted.test(a.text));
      if (found) return found.href;
    } catch (_) {
    }
    return detailUrl;
  });
}

// src/doramasyt/extract.js
function decode(value) {
  return String(value || "").replace(/&amp;/gi, "&").replace(/&quot;|&#34;|&#x22;/gi, '"').replace(/&#39;|&#x27;/gi, "'").replace(/\\u002F/gi, "/").replace(/\\\//g, "/").trim();
}
function base64ToText(value) {
  try {
    let input = String(value || "").replace(/\s/g, "").replace(/-/g, "+").replace(/_/g, "/");
    if (!input || !/^[A-Za-z0-9+/=]+$/.test(input)) return "";
    while (input.length % 4) input += "=";
    if (typeof atob !== "function") return "";
    const binary = atob(input);
    let out = "";
    for (let i = 0; i < binary.length; i++) out += String.fromCharCode(binary.charCodeAt(i));
    try {
      return decodeURIComponent(escape(out));
    } catch (_) {
      return out;
    }
  } catch (_) {
    return "";
  }
}
function addUrl(out, seen, url, referer, title = "Servidor") {
  if (!url) return;
  let u = decode(url);
  if (u.startsWith("//")) u = "https:" + u;
  if (!/^https?:\/\//i.test(u) || seen.has(u)) return;
  seen.add(u);
  out.push({
    name: "DoramaYT",
    title,
    url: u,
    quality: /(?:2160|4k)/i.test(u) ? "2160p" : /1080/i.test(u) ? "1080p" : /720/i.test(u) ? "720p" : /480/i.test(u) ? "480p" : "Auto",
    headers: __spreadProps(__spreadValues({}, HEADERS), { Referer: referer })
  });
}
function unwrapPlayer(url) {
  const value = decode(url);
  if (!/\/reproductor\?url=/i.test(value)) return "";
  const match = value.match(/[?&]url=(.+)$/i);
  if (!match) return "";
  try {
    return decodeURIComponent(match[1]);
  } catch (_) {
    return match[1];
  }
}
function collectRawCandidates(html) {
  const out = [];
  let m;
  const attrs = /(?:src|href|file|source|data-src|data-file|data-video|data-embed)=["']([^"']+)["']/gi;
  while (m = attrs.exec(html)) out.push({ value: m[1], nested: true });
  const players = /data-player=["']([^"']+)["']/gi;
  while (m = players.exec(html)) {
    const decoded = base64ToText(m[1]);
    const player = decoded || m[1];
    const target = unwrapPlayer(player);
    if (target) out.push({ value: target, nested: true });
    out.push({ value: player, nested: true });
  }
  const iframe = /<iframe[^>]+src=["']([^"']+)["']/gi;
  while (m = iframe.exec(html)) out.push({ value: m[1], nested: true });
  const jsonish = /["'](?:file|url|src|stream|source|playlist|hls|dash)["']\s*:\s*["']([^"']+)["']/gi;
  while (m = jsonish.exec(html)) out.push({ value: m[1], nested: true });
  const jw = /(?:file|src)\s*:\s*["'](https?:\/\/[^"']+)["']/gi;
  while (m = jw.exec(html)) out.push({ value: m[1], nested: true });
  return out;
}
function isLikelyMedia(url) {
  return /\.(m3u8|mpd|mp4|mkv|webm|m4v|mov|ts|avi|flv|3gp|mpeg|mpg|ogv)(?:$|[?#])/i.test(url) || /(?:\.m3u8\?|\.mpd\?|manifest(?:\.m3u8)?|playlist(?:\.m3u8)?)/i.test(url);
}
function isUsefulNested(url) {
  return /(?:voe|filemoon|moonplayer|streamwish|strwish|wishembed|wishfast|dood|doodstream|ds2play|filelions|mixdrop|streamtape|ok\.ru|okru|uqload|vidmoly|vidhide|vidplay|embed|player|stream|reproductor)/i.test(url);
}
function extractStreams(_0) {
  return __async(this, arguments, function* (pageUrl, depth = 0, visited = /* @__PURE__ */ new Set()) {
    if (depth > 4 || visited.has(pageUrl)) return [];
    visited.add(pageUrl);
    const html = yield request(pageUrl);
    const direct = [];
    const nested = [];
    const seen = /* @__PURE__ */ new Set();
    for (const candidate of collectRawCandidates(html)) {
      const raw = decode(candidate.value);
      const unwrapped = unwrapPlayer(raw);
      const values = unwrapped ? [unwrapped, raw] : [raw];
      for (const value of values) {
        const u = absoluteUrl(value);
        if (!u) continue;
        if (isLikelyMedia(u)) addUrl(direct, seen, u, pageUrl);
        else if (candidate.nested && depth < 4 && isUsefulNested(u)) nested.push(u);
      }
    }
    for (const nestedUrl of [...new Set(nested)].slice(0, 16)) {
      try {
        const more = yield extractStreams(nestedUrl, depth + 1, visited);
        for (const stream of more) {
          if (!seen.has(stream.url)) {
            seen.add(stream.url);
            direct.push(stream);
          }
        }
      } catch (error) {
        console.error("[DoramaYT] Nested extractor: " + error.message);
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
      const pageUrl = mediaType === "tv" && episode ? yield getEpisodeUrl(detail, title, episode) : detail;
      const streams = yield extractStreams(pageUrl);
      return streams;
    } catch (error) {
      console.error("[DoramaYT] " + error.message);
      return [];
    }
  });
}
module.exports = { getStreams };
