const { getStreams } = require("../providers/doramasyt.js");

async function main() {
  const tmdbId = process.argv[2] || "291496";
  const mediaType = process.argv[3] || "tv";
  const season = process.argv[4] ? Number(process.argv[4]) : 1;
  const episode = process.argv[5] ? Number(process.argv[5]) : 1;

  console.log(`[TEST] tmdbId=${tmdbId} type=${mediaType} season=${season} episode=${episode}`);
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
