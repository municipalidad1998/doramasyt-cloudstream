/**
 * doramasyt - Built from src/doramasyt/
 * Generated: 2026-09-14T01:57:41.005Z
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
    if (!response.ok) throw new Error("HTTP " + response.status + " for " + url);
    return options.json ? response.json() : response.text();
  });
}
function clean(value) {
  return String(value || "").replace(/<[^>]+>/g, " ").replace(/&amp;/g, "&").replace(/&quot;/g, '"').replace(/&#39;/g, "'").replace(/\s+/g, " ").trim();
}
function absoluteUrl(href, base = BASE_URL) {
  if (!href) return "";
  const value = String(href).trim();
  try {
    return new URL(value, base).toString();
  } catch (_) {
    if (value.startsWith("//")) return "https:" + value;
    if (/^https?:\/\//i.test(value)) return value;
    if (value.startsWith("/")) return BASE_URL + value;
    return BASE_URL + "/" + value;
  }
}

// src/doramasyt/titles.js
function decodeHtml(value) {
  return clean(String(value || "")).replace(/&amp;/gi, "&").replace(/&#39;/gi, "'").replace(/&quot;/gi, '"').replace(/&apos;/gi, "'").replace(/&lt;/gi, "<").replace(/&gt;/gi, ">");
}
function unique(values) {
  return [...new Set(values.map((v) => clean(v)).filter(Boolean))];
}
function extractTitle(html) {
  const og = html.match(/<meta[^>]+property=["']og:title["'][^>]+content=["']([^"']+)/i);
  return clean((og ? og[1] : (html.match(/<title>([^<]+)/i) || [])[1] || "").replace(/\s*\|\s*TMDB.*$/i, ""));
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
    while (match = pattern.exec(html)) originals.push(decodeHtml(match[1]));
  }
  return originals;
}
function getTmdbTitleVariants(tmdbId, mediaType) {
  return __async(this, null, function* () {
    const type = mediaType === "tv" || mediaType === "series" ? "tv" : "movie";
    const base = "https://www.themoviedb.org/" + type + "/" + tmdbId;
    const urls = [base, base + "?language=es-ES", base + "?language=en-US"];
    const pages = yield Promise.all(urls.map((url) => __async(null, null, function* () {
      try {
        return yield request(url);
      } catch (_) {
        return "";
      }
    })));
    const titles = [];
    for (const html of pages) {
      if (!html) continue;
      const title = extractTitle(html);
      if (title) titles.push(title);
      titles.push(...extractOriginals(html));
    }
    return unique(titles).slice(0, 10);
  });
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
function episodeMatch(item, title, episode) {
  const ep = episodeNumber(episode);
  if (!ep) return false;
  const text = normalize(item.text + " " + item.href);
  if (!titleMatch(text, title)) return false;
  const wanted = new RegExp("(?:cap[i\xED]tulo|episodio|episode|ep|e|x)[^0-9]{0,10}0*" + ep + "(?:\\D|$)", "i");
  return wanted.test(text) || new RegExp("(?:1x|s0*1e|e)0*" + ep + "(?:\\D|$)", "i").test(text);
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
  const tokenMatch = html.match(/<meta[^>]+name=["']csrf-token["'][^>]+content=["']([^"']+)/i) || html.match(/name=["']csrf-token["'][^>]+content=["']([^"']+)/i);
  const ajaxMatch = html.match(/class=["'][^"']*caplist[^"']*["'][^>]+data-ajax=["']([^"']+)/i) || html.match(/data-ajax=["']([^"']+)["'][^>]*class=["'][^"']*caplist/i) || html.match(/data-ajax=["']([^"']+)["']/i);
  if (!ajaxMatch) return null;
  return { token: tokenMatch ? tokenMatch[1] : "", ajax: absoluteUrl(ajaxMatch[1]), referer: detailUrl };
}
function findEpisodeFromApi(detailUrl, episode) {
  return __async(this, null, function* () {
    const wantedEpisode = episodeNumber(episode);
    if (!wantedEpisode) return null;
    const html = yield request(detailUrl);
    const api = extractEpisodeApi(html, detailUrl);
    if (!api) return null;
    const body = api.token ? "_token=" + encodeURIComponent(api.token) : "";
    const first = yield postForm(api.ajax, body, api.referer);
    if (!first || typeof first !== "object") return null;
    const total = Array.isArray(first.eps) ? first.eps.length : 0;
    const perPage = Number(first.perpage || total || 1);
    const pages = Math.max(1, Math.ceil(total / perPage));
    const paginateUrl = absoluteUrl(first.paginate_url || api.ajax);
    for (let page = 1; page <= pages; page++) {
      const pageBody = (api.token ? "_token=" + encodeURIComponent(api.token) + "&" : "") + "p=" + encodeURIComponent(page);
      const data = yield postForm(paginateUrl, pageBody, api.referer);
      const caps = data && Array.isArray(data.caps) ? data.caps : [];
      for (const cap of caps) {
        if (episodeNumber(cap.episodio) === wantedEpisode && cap.url) return absoluteUrl(cap.url);
      }
    }
    return null;
  });
}
function findEpisodeByDeterministicUrl(detailUrl, episode) {
  return __async(this, null, function* () {
    const wanted = episodeNumber(episode);
    if (!wanted) return null;
    const match = detailUrl.match(/\/dorama\/([^/?#]+)(?:[/?#]|$)/i);
    if (!match) return null;
    const baseSlug = match[1].replace(/-+$/, "");
    const candidates = [
      BASE_URL + "/ver/" + baseSlug + "-episodio-" + wanted,
      BASE_URL + "/ver/" + baseSlug + "-capitulo-" + wanted
    ];
    const results = yield Promise.all(candidates.map((candidate) => __async(null, null, function* () {
      try {
        const html = yield request(candidate);
        return /<(?:title|h1)[^>]*>[\s\S]*?(?:episodio|cap[ií]tulo|e\s*\d+)/i.test(html) || /data-player=/i.test(html) ? candidate : null;
      } catch (_) {
        return null;
      }
    })));
    return results.find(Boolean) || null;
  });
}
function findEpisodeFromSearch(title, episode) {
  return __async(this, null, function* () {
    const queries = aliases(title);
    const results = yield Promise.all(queries.map((q) => __async(null, null, function* () {
      try {
        const html = yield request(BASE_URL + "/buscar?q=" + encodeURIComponent(q));
        return parseAnchors(html).filter((a) => episodeMatch(a, q, episode));
      } catch (_) {
        return [];
      }
    })));
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
  });
}
function findEpisodeFromEmission(title, episode) {
  return __async(this, null, function* () {
    const wanted = episodeNumber(episode);
    if (!wanted) return null;
    try {
      const html = yield request(BASE_URL + "/emision");
      const anchors = parseAnchors(html);
      const matches = anchors.filter((a) => episodeMatch(a, title, wanted));
      if (matches.length) return matches[0].href;
    } catch (_) {
    }
    return null;
  });
}
function searchDorama(title) {
  return __async(this, null, function* () {
    const queries = aliases(title);
    const results = yield Promise.all(queries.map((q) => __async(null, null, function* () {
      try {
        const html = yield request(BASE_URL + "/buscar?q=" + encodeURIComponent(q));
        const anchors = parseAnchors(html);
        const candidates = anchors.filter((a) => /\/dorama\//i.test(a.href));
        const fallback = anchors.filter((a) => titleMatch(a.text, q));
        return { query: q, candidates, fallback };
      } catch (_) {
        return { query: q, candidates: [], fallback: [] };
      }
    })));
    let best = null;
    for (const result of results) {
      result.candidates.sort((a, b) => {
        const score = (x) => titleMatch(x.text, result.query) ? 0 : slug(x.href).includes(slug(result.query)) ? 1 : 5;
        return score(a) - score(b);
      });
      if (result.candidates.length && (!best || titleMatch(result.candidates[0].text, title))) best = result.candidates[0];
    }
    if (best) return best.href;
    for (const result of results) if (result.fallback.length) return result.fallback[0].href;
    throw new Error("DoramaYT title not found: " + title);
  });
}
function getEpisodeUrl(detailUrl, title, episode) {
  return __async(this, null, function* () {
    const wanted = episodeNumber(episode);
    if (!wanted) return detailUrl;
    try {
      const deterministic = yield findEpisodeByDeterministicUrl(detailUrl, wanted);
      if (deterministic) return deterministic;
    } catch (error) {
      console.error("[DoramaYT] Deterministic episode URL: " + error.message);
    }
    try {
      const apiUrl = yield findEpisodeFromApi(detailUrl, wanted);
      if (apiUrl) return apiUrl;
    } catch (error) {
      console.error("[DoramaYT] Episode API: " + error.message);
    }
    try {
      const searchUrl = yield findEpisodeFromSearch(title, wanted);
      if (searchUrl) return searchUrl;
    } catch (error) {
      console.error("[DoramaYT] Episode search: " + error.message);
    }
    try {
      const emissionUrl = yield findEpisodeFromEmission(title, wanted);
      if (emissionUrl) return emissionUrl;
    } catch (error) {
      console.error("[DoramaYT] Emission fallback: " + error.message);
    }
    return detailUrl;
  });
}

// src/doramasyt/extract.js
function decode(value) {
  let out = String(value || "").replace(/&amp;/gi, "&").replace(/&quot;|&#34;|&#x22;/gi, '"').replace(/&#39;|&#x27;/gi, "'").replace(/\\u002F/gi, "/").replace(/\\\//g, "/").trim();
  try {
    out = decodeURIComponent(out);
  } catch (_) {
  }
  return out;
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
function unpackPacker(script) {
  const source = String(script || "");
  if (!/eval\s*\(\s*function\s*\(p\s*,\s*a\s*,\s*c\s*,\s*k\s*,\s*e\s*,\s*[rd]\s*\)/i.test(source)) return "";
  const match = source.match(/}\s*\(['"]([\s\S]*?)['"]\s*,\s*([0-9]+)\s*,\s*([0-9]+)\s*,\s*['"]([\s\S]*?)['"]\.split\(['"]\|['"]\)/i);
  if (!match) return "";
  const payload = match[1].replace(/\\'/g, "'");
  const radix = Number(match[2]);
  const count = Number(match[3]);
  const symtab = match[4].split("|");
  if (!radix || !Number.isFinite(radix) || symtab.length !== count) return "";
  const alphabet62 = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
  const alphabet95 = " !\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~";
  let alphabet = "";
  if (radix > 36) alphabet = radix <= 62 ? alphabet62.slice(0, radix) : alphabet95.slice(0, radix);
  function unbase(word) {
    if (radix <= 36) return parseInt(word, radix);
    let value = 0;
    for (const char of word.split("").reverse()) {
      const digit = alphabet.indexOf(char);
      if (digit < 0) return NaN;
      value = value * radix + digit;
    }
    return value;
  }
  return payload.replace(/\b[a-zA-Z0-9_]+\b/g, (word) => {
    const index = unbase(word);
    return Number.isInteger(index) && index >= 0 && index < symtab.length && symtab[index] ? symtab[index] : word;
  });
}
function addUrl(out, seen, url, referer, title = "Servidor") {
  if (!url) return;
  let u = decode(url).replace(/["'<>),;]+$/g, "");
  if (u.startsWith("//")) u = "https:" + u;
  try {
    const parsed = new URL(u);
    if (/doramasyt\.com$/i.test(parsed.hostname) && /\/reproductor/i.test(parsed.pathname)) return;
    if (/\.(?:mp4|m3u8|mpd|mkv|webm)$/i.test(parsed.hostname)) return;
  } catch (_) {
    return;
  }
  if (!/^https?:\/\//i.test(u) || seen.has(u)) return;
  seen.add(u);
  out.push({ name: "DoramaYT", title, url: u, quality: /(?:2160|4k)/i.test(u) ? "2160p" : /1080/i.test(u) ? "1080p" : /720/i.test(u) ? "720p" : /480/i.test(u) ? "480p" : "Auto", headers: __spreadProps(__spreadValues({}, HEADERS), { Referer: referer }) });
}
function unwrapPlayer(url) {
  const value = decode(url);
  const match = value.match(/[?&]url=([^#]+)$/i);
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
  const playerKeyMatch = html.match(/<[^>]*class=["'][^"']*player[^"']*["'][^>]*data-key=["']([^"']+)["']/i) || html.match(/<[^>]*data-key=["']([^"']+)["'][^>]*class=["'][^"']*player/i);
  const playerKey = playerKeyMatch ? playerKeyMatch[1] : "";
  if (playerKey) {
    const buttons = /<button[^>]*data-player=["']([^"']+)["'][^>]*data-usa-api=["']([^"']+)["'][^>]*>([\s\S]*?)<\/button>/gi;
    while (m = buttons.exec(html)) {
      const label = (m[3] || "").replace(/<[^>]+>/g, " ").replace(/\s+/g, " ").trim() || "Servidor";
      const playerUrl = m[2] === "1" ? playerKey + m[1] + "&player=" + encodeURIComponent(label) : m[1];
      out.push({ value: playerUrl, nested: true, player: true, title: label });
    }
  }
  const attrs = /(?:src|file|source|data-src|data-file|data-video|data-embed|data-url)=\s*["']([^"']+)["']/gi;
  while (m = attrs.exec(html)) out.push({ value: m[1], nested: true });
  const players = /data-player=\s*["']([^"']+)["']/gi;
  while (m = players.exec(html)) {
    const decoded = base64ToText(m[1]);
    const player = decoded || m[1];
    const target = unwrapPlayer(player);
    if (target) out.push({ value: target, nested: true });
    out.push({ value: player, nested: true });
  }
  const iframe = /<iframe[^>]+src=\s*["']([^"']+)["']/gi;
  while (m = iframe.exec(html)) out.push({ value: m[1], nested: true });
  const jsonish = /["'](?:file|url|src|stream|source|playlist|hls|dash|file_url|video_url|stream_url)["']\s*:\s*["']([^"']+)["']/gi;
  while (m = jsonish.exec(html)) out.push({ value: m[1], nested: true });
  const jsQuoted = /(?:file|src|source|stream|playlist|hls|dash)\s*[:=]\s*["'](https?:\/\/[^"']+)["']/gi;
  while (m = jsQuoted.exec(html)) out.push({ value: m[1], nested: true });
  const mediaUrls = /https?:\\?\/\\?\/[^\s"'<>]+\.(?:m3u8|mpd|mp4|mkv|webm|m4v|mov|ts)(?:\?[^\s"'<>]*)?/gi;
  while (m = mediaUrls.exec(html)) out.push({ value: m[0], nested: false });
  const scripts = /<script\b[^>]*>([\s\S]*?)<\/script>/gi;
  while (m = scripts.exec(html)) {
    const body = m[1] || "";
    out.push({ value: body, nested: false, script: true });
    const unpacked = unpackPacker(body);
    if (unpacked) out.push({ value: unpacked, nested: false, script: true });
  }
  return out;
}
function isLikelyMedia(url) {
  try {
    const parsed = new URL(url);
    if (/\.(?:mp4|m3u8|mpd|mkv|webm)$/i.test(parsed.hostname)) return false;
    if (/(?:streamtape\.com|mp4upload\.com|savefiles\.com|bysekoze\.com)/i.test(parsed.hostname) && /(?:\/e\/|\/embed[-/])/i.test(parsed.pathname)) return false;
  } catch (_) {
    return false;
  }
  return /\.(m3u8|mpd|mp4|mkv|webm|m4v|mov|ts|avi|flv|3gp|mpeg|mpg|ogv)(?:$|[?#])/i.test(url) || /(?:\.m3u8\?|\.mpd\?|manifest(?:\.m3u8)?|playlist(?:\.m3u8)?|master\.txt)/i.test(url);
}
function isUsefulNested(url) {
  try {
    const parsed = new URL(url);
    const host = parsed.hostname.toLowerCase();
    const path = parsed.pathname.toLowerCase();
    if (!host) return false;
    if (/(googletagmanager|google-analytics|doubleclick|facebook\.com|facebook\.net|gstatic\.com|cloudflareinsights)/i.test(host)) return false;
    if (/\.(?:js|css|png|jpe?g|gif|svg|webp|woff2?|ttf)(?:$|[?#])/i.test(url)) return false;
    const knownHost = /(filemoon|streamwish|strwish|wishembed|wishfast|voe|dood|ds2play|filelions|mixdrop|streamtape|streamsb|uqload|vidmoly|vidhide|vidplay|vidsonic|ok\.ru|okru|embed|earnvid|lulu|mp4upload|savefiles|streamable|doramasyt\.com)/i.test(host);
    const playerPath = /(?:\/reproductor(?:\/|$)|\/embed(?:\/|$)|\/player(?:\/|$)|\/e\/|\/f\/|\/d\/|\/video\/|\/watch\/|\/stream\/|\/play\/)/i.test(path);
    return knownHost || playerPath;
  } catch (_) {
    return false;
  }
}
function resolveDood(url, referer) {
  return __async(this, null, function* () {
    if (!/dood(?:stream)?\.|dood\./i.test(url)) return [];
    try {
      const embedUrl = url.replace(/\/d\//i, "/e/");
      const html = yield request(embedUrl, { headers: { Referer: referer } });
      const host = new URL(embedUrl).origin;
      const pass = html.match(/\/pass_md5\/[^'"\s<]+/i);
      if (!pass) return [];
      const passUrl = new URL(pass[0], host).toString();
      const token = passUrl.split("/").pop();
      const base = yield request(passUrl, { headers: { Referer: embedUrl } });
      if (!base || !/^https?:\/\//i.test(base.trim())) return [];
      return [{ url: base.trim() + Math.random().toString(36).slice(2, 12) + "?token=" + token, referer: host + "/", title: "DoodStream" }];
    } catch (error) {
      console.error("[DoramaYT] Dood resolver: " + error.message);
      return [];
    }
  });
}
function resolveStreamTape(url, referer) {
  return __async(this, null, function* () {
    if (!/streamtape\./i.test(url)) return [];
    try {
      const html = yield request(url, { headers: { Referer: referer } });
      const bot = html.match(/id=["']botlink["'][^>]*>([^<]+)<\/[^>]+>/i);
      if (!bot) return [];
      let stream = bot[1].trim();
      if (stream.startsWith("//")) stream = "https:" + stream;
      else if (/^\/streamtape\.com\//i.test(stream)) stream = "https://" + stream.replace(/^\//, "");
      else if (stream.startsWith("/")) stream = "https://streamtape.com" + stream;
      if (!/^https?:\/\//i.test(stream)) return [];
      if (!/[?&]stream=1(?:&|$)/i.test(stream)) stream += (stream.includes("?") ? "&" : "?") + "stream=1";
      return [{ url: stream, referer: url, title: "StreamTape" }];
    } catch (error) {
      console.error("[DoramaYT] StreamTape resolver: " + error.message);
      return [];
    }
  });
}
function extractNested(nestedUrl, depth, visited, referer) {
  return __async(this, null, function* () {
    try {
      return yield extractStreams(nestedUrl, depth + 1, visited, referer);
    } catch (error) {
      console.error("[DoramaYT] Nested extractor: " + error.message);
      return [];
    }
  });
}
function extractStreams(_0) {
  return __async(this, arguments, function* (pageUrl, depth = 0, visited = /* @__PURE__ */ new Set(), parentReferer = "https://www.doramasyt.com/") {
    if (depth > 5 || visited.has(pageUrl)) return [];
    visited.add(pageUrl);
    const knownDoodPromise = resolveDood(pageUrl, parentReferer);
    const knownTapePromise = resolveStreamTape(pageUrl, parentReferer);
    const [knownDood, knownTape] = yield Promise.all([knownDoodPromise, knownTapePromise]);
    if (knownDood.length) return knownDood.map((item) => ({ name: "DoramaYT", title: item.title, url: item.url, quality: "Auto", headers: __spreadProps(__spreadValues({}, HEADERS), { Referer: item.referer }) }));
    if (knownTape.length) return knownTape.map((item) => ({ name: "DoramaYT", title: item.title, url: item.url, quality: "Auto", headers: __spreadProps(__spreadValues({}, HEADERS), { Referer: item.referer }) }));
    const html = yield request(pageUrl, { headers: { Referer: parentReferer } });
    const direct = [];
    const nested = [];
    const seen = /* @__PURE__ */ new Set();
    for (const candidate of collectRawCandidates(html)) {
      const raw = decode(candidate.value);
      if (candidate.script) {
        const unpacked = unpackPacker(raw);
        for (const script of unpacked ? [raw, unpacked] : [raw]) {
          const media = script.match(/https?:\\?\/\\?\/[^\s"'<>]+(?:m3u8|mpd|mp4|mkv|webm|m4v|mov|ts)(?:\?[^\s"'<>]*)?/gi) || [];
          for (const url of media) addUrl(direct, seen, url, pageUrl, "Servidor");
        }
        continue;
      }
      const unwrapped = unwrapPlayer(raw);
      const values = unwrapped ? [unwrapped, raw] : [raw];
      for (const value of values) {
        const u = absoluteUrl(value, pageUrl);
        if (!u) continue;
        if (isLikelyMedia(u)) addUrl(direct, seen, u, pageUrl, candidate.title || "Servidor");
        else if (candidate.nested && depth < 5 && isUsefulNested(u)) nested.push(u);
      }
    }
    const nestedUrls = [...new Set(nested)].slice(0, 16);
    if (!nestedUrls.length) return direct;
    const results = yield Promise.all(nestedUrls.map((url) => extractNested(url, depth, visited, pageUrl)));
    for (const streams of results) {
      for (const stream of streams) {
        if (!seen.has(stream.url)) {
          seen.add(stream.url);
          direct.push(stream);
        }
      }
    }
    return direct;
  });
}

