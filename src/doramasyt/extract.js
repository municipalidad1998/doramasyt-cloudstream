import { request, HEADERS, absoluteUrl } from "./http.js";

function decode(value) {
  return String(value || "")
    .replace(/&amp;/gi, "&")
    .replace(/&quot;|&#34;|&#x22;/gi, '"')
    .replace(/&#39;|&#x27;/gi, "'")
    .replace(/\\u002F/gi, "/")
    .replace(/\\\//g, "/")
    .trim();
}

function base64ToText(value) {
  try {
    const input = String(value || "").replace(/\s/g, "");
    if (!input || !/^[A-Za-z0-9+/=_-]+$/.test(input)) return "";
    if (typeof atob === "function") {
      const binary = atob(input.replace(/-/g, "+").replace(/_/g, "/"));
      let out = "";
      for (let i = 0; i < binary.length; i++) out += String.fromCharCode(binary.charCodeAt(i));
      try { return decodeURIComponent(escape(out)); } catch (_) { return out; }
    }
  } catch (_) {}
  return "";
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
    headers: { ...HEADERS, Referer: referer }
  });
}

function collectRawCandidates(html) {
  const out = [];
  let m;

  const attrs = /(?:src|href|file|source|data-src|data-file|data-video|data-embed)=["']([^"']+)["']/gi;
  while ((m = attrs.exec(html))) out.push({ value: m[1], nested: true });

  const players = /data-player=["']([^"']+)["']/gi;
  while ((m = players.exec(html))) {
    const decoded = base64ToText(m[1]);
    if (decoded) out.push({ value: decoded, nested: true });
  }

  const iframe = /<iframe[^>]+src=["']([^"']+)["']/gi;
  while ((m = iframe.exec(html))) out.push({ value: m[1], nested: true });

  const jsonish = /["'](?:file|url|src|stream|source|playlist|hls|dash)["']\s*:\s*["']([^"']+)["']/gi;
  while ((m = jsonish.exec(html))) out.push({ value: m[1], nested: true });

  const jw = /(?:file|src)\s*:\s*["'](https?:\/\/[^"']+)["']/gi;
  while ((m = jw.exec(html))) out.push({ value: m[1], nested: true });

  return out;
}

function isLikelyMedia(url) {
  return /\.(m3u8|mpd|mp4|mkv|webm|m4v|mov|ts|avi|flv|3gp|mpeg|mpg|ogv)(?:$|[?#])/i.test(url) ||
    /(?:\.m3u8\?|\.mpd\?|manifest(?:\.m3u8)?|playlist(?:\.m3u8)?)/i.test(url);
}

function isUsefulNested(url) {
  return /(?:voe|filemoon|moonplayer|streamwish|strwish|wishembed|wishfast|dood|doodstream|ds2play|filelions|mixdrop|streamtape|ok\.ru|okru|uqload|vidmoly|vidhide|vidplay|embed|player|stream)/i.test(url);
}

export async function extractStreams(pageUrl, depth = 0, visited = new Set()) {
  if (depth > 4 || visited.has(pageUrl)) return [];
  visited.add(pageUrl);

  const html = await request(pageUrl);
  const direct = [];
  const nested = [];
  const seen = new Set();

  for (const candidate of collectRawCandidates(html)) {
    const u = absoluteUrl(decode(candidate.value));
    if (!u) continue;
    if (isLikelyMedia(u)) {
      addUrl(direct, seen, u, pageUrl);
    } else if (candidate.nested && depth < 4 && isUsefulNested(u)) {
      nested.push(u);
    }
  }

  for (const nestedUrl of [...new Set(nested)].slice(0, 12)) {
    try {
      const more = await extractStreams(nestedUrl, depth + 1, visited);
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
