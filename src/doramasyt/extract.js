import { request, HEADERS, absoluteUrl } from "./http.js";

function decode(value) {
  let out = String(value || "")
    .replace(/&amp;/gi, "&")
    .replace(/&quot;|&#34;|&#x22;/gi, '"')
    .replace(/&#39;|&#x27;/gi, "'")
    .replace(/\\u002F/gi, "/")
    .replace(/\\\//g, "/")
    .trim();
  try { out = decodeURIComponent(out); } catch (_) {}
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
    try { return decodeURIComponent(escape(out)); } catch (_) { return out; }
  } catch (_) { return ""; }
}

// CloudStream uses JsUnpacker for many external hosts. Port the same
// P.A.C.K.E.R. decoder so Nuvio can see URLs hidden inside eval(function(p,a,c,k,e,d)).
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
  if (radix > 36) {
    if (radix <= 62) alphabet = alphabet62.slice(0, radix);
    else if (radix <= 95) alphabet = alphabet95.slice(0, radix);
  }

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
    return Number.isInteger(index) && index >= 0 && index < symtab.length && symtab[index]
      ? symtab[index]
      : word;
  });
}

function addUrl(out, seen, url, referer, title = "Servidor") {
  if (!url) return;
  let u = decode(url).replace(/["'<>),;]+$/g, "");
  if (u.startsWith("//")) u = "https:" + u;
  if (!/^https?:\/\//i.test(u) || seen.has(u)) return;
  seen.add(u);
  out.push({
    name: "DoramaYT",
    title,
    url: u,
    quality: /(?:2160|4k)/i.test(u) ? "2160p" : /1080/i.test(u) ? "1080p" : /720/i.test(u) ? "720p" : /480/i.test(u) ? "480p" : "Auto",
    headers: { ...HEADERS, Referer: referer }
  });
}

function unwrapPlayer(url) {
  const value = decode(url);
  if (!/\/reproductor\?url=/i.test(value)) return "";
  const match = value.match(/[?&]url=([^#]+)$/i);
  if (!match) return "";
  try { return decodeURIComponent(match[1]); } catch (_) { return match[1]; }
}

function collectRawCandidates(html) {
  const out = [];
  let m;

  const attrs = /(?:src|href|file|source|data-src|data-file|data-video|data-embed|data-url)=\s*["']([^"']+)["']/gi;
  while ((m = attrs.exec(html))) out.push({ value: m[1], nested: true });

  const players = /data-player=\s*["']([^"']+)["']/gi;
  while ((m = players.exec(html))) {
    const decoded = base64ToText(m[1]);
    const player = decoded || m[1];
    const target = unwrapPlayer(player);
    if (target) out.push({ value: target, nested: true });
    out.push({ value: player, nested: true });
  }

  const iframe = /<iframe[^>]+src=\s*["']([^"']+)["']/gi;
  while ((m = iframe.exec(html))) out.push({ value: m[1], nested: true });

  const jsonish = /["'](?:file|url|src|stream|source|playlist|hls|dash|file_url|video_url|stream_url)["']\s*:\s*["']([^"']+)["']/gi;
  while ((m = jsonish.exec(html))) out.push({ value: m[1], nested: true });

  const jsQuoted = /(?:file|src|source|stream|playlist|hls|dash)\s*[:=]\s*["'](https?:\/\/[^"']+)["']/gi;
  while ((m = jsQuoted.exec(html))) out.push({ value: m[1], nested: true });

  const mediaUrls = /https?:\\?\/\\?\/[^\s"'<>]+\.(?:m3u8|mpd|mp4|mkv|webm|m4v|mov|ts)(?:\?[^\s"'<>]*)?/gi;
  while ((m = mediaUrls.exec(html))) out.push({ value: m[0], nested: false });

  // Parse normal and packed script bodies. Packed scripts are common on
  // Filemoon/StreamWish-style hosts and otherwise hide the actual media URL.
  const scripts = /<script\b[^>]*>([\s\S]*?)<\/script>/gi;
  while ((m = scripts.exec(html))) {
    const body = m[1] || "";
    out.push({ value: body, nested: false, script: true });
    const unpacked = unpackPacker(body);
    if (unpacked) out.push({ value: unpacked, nested: false, script: true });
  }

  return out;
}

function isLikelyMedia(url) {
  return /\.(m3u8|mpd|mp4|mkv|webm|m4v|mov|ts|avi|flv|3gp|mpeg|mpg|ogv)(?:$|[?#])/i.test(url) ||
    /(?:\.m3u8\?|\.mpd\?|manifest(?:\.m3u8)?|playlist(?:\.m3u8)?|master\.txt)/i.test(url);
}

function isUsefulNested(url) {
  try {
    const host = new URL(url).hostname.toLowerCase();
    if (!host) return false;
    if (/^(www\.)?doramasyt\.com$/i.test(host)) return /(?:reproductor|player|stream|video)/i.test(url);
    if (/\.(?:js|css|png|jpe?g|gif|svg|webp|woff2?|ttf)(?:$|[?#])/i.test(url)) return false;
    return true;
  } catch (_) {
    return false;
  }
}

export async function extractStreams(pageUrl, depth = 0, visited = new Set(), parentReferer = "https://www.doramasyt.com/") {
  if (depth > 5 || visited.has(pageUrl)) return [];
  visited.add(pageUrl);

  const html = await request(pageUrl, { headers: { Referer: parentReferer } });
  const direct = [];
  const nested = [];
  const seen = new Set();

  for (const candidate of collectRawCandidates(html)) {
    const raw = decode(candidate.value);
    if (candidate.script) {
      const unpacked = unpackPacker(raw);
      if (unpacked) {
        for (const value of [raw, unpacked]) {
          const media = value.match(/https?:\\?\/\\?\/[^\s"'<>]+(?:m3u8|mpd|mp4|mkv|webm|m4v|mov|ts)(?:\?[^\s"'<>]*)?/gi) || [];
          for (const url of media) addUrl(direct, seen, url, pageUrl, "Servidor");
        }
      }
      continue;
    }

    const unwrapped = unwrapPlayer(raw);
    const values = unwrapped ? [unwrapped, raw] : [raw];
    for (const value of values) {
      const u = absoluteUrl(value, pageUrl);
      if (!u) continue;
      if (isLikelyMedia(u)) {
        addUrl(direct, seen, u, pageUrl);
      } else if (candidate.nested && depth < 5 && isUsefulNested(u)) {
        nested.push(u);
      }
    }
  }

  for (const nestedUrl of [...new Set(nested)].slice(0, 20)) {
    try {
      const more = await extractStreams(nestedUrl, depth + 1, visited, pageUrl);
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
}