// src/doramasyt/playback.js
function normalizeStream(stream) {
  if (!stream || !stream.url) return null;
  const headers = __spreadValues({}, stream.headers || {});
  const referer = headers.Referer || headers.referer || "https://www.doramasyt.com/";
  headers.Referer = referer;
  try {
    const streamOrigin = new URL(stream.url).origin;
    const refererOrigin = new URL(referer).origin;
    if (streamOrigin !== refererOrigin) headers.Origin = refererOrigin;
  } catch (_) {
  }
  return __spreadProps(__spreadValues({}, stream), { headers });
}
function prepareStreams(streams) {
  const result = [];
  const seen = /* @__PURE__ */ new Set();
  for (const stream of Array.isArray(streams) ? streams : []) {
    const normalized = normalizeStream(stream);
    if (!normalized || seen.has(normalized.url)) continue;
    seen.add(normalized.url);
    result.push(normalized);
  }
  return result;
}

// src/doramasyt/index.js
function normalizeMediaType(mediaType) {
  const type = String(mediaType || "").toLowerCase();
  if (type === "movie" || type === "film") return "movie";
  if (type === "tv" || type === "series" || type === "show" || type === "tvseries") return "tv";
  return "tv";
}
function uniqueTitles(values) {
  return [...new Set((Array.isArray(values) ? values : []).map((value) => String(value || "").trim()).filter(Boolean))];
}
function getTitleVariantsSafe(tmdbId, type) {
  return __async(this, null, function* () {
    const variants = [];
    try {
      variants.push(...yield getTmdbTitleVariants(tmdbId, type));
    } catch (error) {
      console.error("[DoramaYT] TMDB variants: " + error.message);
    }
    try {
      variants.push(yield getTmdbTitle(tmdbId, type));
    } catch (error) {
      console.error("[DoramaYT] TMDB fallback title: " + error.message);
    }
    return uniqueTitles(variants).slice(0, 8);
  });
}
function tryVariant(variant, type, season, episode) {
  return __async(this, null, function* () {
    try {
      const detail = yield searchDorama(variant);
      const pageUrl = type === "tv" && episode ? yield getEpisodeUrl(detail, variant, episode) : detail;
      const streams = prepareStreams(yield extractStreams(pageUrl));
      if (streams.length) {
        console.log("[DoramaYT] Streams found using title: " + variant);
        return streams;
      }
    } catch (error) {
      console.error("[DoramaYT] Variant '" + variant + "': " + error.message);
    }
    return [];
  });
}
function getStreams(tmdbId, mediaType, season, episode) {
  return __async(this, null, function* () {
    try {
      const type = normalizeMediaType(mediaType);
      const variants = yield getTitleVariantsSafe(tmdbId, type);
      if (!variants.length) throw new Error("TMDB title not found");
      for (const variant of variants) {
        const streams = yield tryVariant(variant, type, season, episode);
        if (streams.length) return streams;
      }
      console.error("[DoramaYT] No streams found for title variants: " + variants.join(" | "));
      return [];
    } catch (error) {
      console.error("[DoramaYT] " + error.message);
      return [];
    }
  });
}
module.exports = { getStreams };
