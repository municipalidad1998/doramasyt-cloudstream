import { request, absoluteUrl, HEADERS } from "./http.js";
function decode(value) {
  return String(value || "").replace(/&amp;/gi, "&").replace(/&quot;|&#34;|&#x22;/gi, '"').replace(/&#39;|&#x27;/gi, "'").replace(/\\u002F/gi, "/").replace(/\\\//g, "/").trim();
}
function isMedia(url) {
  return /\.(?:m3u8|mpd|mp4|mkv|webm|m4v|mov|ts)(?:$|[?#])/i.test(url) || /(?:manifest|playlist|master)\.(?:m3u8|mpd)/i.test(url);
}
function likelyPlayer(url) {
  try {
    const p = new URL(url), h = p.hostname.toLowerCase(), path = p.pathname.toLowerCase();
    if (/\.(?:js|css|png|jpe?g|gif|svg|webp|woff2?|ttf)(?:$|[?#])/i.test(url)) return false;
    return /(streamtape|dood|filemoon|streamwish|voe|uqload|mixdrop|vidplay|vidhide|filelions|mp4upload)/i.test(h) || /(?:\/embed|\/e\/|\/player|\/reproductor|\/watch|\/stream|\/play|\/servidor|\/video)/i.test(path);
  } catch (_) { return false; }
}
function addCandidate(raw, pageUrl, title, streams, nested, seen) {
  const u = absoluteUrl(decode(raw), pageUrl);
  if (!u || seen.has(u)) return;
  if (isMedia(u)) {
    seen.add(u);
    streams.push({ name: "SoloLatino", title, url: u, quality: /1080/i.test(u) ? "1080p" : /720/i.test(u) ? "720p" : "Auto", headers: { ...HEADERS, Referer: pageUrl } });
  } else if (likelyPlayer(u)) nested.push({ url: u, title });
}
async function ajaxIframes(html, pageUrl) {
  const found = [];
  const re = /data-type=["']([^"']+)["'][^>]*data-post=["']([^"']+)["'][^>]*data-nume=["']([^"']+)["']/gi;
  let m;
  while (m = re.exec(html)) {
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
  if (depth > 6 || visited.has(pageUrl)) return [];
  visited.add(pageUrl);
  const html = await request(pageUrl);
  const streams = [], nested = [], seen = new Set();
  let m;
  for (const iframe of await ajaxIframes(html, pageUrl)) nested.push({ url: iframe, title: "Servidor" });
  const add = (raw, title = "Servidor") => addCandidate(raw, pageUrl, title, streams, nested, seen);
  const attrs = /(?:src|file|source|data-src|data-file|data-video|data-embed|data-url|data-href|data-link|data-player)=\s*["']([^"']+)["']/gi;
  while (m = attrs.exec(html)) add(m[1]);
  const iframes = /<iframe[^>]+src=\s*["']([^"']+)["']/gi;
  while (m = iframes.exec(html)) nested.push({ url: absoluteUrl(m[1], pageUrl), title: "Servidor" });
  const playerCalls = /(?:go_to_player|go_to_playerVast)\s*\(\s*["']([^"']+)["']/gi;
  while (m = playerCalls.exec(html)) add(m[1], "Servidor");
  const phpLinks = /\.php\?link=([^&'"\s]+)&(?:amp;)?servidor=/gi;
  while (m = phpLinks.exec(html)) { try { add(Buffer.from(decode(m[1]), "base64").toString("utf8"), "Servidor"); } catch (_) {} }
  const dataR = /data-r=["']([^"']+)["']/gi;
  while (m = dataR.exec(html)) add(m[1], "Servidor");
  const json = /["'](?:file|url|src|stream|source|playlist|hls|dash|file_url|video_url|stream_url|player_url)["']\s*:\s*["']([^"']+)["']/gi;
  while (m = json.exec(html)) add(m[1]);
  const media = /https?:\/\/[^\s"'<>]+\.(?:m3u8|mpd|mp4|mkv|webm|m4v|mov|ts)(?:\?[^\s"'<>]*)?/gi;
  while (m = media.exec(html)) add(m[0]);
  const uniqueNested = [...new Map(nested.filter(x => x.url).map(x => [x.url, x])).values()].slice(0, 20);
  const results = await Promise.all(uniqueNested.map(x => extractStreams(x.url, depth + 1, visited).catch(() => [])));
  for (const group of results) for (const stream of group) if (!seen.has(stream.url)) { seen.add(stream.url); streams.push(stream); }
  return streams;
}
