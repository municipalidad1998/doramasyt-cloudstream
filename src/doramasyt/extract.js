import { request, HEADERS, absoluteUrl } from "./http.js";

function decode(value) {
  let out = String(value || "").replace(/&amp;/gi, "&").replace(/&quot;|&#34;|&#x22;/gi, '"').replace(/&#39;|&#x27;/gi, "'").replace(/\\u002F/gi, "/").replace(/\\\//g, "/").trim();
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
  } catch (_) { return; }
  if (!/^https?:\/\//i.test(u) || seen.has(u)) return;
  seen.add(u);
  out.push({ name: "DoramaYT", title, url: u, quality: /(?:2160|4k)/i.test(u) ? "2160p" : /1080/i.test(u) ? "1080p" : /720/i.test(u) ? "720p" : /480/i.test(u) ? "480p" : "Auto", headers: { ...HEADERS, Referer: referer } });
}

function unwrapPlayer(url) {
  const value = decode(url);
  const match = value.match(/[?&]url=([^#]+)$/i);
  if (!match) return "";
  try { return decodeURIComponent(match[1]); } catch (_) { return match[1]; }
}

function collectRawCandidates(html) {
  const out = [];
  let m;
  const playerKeyMatch = html.match(/<[^>]*class=["'][^"']*player[^"']*["'][^>]*data-key=["']([^"']+)["']/i) || html.match(/<[^>]*data-key=["']([^"']+)["'][^>]*class=["'][^"']*player/i);
  const playerKey = playerKeyMatch ? playerKeyMatch[1] : "";
  if (playerKey) {
    const buttons = /<button[^>]*data-player=["']([^"']+)["'][^>]*data-usa-api=["']([^"']+)["'][^>]*>([\s\S]*?)<\/button>/gi;
    while ((m = buttons.exec(html))) {
      const label = (m[3] || "").replace(/<[^>]+>/g, " ").replace(/\s+/g, " ").trim() || "Servidor";
      const playerUrl = m[2] === "1" ? playerKey + m[1] + "&player=" + encodeURIComponent(label) : m[1];
      out.push({ value: playerUrl, nested: true, player: true, title: label });
    }
  }
  const attrs = /(?:src|file|source|data-src|data-file|data-video|data-embed|data-url)=\s*["']([^"']+)["']/gi;
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
  try {
    const parsed = new URL(url);
    if (/\.(?:mp4|m3u8|mpd|mkv|webm)$/i.test(parsed.hostname)) return false;
    if (/(?:streamtape\.com|mp4upload\.com|savefiles\.com|bysekoze\.com)/i.test(parsed.hostname) && /(?:\/e\/|\/embed[-/])/i.test(parsed.pathname)) return false;
  } catch (_) { return false; }
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
  } catch (_) { return false; }
}

async function resolveDood(url, referer) {
  if (!/dood(?:stream)?\.|dood\./i.test(url)) return [];
  try {
    const embedUrl = url.replace(/\/d\//i, "/e/");
    const html = await request(embedUrl, { headers: { Referer: referer } });
    const host = new URL(embedUrl).origin;
    const pass = html.match(/\/pass_md5\/[^'"\s<]+/i);
    if (!pass) return [];
    const passUrl = new URL(pass[0], host).toString();
    const token = passUrl.split("/").pop();
    const base = await request(passUrl, { headers: { Referer: embedUrl } });
    if (!base || !/^https?:\/\//i.test(base.trim())) return [];
    return [{ url: base.trim() + Math.random().toString(36).slice(2, 12) + "?token=" + token, referer: host + "/", title: "DoodStream" }];
  } catch (error) { console.error("[DoramaYT] Dood resolver: " + error.message); return []; }
}

async function resolveStreamTape(url, referer) {
  if (!/streamtape\./i.test(url)) return [];
  try {
    const html = await request(url, { headers: { Referer: referer } });
    const bot = html.match(/id=["']botlink["'][^>]*>([^<]+)<\/[^>]+>/i);
    if (!bot) return [];
    let stream = bot[1].trim();
    if (stream.startsWith("//")) stream = "https:" + stream;
    else if (/^\/streamtape\.com\//i.test(stream)) stream = "https://" + stream.replace(/^\//, "");
    else if (stream.startsWith("/")) stream = "https://streamtape.com" + stream;
    if (!/^https?:\/\//i.test(stream)) return [];
    if (!/[?&]stream=1(?:&|$)/i.test(stream)) stream += (stream.includes("?") ? "&" : "?") + "stream=1";
    return [{ url: stream, referer: url, title: "StreamTape" }];
  } catch (error) { console.error("[DoramaYT] StreamTape resolver: " + error.message); return []; }
}

export async function extractStreams(pageUrl, depth = 0, visited = new Set(), parentReferer = "https://www.doramasyt.com/") {
  if (depth > 5 || visited.has(pageUrl)) return [];
  visited.add(pageUrl);
  const knownDood = await resolveDood(pageUrl, parentReferer);
  if (knownDood.length) return knownDood.map(item => ({ name: "DoramaYT", title: item.title, url: item.url, quality: "Auto", headers: { ...HEADERS, Referer: item.referer } }));
  const knownTape = await resolveStreamTape(pageUrl, parentReferer);
  if (knownTape.length) return knownTape.map(item => ({ name: "DoramaYT", title: item.title, url: item.url, quality: "Auto", headers: { ...HEADERS, Referer: item.referer } }));

  const html = await request(pageUrl, { headers: { Referer: parentReferer } });
  const direct = [];
  const nested = [];
  const seen = new Set();
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
  for (const nestedUrl of [...new Set(nested)].slice(0, 16)) {
    try {
      const more = await extractStreams(nestedUrl, depth + 1, visited, pageUrl);
      for (const stream of more) if (!seen.has(stream.url)) { seen.add(stream.url); direct.push(stream); }
    } catch (error) { console.error("[DoramaYT] Nested extractor: " + error.message); }
  }
  return direct;
}
