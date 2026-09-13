import { request, HEADERS, absoluteUrl } from "./http.js";

function decode(value) {
  return String(value || "")
    .replace(/&amp;/gi, "&")
    .replace(/&quot;|&#34;|&#x22;/gi, '"')
    .replace(/&#39;|&#x27;/gi, "'")
    .replace(/\\u002F/gi, "/")
    .replace(/\\\//g, "/");
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
    headers: { ...HEADERS, Referer: referer }
  });
}

function collectRawCandidates(html) {
  const out = [];
  const re = /(?:src|href|file|source|data-src|data-file|data-video|data-player|data-embed)=["']([^"']+)["']/gi;
  let m;
  while ((m = re.exec(html))) out.push(m[1]);

  const iframe = /<iframe[^>]+src=["']([^"']+)["']/gi;
  while ((m = iframe.exec(html))) out.push(m[1]);

  const jsonish = /"(?:file|url|src|stream|source|playlist|hls|dash)"\s*:\s*"([^"]+)"/gi;
  while ((m = jsonish.exec(html))) out.push(m[1]);

  return out;
}

function isLikelyMedia(url) {
  return /\.(m3u8|mpd|mp4|mkv|webm|m4v|mov|ts|avi|flv|3gp|mpeg|mpg|ogv)(?:$|[?#])/i.test(url);
}

export async function extractStreams(pageUrl, depth = 0, visited = new Set()) {
  if (depth > 3 || visited.has(pageUrl)) return [];
  visited.add(pageUrl);

  const html = await request(pageUrl);
  const direct = [];
  const nested = [];
  const seen = new Set();

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
      const more = await extractStreams(nestedUrl, depth + 1, visited);
      for (const stream of more) {
        if (!seen.has(stream.url)) {
          seen.add(stream.url);
          direct.push(stream);
        }
      }
    } catch (_) {}
  }

  return direct;
}
