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

  const hrefs = [];
  const re = /href=["']([^"']+)["']/gi;
  let m;
  while ((m = re.exec(html))) {
    if (/our-sticky-love|capitulo|episodio|1x1|1-1/i.test(m[1])) hrefs.push(m[1]);
  }
  console.log(`[RAW] matching hrefs=${JSON.stringify(hrefs.slice(0, 50))}`);

  const players = [];
  const pr = /data-player=["']([^"']+)["']/gi;
  while ((m = pr.exec(html))) players.push(m[1]);
  console.log(`[RAW] data-player count=${players.length}`);
  if (players.length) console.log(`[RAW] first data-player=${players[0]}`);

  const important = [];
  const ir = /<(?:iframe|video|source|script)[^>]*>|(?:playother|data-player|data-video|data-embed|m3u8|mp4|filemoon|streamwish|dood|uqload|vidsonic|ok\.ru)[^<\s]*/gi;
  while ((m = ir.exec(html))) important.push(m[0].slice(0, 500));
  console.log(`[RAW] important=${JSON.stringify(important.slice(0, 40))}`);
  return html;
}

async function inspectScript(url) {
  const response = await fetch(url, { headers: HEADERS });
  const text = await response.text();
  console.log(`[SCRIPT] ${url} HTTP=${response.status} bytes=${text.length}`);
  const matches = text.match(/.{0,180}(?:CryptoJS|AES|decrypt|data-player|reproductor|secret|token|fetch\(|axios|\/ajax\/).{0,300}/gi) || [];
  console.log(`[SCRIPT] matches=${JSON.stringify(matches.slice(0, 30))}`);
}

async function main() {
  const tmdbId = process.argv[2] || "291496";
  const mediaType = process.argv[3] || "tv";
  const season = process.argv[4] ? Number(process.argv[4]) : 1;
  const episode = process.argv[5] ? Number(process.argv[5]) : 1;

  console.log(`[TEST] tmdbId=${tmdbId} type=${mediaType} season=${season} episode=${episode}`);
  await inspectPage("https://www.doramasyt.com/");
  await inspectPage("https://www.doramasyt.com/dorama/our-sticky-love-sub-espanol");
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
