/**
 * sololatino - Built from src/sololatino/
 * Generated: 2026-09-14T01:38:54.526Z
 */
var __defProp = Object.defineProperty;
var __defProps = Object.defineProperties;
var __getOwnPropDescs = Object.getOwnPropertyDescriptors;
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

// src/sololatino/http.js
var BASE_URL = "https://sololatino.net";
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
    if (!response.ok) throw new Error("HTTP " + response.status + " for " + url);
    return options.json ? response.json() : response.text();
  });
}
function clean(value) {
  return String(value || "").replace(/<[^>]+>/g, " ").replace(/&amp;/g, "&").replace(/&quot;/g, '"').replace(/&#39;/g, "'").replace(/\s+/g, " ").trim();
}
function absoluteUrl(href, base = BASE_URL) {
  if (!href) return "";
  try {
    return new URL(String(href).trim(), base).toString();
  } catch (_) {
    return "";
  }
}

// src/sololatino/titles.js
function unique(values) {
  return [...new Set(values.map(clean).filter(Boolean))];
}
function extractTitle(html) {
  const og = html.match(/<meta[^>]+property=["']og:title["'][^>]+content=["']([^"']+)/i);
  return clean((og ? og[1] : (html.match(/<title>([^<]+)/i) || [])[1] || "").replace(/\s*\|\s*TMDB.*$/i, ""));
}
function getTmdbTitleVariants(tmdbId, mediaType) {
  return __async(this, null, function* () {
    const type = mediaType === "movie" ? "movie" : "tv";
    const urls = [
      `https://www.themoviedb.org/${type}/${tmdbId}`,
      `https://www.themoviedb.org/${type}/${tmdbId}?language=es-ES`,
      `https://www.themoviedb.org/${type}/${tmdbId}?language=en-US`
    ];
    const titles = [];
    for (const url of urls) {
      try {
        titles.push(extractTitle(yield request(url)));
      } catch (_) {
      }
    }
    return unique(titles).slice(0, 8);
  });
}

// src/sololatino/search.js
function normalize(value) {
  return clean(value).toLowerCase().normalize("NFD").replace(/[\u0300-\u036f]/g, "").replace(/[^a-z0-9]+/g, " ").trim();
}
function titleMatch(text, title) {
  const a = normalize(text), b = normalize(title);
  if (!a || !b) return false;
  if (a.includes(b) || b.includes(a)) return true;
  const words = b.split(" ").filter((w) => w.length > 2);
  const hits = words.filter((w) => a.includes(w)).length;
  return words.length > 1 && hits >= Math.max(2, words.length - 1);
}
function parseAnchors(html) {
  const result = [];
  const re = /<a\b[^>]*href=["']([^"']+)["'][^>]*>([\s\S]*?)<\/a>/gi;
  let m;
  while (m = re.exec(html)) result.push({ href: absoluteUrl(m[1]), text: clean(m[2]) });
  return result;
}
function searchSoloLatino(title) {
  return __async(this, null, function* () {
    const queries = [...new Set([title, title.replace(/[:.!?]/g, " ")].map(clean).filter(Boolean))];
    for (const query of queries) {
      for (const url of [
        BASE_URL + "/buscar?query=" + encodeURIComponent(query),
        BASE_URL + "/buscar?q=" + encodeURIComponent(query)
      ]) {
        try {
          const html = yield request(url);
          const candidates = parseAnchors(html).filter(
            (a) => a.href.startsWith(BASE_URL + "/") && titleMatch(a.text, query)
          );
          const content = candidates.find((a) => /\/(?:pelicula|serie|anime|dorama)\//i.test(a.href)) || candidates[0];
          if (content) return content.href;
        } catch (_) {
        }
      }
    }
    throw new Error("SoloLatino title not found: " + title);
  });
}
function findEpisodeUrl(html, episode) {
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

// src/sololatino/extract.js
function decode(value) {
  return String(value || "").replace(/&amp;/gi, "&").replace(/&quot;|&#34;|&#x22;/gi, '"').replace(/&#39;|&#x27;/gi, "'").replace(/\\u002F/gi, "/").replace(/\\\//g, "/").trim();
}
function isMedia(url) {
  return /\.(?:m3u8|mpd|mp4|mkv|webm|m4v|mov|ts)(?:$|[?#])/i.test(url) || /(?:manifest|playlist|master)\.(?:m3u8|mpd)/i.test(url);
}
function likelyPlayer(url) {
  try {
    const p = new URL(url);
    const h = p.hostname.toLowerCase(), path = p.pathname.toLowerCase();
    if (/\.(?:js|css|png|jpe?g|gif|svg|webp|woff2?|ttf)(?:$|[?#])/i.test(url)) return false;
    return /(streamtape|dood|filemoon|streamwish|voe|uqload|mixdrop|vidplay|vidhide|filelions|mp4upload)/i.test(h) || /(?:\/embed|\/e\/|\/player|\/reproductor|\/watch|\/stream|\/play)/i.test(path);
  } catch (_) {
    return false;
  }
}
function extractStreams(_0) {
  return __async(this, arguments, function* (pageUrl, depth = 0, visited = /* @__PURE__ */ new Set()) {
    if (depth > 4 || visited.has(pageUrl)) return [];
    visited.add(pageUrl);
    const html = yield request(pageUrl);
    const streams = [], nested = [], seen = /* @__PURE__ */ new Set();
    const add = (raw, title = "Servidor") => {
      const u = absoluteUrl(decode(raw), pageUrl);
      if (!u || seen.has(u)) return;
      if (isMedia(u)) {
        seen.add(u);
        streams.push({
          name: "SoloLatino",
          title,
          url: u,
          quality: /1080/i.test(u) ? "1080p" : /720/i.test(u) ? "720p" : "Auto",
          headers: __spreadProps(__spreadValues({}, HEADERS), { Referer: pageUrl })
        });
      } else if (likelyPlayer(u)) nested.push({ url: u, title });
    };
    let m;
    const attrs = /(?:src|file|source|data-src|data-file|data-video|data-embed|data-url)=\s*["']([^"']+)["']/gi;
    while (m = attrs.exec(html)) add(m[1]);
    const iframes = /<iframe[^>]+src=\s*["']([^"']+)["']/gi;
    while (m = iframes.exec(html)) nested.push({ url: absoluteUrl(m[1], pageUrl), title: "Servidor" });
    const json = /["'](?:file|url|src|stream|source|playlist|hls|dash|file_url|video_url|stream_url)["']\s*:\s*["']([^"']+)["']/gi;
    while (m = json.exec(html)) add(m[1]);
    const media = /https?:\/\/[^\s"'<>]+\.(?:m3u8|mpd|mp4|mkv|webm|m4v|mov|ts)(?:\?[^\s"'<>]*)?/gi;
    while (m = media.exec(html)) add(m[0]);
    const uniqueNested = [...new Map(nested.filter((x) => x.url).map((x) => [x.url, x])).values()].slice(0, 12);
    const results = yield Promise.all(uniqueNested.map((x) => extractStreams(x.url, depth + 1, visited).catch(() => [])));
    for (const group of results) for (const stream of group)
      if (!seen.has(stream.url)) {
        seen.add(stream.url);
        streams.push(stream);
      }
    return streams;
  });
}

// src/sololatino/index.js
function normalizeType(type) {
  const value = String(type || "").toLowerCase();
  return value === "movie" || value === "film" ? "movie" : "tv";
}
function getStreams(tmdbId, mediaType, season, episode) {
  return __async(this, null, function* () {
    try {
      const type = normalizeType(mediaType);
      const variants = yield getTmdbTitleVariants(tmdbId, type);
      for (const title of variants) {
        try {
          const detail = yield searchSoloLatino(title);
          let pageUrl = detail;
          if (type === "tv" && episode) {
            const html = yield request(detail);
            const epUrl = findEpisodeUrl(html, episode);
            if (epUrl) pageUrl = epUrl;
          }
          const streams = yield extractStreams(pageUrl);
          if (streams.length) return streams;
        } catch (error) {
          console.error("[SoloLatino] Variant '" + title + "': " + error.message);
        }
      }
      return [];
    } catch (error) {
      console.error("[SoloLatino] " + error.message);
      return [];
    }
  });
}
module.exports = { getStreams };
