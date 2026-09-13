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

  const key = (html.match(/<[^>]*class=["'][^"']*player[^"']*["'][^>]*data-key=["']([^"']+)["']/i) || [])[1] ||
    (html.match(/<[^>]*data-key=["']([^"']+)["'][^>]*class=["'][^"']*player/i) || [])[1] || "";
  const firstButton = html.match(/<button[^>]*data-player=["']([^"']+)["'][^>]*data-usa-api=["']([^"']+)["'][^>]*>/i);
  console.log(`[RAW] playerKey=${key}`);
  console.log(`[RAW] firstButton=${firstButton ? firstButton[0] : ""}`);

  if (key && firstButton) {
    const playerNameMatch = firstButton[0].match(/>([^<]+)</);
    const playerName = playerNameMatch ? playerNameMatch[1].trim() : "Filemoon";
    const playerUrl = key + firstButton[1] + "&player=" + encodeURIComponent(playerName);
    console.log(`[RAW] constructedPlayer=${playerUrl}`);
    const playerResponse = await fetch(playerUrl, { headers: { ...HEADERS, Referer: url } });
    const playerHtml = await playerResponse.text();
    console.log(`[PLAYER] HTTP=${playerResponse.status} bytes=${playerHtml.length} final=${playerResponse.url}`);
    console.log(`[PLAYER] urls=${JSON.stringify([...playerHtml.matchAll(/https?:[^\s"'<>]+/gi)].map(x => x[0]).slice(0, 20))}`);
    console.log(`[PLAYER] iframes=${JSON.stringify([...playerHtml.matchAll(/<iframe[^>]+src=["']([^"']+)["']/gi)].map(x => x[1]).slice(0, 20))}`);
    console.log(`[PLAYER] media=${JSON.stringify([...playerHtml.matchAll(/https?:[^\s"'<>]+\.(?:m3u8|mp4|mpd|mkv)[^\s"'<>]*/gi)].map(x => x[0]).slice(0, 20))}`);
  }
}

async function inspectScript(url) {
  const response = await fetch(url, { headers: HEADERS });
  const text = await response.text();
  console.log(`[SCRIPT] ${url} HTTP=${response.status} bytes=${text.length}`);
  console.log(`[SCRIPT] player construction=${(text.match(/var player_url[\s\S]{0,700}/i) || [""])[0]}`);
}

async function main() {
  const tmdbId = process.argv[2] || "291496";
  const mediaType = process.argv[3] || "tv";
  const season = process.argv[4] ? Number(process.argv[4]) : 1;
  const episode = process.argv[5] ? Number(process.argv[5]) : 1;

  console.log(`[TEST] tmdbId=${tmdbId} type=${mediaType} season=${season} episode=${episode}`);
  await inspectEpisode("https://www.doramasyt.com/ver/our-sticky-love-episodio-1");
  await inspectScript("https://www.doramasyt.com/js/capitulo.js?v=1775974031");

  const streams = await getStreams(tmdbId, mediaType, season, episode);
  console.log(`[TEST] STREAM_COUNT=${streams.length}`);
  for (const stream of streams) {
    console.log(`[TEST] STREAM name=${stream.name} title=${stream.title} quality=${stream.quality} url=${stream.url}`);
  }
  if (!streams.length) process.exitCode = 2;
}

main().catch((error) => {
  console.error("[TEST] FATAL", error && error.stack ? error.stack : error);
  process.exitCode = 1;
});
