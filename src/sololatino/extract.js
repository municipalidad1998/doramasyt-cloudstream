import { request, absoluteUrl, HEADERS } from "./http.js";
function decode(value) {
  return String(value || "").replace(/&amp;/gi, "&").replace(/&quot;|&#34;|&#x22;/gi, '"')
    .replace(/&#39;|&#x27;/gi, "'").replace(/\\u002F/gi, "/").replace(/\\\//g, "/").trim();
}
function isMedia(url) {
  return /\.(?:m3u8|mpd|mp4|mkv|webm|m4v|mov|ts)(?:$|[?#])/i.test(url)
    || /(?:manifest|playlist|master)\.(?:m3u8|mpd)/i.test(url);
}
function likelyPlayer(url) {
  try {
    const p = new URL(url);
    const h = p.hostname.toLowerCase(), path = p.pathname.toLowerCase();
    if (/\.(?:js|css|png|jpe?g|gif|svg|webp|woff2?|ttf)(?:$|[?#])/i.test(url)) return false;
    return /(streamtape|dood|filemoon|streamwish|voe|uqload|mixdrop|vidplay|vidhide|filelions|mp4upload)/i.test(h)
      || /(?:\/embed|\/e\/|\/player|\/reproductor|\/watch|\/stream|\/play|\/servidor|\/video)/i.test(path);
  } catch (_) { return false; }
}
export async function extractStreams(pageUrl, depth = 0, visited = new Set()) {
  if (depth > 5 || visited.has(pageUrl)) return [];
  visited.add(pageUrl);
  const html = await request(pageUrl);
  const streams = [], nested = [], seen = new Set();
  const add = (raw, title = "Servidor") => {
    const u = absoluteUrl(decode(raw), pageUrl);
    if (!u || seen.has(u)) return;
    if (isMedia(u)) {
      seen.add(u);
      streams.push({
        name: "SoloLatino", title, url: u,
        quality: /1080/i.test(u) ? "1080p" : /720/i.test(u) ? "720p" : "Auto",
        headers: { ...HEADERS, Referer: pageUrl }
      });
    } else if (likelyPlayer(u)) nested.push({ url: u, title });
  };
  let m;
  const attrs = /(?:src|file|source|data-src|data-file|data-video|data-embed|data-url|data-href|data-link|data-player)=\s*["']([^"']+)["']/gi;
  while (m = attrs.exec(html)) add(m[1]);
  const iframes = /<iframe[^>]+src=\s*["']([^"']+)["']/gi;
  while (m = iframes.exec(html)) nested.push({ url: absoluteUrl(m[1], pageUrl), title: "Servidor" });
  const playerCalls = /(?:go_to_player|go_to_playerVast)\s*\(\s*["']([^"']+)["']/gi;
  while (m = playerCalls.exec(html)) add(m[1], "Servidor");
  const onclickUrls = /(?:onclick|onload)=\s*["'][^"']*(?:https?:)?(\/\/|\/)[^"']+["']/gi;
  while (m = onclickUrls.exec(html)) {
    const raw = m[0].match(/(https?:\/\/[^'"\s]+|\/[^'"\s)]+\b)/i);
    if (raw) add(raw[1], "Servidor");
  }
  const json = /["'](?:file|url|src|stream|source|playlist|hls|dash|file_url|video_url|stream_url|player_url)["']\s*:\s*["']([^"']+)["']/gi;
  while (m = json.exec(html)) add(m[1]);
  const media = /https?:\/\/[^\s"'<>]+\.(?:m3u8|mpd|mp4|mkv|webm|m4v|mov|ts)(?:\?[^\s"'<>]*)?/gi;
  while (m = media.exec(html)) add(m[0]);
  const uniqueNested = [...new Map(nested.filter(x => x.url).map(x => [x.url, x])).values()].slice(0, 20);
  const results = await Promise.all(uniqueNested.map(x => extractStreams(x.url, depth + 1, visited).catch(() => [])));
  for (const group of results) for (const stream of group)
    if (!seen.has(stream.url)) { seen.add(stream.url); streams.push(stream); }
  return streams;
}
