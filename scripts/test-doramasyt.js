const { getStreams } = require("../providers/doramasyt.js");

const HEADERS = {
  "User-Agent": "Mozilla/5.0 (Android 13) AppleWebKit/537.36 Chrome/122 Mobile Safari/537.36",
  "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
  "Accept-Language": "es-ES,es;q=0.9,en;q=0.5"
};

async function inspectEpisode(url) {
  const response = await fetch(url, { headers: HEADERS });
  const html = await response.text();
  console.log(`[RAW] ${url} HTTP=${response.status} bytes=${html.length}`);
  const key = (html.match(/<[^>]*class=["'][^"']*player[^"']*["'][^>]*data-key=["']([^"']+)["']/i) || [])[1] || "";
  console.log(`[RAW] playerKey=${key}`);
  const buttonRe = /<button[^>]*data-player=["']([^"']+)["'][^>]*data-usa-api=["']([^"']+)["'][^>]*>([\s\S]*?)<\/button>/gi;
  const buttons = [...html.matchAll(buttonRe)];
  console.log(`[RAW] player buttons=${buttons.length}`);
  for (const match of buttons) {
    const label = (match[3].replace(/<[^>]+>/g, " ").replace(/\s+/g, " ").trim()) || "Servidor";
    const playerUrl = match[2] === "1" ? key + match[1] + "&player=" + encodeURIComponent(label) : match[1];
    try {
      const r = await fetch(playerUrl, { headers: { ...HEADERS, Referer: url } });
      const body = await r.text();
      const iframes = [...body.matchAll(/<iframe[^>]+src=["']([^"']+)["']/gi)].map(x => x[1]);
      const media = [...body.matchAll(/https?:[^\s"'<>]+\.(?:m3u8|mp4|mpd|mkv|webm)[^\s"'<>]*/gi)].map(x => x[0]);
      console.log(`[PLAYER] ${label} status=${r.status} bytes=${body.length}`);
      console.log(`[PLAYER] ${label} iframes=${JSON.stringify(iframes.slice(0, 10))}`);
      console.log(`[PLAYER] ${label} media=${JSON.stringify(media.slice(0, 10))}`);
    } catch (error) {
      console.log(`[PLAYER] ${label} ERROR=${error.message}`);
    }
  }
}

async function main() {
  const tmdbId = process.argv[2] || "291496";
  const mediaType = process.argv[3] || "tv";
  const season = process.argv[4] ? Number(process.argv[4]) : 1;
  const episode = process.argv[5] ? Number(process.argv[5]) : 1;
  console.log(`[TEST] tmdbId=${tmdbId} type=${mediaType} season=${season} episode=${episode}`);
  await inspectEpisode("https://www.doramasyt.com/ver/our-sticky-love-episodio-1");
  const streams = await getStreams(tmdbId, mediaType, season, episode);
  console.log(`[TEST] STREAM_COUNT=${streams.length}`);
  for (const stream of streams) console.log(`[TEST] STREAM name=${stream.name} title=${stream.title} quality=${stream.quality} url=${stream.url}`);
  if (!streams.length) process.exitCode = 2;
}
main().catch((error) => { console.error("[TEST] FATAL", error && error.stack ? error.stack : error); process.exitCode = 1; });
