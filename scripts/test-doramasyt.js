const { getStreams } = require("../providers/doramasyt.js");

const HEADERS = {
  "User-Agent": "Mozilla/5.0 (Android 13) AppleWebKit/537.36 Chrome/122 Mobile Safari/537.36",
  "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
  "Accept-Language": "es-ES,es;q=0.9,en;q=0.5"
};

async function inspectPage(url) {
  const response = await fetch(url, { headers: HEADERS });
  const html = await response.text();
  console.log(`[RAW] ${url} HTTP=${response.status} bytes=${html.length}`);
  const players = [...html.matchAll(/data-player=["']([^"']+)["']/gi)].map(x => x[1]);
  console.log(`[RAW] data-player count=${players.length}`);
  if (players.length) console.log(`[RAW] first data-player=${players[0]}`);
  const playerDiv = html.match(/<[^>]*class=["'][^"']*player[^"']*["'][^>]*>/i);
  console.log(`[RAW] player-container=${playerDiv ? playerDiv[0] : ""}`);
  const button = html.match(/<button[^>]*data-player=["'][^"']+["'][^>]*>/i);
  console.log(`[RAW] first-player-button=${button ? button[0] : ""}`);
  return html;
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
  await inspectPage("https://www.doramasyt.com/ver/our-sticky-love-episodio-1");
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
