const { getStreams } = require("../providers/doramasyt.js");
const HEADERS = {"User-Agent":"Mozilla/5.0 (Android 13) AppleWebKit/537.36 Chrome/122 Mobile Safari/537.36","Accept":"text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8","Accept-Language":"es-ES,es;q=0.9,en;q=0.5"};

async function inspectEpisode(url) {
  const html = await (await fetch(url,{headers:HEADERS})).text();
  const key = (html.match(/<[^>]*class=["'][^"']*player[^"']*["'][^>]*data-key=["']([^"']+)["']/i)||[])[1]||"";
  const buttons = [...html.matchAll(/<button[^>]*data-player=["']([^"']+)["'][^>]*data-usa-api=["']([^"']+)["'][^>]*>([\s\S]*?)<\/button>/gi)];
  console.log(`[RAW] playerKey=${key} buttons=${buttons.length}`);
  for(const m of buttons){
    const label=(m[3].replace(/<[^>]+>/g," ").replace(/\s+/g," ").trim())||"Servidor";
    const playerUrl=m[2]==="1"?key+m[1]+"&player="+encodeURIComponent(label):m[1];
    const body=await (await fetch(playerUrl,{headers:{...HEADERS,Referer:url}})).text();
    const ifr=[...body.matchAll(/<iframe[^>]+src=["']([^"']+)["']/gi)].map(x=>x[1]);
    console.log(`[PLAYER] ${label} iframes=${JSON.stringify(ifr.slice(0,5))}`);
    for(const iframe of ifr.filter(x=>/streamtape/i.test(x))){
      const b=await (await fetch(iframe,{headers:{...HEADERS,Referer:playerUrl}})).text();
      console.log(`[STREAMTAPE] bytes=${b.length}`);
      console.log(`[STREAMTAPE] botlink=${JSON.stringify((b.match(/.{0,300}botlink.{0,800}/gi)||[]).slice(0,5))}`);
    }
  }
}

async function main(){
  const tmdbId=process.argv[2]||"291496", mediaType=process.argv[3]||"tv", season=Number(process.argv[4]||1), episode=Number(process.argv[5]||1);
  console.log(`[TEST] ${tmdbId} ${mediaType} S${season}E${episode}`);
  await inspectEpisode("https://www.doramasyt.com/ver/our-sticky-love-episodio-1");
  const streams=await getStreams(tmdbId,mediaType,season,episode);
  console.log(`[TEST] STREAM_COUNT=${streams.length}`);
  for(const s of streams) console.log(`[TEST] STREAM ${s.title} ${s.url}`);
  if(!streams.length) process.exitCode=2;
}
main().catch(e=>{console.error(e.stack||e);process.exitCode=1;});
