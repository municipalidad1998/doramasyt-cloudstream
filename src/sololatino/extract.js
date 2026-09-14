import crypto from "node:crypto";
import { request, absoluteUrl, HEADERS } from "./http.js";

const AES_KEY = "Ak7qrvvH4WKYxV2OgaeHAEg2a5eh16vE";

function decode(value) {
  return String(value || "")
    .replace(/&amp;/gi, "&")
    .replace(/&quot;|&#34;|&#x22;/gi, '"')
    .replace(/&#39;|&#x27;/gi, "'")
    .replace(/\\u002F/gi, "/")
    .replace(/\\\//g, "/")
    .trim();
}

function isMedia(url) {
  return /\.(?:m3u8|mpd|mp4|mkv|webm|m4v|mov|ts)(?:$|[?#])/i.test(url) || /(?:manifest|playlist|master)\.(?:m3u8|mpd)/i.test(url);
}

function likelyPlayer(url) {
  try {
    const p = new URL(url), h = p.hostname.toLowerCase(), path = p.pathname.toLowerCase();
    if (/\.(?:js|css|png|jpe?g|gif|svg|webp|woff2?|ttf)(?:$|[?#])/i.test(url)) return false;
    return /(streamtape|dood|filemoon|streamwish|voe|uqload|mixdrop|vidplay|vidhide|filelions|mp4upload|vembed|guard|bembed|vgfplay|wishembed|strwish|streamgg)/i.test(h) || /(?:\/embed|\/e\/|\/player|\/reproductor|\/watch|\/stream|\/play|\/servidor|\/video)/i.test(path);
  } catch (_) { return false; }
}

function addCandidate(raw, pageUrl, title, streams, nested, seen) {
  const u = absoluteUrl(decode(raw), pageUrl);
  if (!u || seen.has(u)) return;
  if (isMedia(u)) {
    seen.add(u);
    streams.push({ name: "SoloLatino", title, url: u, quality: /1080/i.test(u) ? "1080p" : /720/i.test(u) ? "720p" : "Auto", headers: { ...HEADERS, Referer: pageUrl } });
  } else if (/^https?:/i.test(u)) {
    nested.push({ url: u, title });
  }
}

function stripQuotes(value) {
  const s = String(value || "").trim();
  return ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'"))) ? s.slice(1, -1) : s;
}

function decodeJwtPayload(token) {
  try {
    const parts = String(token).split(".");
    if (parts.length < 2) return null;
    const payload = parts[1].replace(/-/g, "+").replace(/_/g, "/").padEnd(Math.ceil(parts[1].length / 4) * 4, "=");
    return Buffer.from(payload, "base64").toString("utf8");
  } catch (_) { return null; }
}

function tryAesDecode(raw) {
  const value = stripQuotes(String(raw || "").trim());
  if (!value || /^https?:\/\//i.test(value)) return value;
  const variants = [];
  try { variants.push(Buffer.from(value, "base64")); } catch (_) {}
  try { variants.push(Buffer.from(value.replace(/-/g, "+").replace(/_/g, "/"), "base64")); } catch (_) {}
  try { if (/^[0-9a-f]+$/i.test(value) && value.length % 2 === 0) variants.push(Buffer.from(value, "hex")); } catch (_) {}

  for (const data of variants) {
    if (!data || data.length < 16) continue;
    try {
      const iv = data.subarray(0, 16);
      const cipher = data.subarray(16);
      if (cipher.length) {
        const decipher = crypto.createDecipheriv("aes-256-cbc", Buffer.from(AES_KEY, "utf8"), iv);
        return decipher.update(cipher, undefined, "utf8") + decipher.final("utf8");
      }
    } catch (_) {}
    try {
      const decipher = crypto.createDecipheriv("aes-256-ecb", Buffer.from(AES_KEY, "utf8"), null);
      decipher.setAutoPadding(true);
      return decipher.update(data, undefined, "utf8") + decipher.final("utf8");
    } catch (_) {}
  }
  return null;
}

function resolveExpression(raw) {
  let expr = String(raw || "").trim().replace(/;\s*$/, "");
  for (let i = 0; i < 8; i++) {
    expr = expr.trim();
    const call = expr.match(/^(?:window\.)?(?:JSON\.parse|decodeURIComponent|atob)\(([\s\S]*)\)$/i);
    if (!call) break;
    let inner = stripQuotes(call[1]);
    if (/^(?:window\.)?decodeURIComponent\(/i.test(expr)) {
      try { inner = decodeURIComponent(inner); } catch (_) { return null; }
    } else if (/^(?:window\.)?atob\(/i.test(expr)) {
      try { inner = Buffer.from(inner, "base64").toString("utf8"); } catch (_) { return null; }
    }
    expr = inner;
  }
  expr = stripQuotes(expr);
  if (/^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$/.test(expr)) {
    const jwt = decodeJwtPayload(expr);
    if (jwt) expr = jwt;
  }
  try { return JSON.parse(expr); } catch (_) {}
  try {
    const decrypted = tryAesDecode(expr);
    if (decrypted) {
      try { return JSON.parse(stripQuotes(decrypted)); } catch (_) {}
      const jwt = decodeJwtPayload(stripQuotes(decrypted));
      if (jwt) try { return JSON.parse(jwt); } catch (_) {}
    }
  } catch (_) {}
  return null;
}

function collectDataLinkLinks(value, result = []) {
  if (Array.isArray(value)) {
    for (const item of value) collectDataLinkLinks(item, result);
    return result;
  }
  if (!value || typeof value !== "object") return result;
  const type = String(value.type || "").toLowerCase();
  const lang = String(value.video_language || value.language || "").toUpperCase();
  const label = lang === "LAT" ? "[LAT]" : lang === "ESP" ? "[CAST]" : lang === "SUB" ? "[SUB]" : "[UNK]";
  if (!type || type === "video") {
    for (const key of ["link", "url", "embed", "src", "video_url", "stream_url"]) {
      if (typeof value[key] === "string" && value[key].trim()) result.push({ link: value[key], label });
    }
  }
  for (const key of Object.keys(value)) collectDataLinkLinks(value[key], result);
  return result;
}

function dataLinkIframes(html) {
  const result = [];
  const matches = html.match(/dataLink\s*=\s*([^;]+);/gis) || [];
  for (const statement of matches) {
    const raw = statement.replace(/^.*?dataLink\s*=\s*/is, "");
    const payload = resolveExpression(raw);
    if (!payload) continue;
    for (const item of collectDataLinkLinks(payload)) result.push(item);
  }
  return result;
}

async function ajaxIframes(html, pageUrl) {
  const found = [];
  const re = /data-type=["']([^"']+)["'][^>]*data-post=["']([^"']+)["'][^>]*data-nume=["']([^"']+)["']/gi;
  let m;
  while ((m = re.exec(html))) {
    try {
      const body = new URLSearchParams({ action: "doo_player_ajax", post: m[2], nume: m[3], type: m[1] }).toString();
      const response = await request(new URL("/wp-admin/admin-ajax.php", pageUrl).toString(), { method: "POST", headers: { ...HEADERS, "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8", "X-Requested-With": "XMLHttpRequest", Referer: pageUrl }, body });
      const iframe = response.match(/<iframe[^>]+src=["']([^"']+)["']/i);
      if (iframe) found.push(absoluteUrl(iframe[1], pageUrl));
    } catch (_) {}
  }
  const fallback = html.match(/pframe[^>]*>\s*<iframe[^>]+src=["']([^"']+)["']/i);
  if (fallback) found.push(absoluteUrl(fallback[1], pageUrl));
  return [...new Set(found.filter(Boolean))];
}

export async function extractStreams(pageUrl, depth = 0, visited = new Set()) {
  if (depth > 7 || visited.has(pageUrl)) return [];
  visited.add(pageUrl);
  const html = await request(pageUrl);
  const streams = [], nested = [], seen = new Set();

  for (const item of dataLinkIframes(html)) addCandidate(item.link, pageUrl, item.label, streams, nested, seen);
  for (const iframe of await ajaxIframes(html, pageUrl)) nested.push({ url: iframe, title: "Servidor" });

  const add = (raw, title = "Servidor") => addCandidate(raw, pageUrl, title, streams, nested, seen);
  let m;
  const attrs = /(?:src|file|source|data-src|data-file|data-video|data-embed|data-url|data-href|data-link|data-player)=\s*["']([^"']+)["']/gi;
  while ((m = attrs.exec(html))) add(m[1]);
  const iframes = /<iframe[^>]+src=\s*["']([^"']+)["']/gi;
  while ((m = iframes.exec(html))) nested.push({ url: absoluteUrl(m[1], pageUrl), title: "Servidor" });
  const playerCalls = /(?:go_to_player|go_to_playerVast)\s*\(\s*["']([^"']+)["']/gi;
  while ((m = playerCalls.exec(html))) add(m[1], "Servidor");
  const phpLinks = /\.php\?link=([^&'"\s]+)&(?:amp;)?servidor=/gi;
  while ((m = phpLinks.exec(html))) { try { add(Buffer.from(decode(m[1]), "base64").toString("utf8"), "Servidor"); } catch (_) {} }
  const dataR = /data-r=["']([^"']+)["']/gi;
  while ((m = dataR.exec(html))) add(m[1], "Servidor");
  const json = /["'](?:file|url|src|stream|source|playlist|hls|dash|file_url|video_url|stream_url|player_url)["']\s*:\s*["']([^"']+)["']/gi;
  while ((m = json.exec(html))) add(m[1]);
  const media = /https?:\/\/[^\s"'<>]+\.(?:m3u8|mpd|mp4|mkv|webm|m4v|mov|ts)(?:\?[^\s"'<>]*)?/gi;
  while ((m = media.exec(html))) add(m[0]);

  const uniqueNested = [...new Map(nested.filter(x => x.url && /^https?:/i.test(x.url)).map(x => [x.url, x])).values()].slice(0, 30);
  const results = await Promise.all(uniqueNested.map(x => extractStreams(x.url, depth + 1, visited).catch(() => [])));
  for (const group of results) for (const stream of group) if (!seen.has(stream.url)) { seen.add(stream.url); streams.push(stream); }
  return streams;
}
