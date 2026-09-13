import { request, clean, absoluteUrl } from "./http.js";

export async function getTmdbTitle(tmdbId, mediaType) {
  const type = mediaType === "tv" || mediaType === "series" ? "tv" : "movie";
  const html = await request("https://www.themoviedb.org/" + type + "/" + tmdbId);
  const meta = html.match(/<meta[^>]+property=["']og:title["'][^>]+content=["']([^"']+)/i);
  const title = meta ? meta[1] : ((html.match(/<title>([^<]+)/i) || [])[1] || "");
  const result = clean(title).replace(/\s*\|\s*TMDB.*$/i, "").trim();
  if (!result) throw new Error("TMDB title not found");
  return result;
}

export async function searchDorama(title) {
  const html = await request("https://www.doramasyt.com/?s=" + encodeURIComponent(title));
  const links = [];
  const seen = new Set();
  const re = /<a[^>]+href=["']([^"']+)["'][^>]*>/gi;
  let m;
  while ((m = re.exec(html))) {
    const href = absoluteUrl(m[1]).split("?")[0].split("#")[0];
    if (!href.startsWith("https://www.doramasyt.com/")) continue;
    if (/\/(category|tag|page|author|feed|wp-|login|register|contacto|dmca)\//i.test(href)) continue;
    if (seen.has(href)) continue;
    seen.add(href);
    const last = href.replace(/\/$/, "").split("/").pop() || "";
    if (last.length > 2) links.push({ href, slug: last.toLowerCase() });
  }
  if (!links.length) throw new Error("DoramaYT title not found");
  const wanted = title.toLowerCase().replace(/[^a-z0-9]+/gi, " ").trim();
  links.sort((a,b) => {
    const score = (x) => x.slug === wanted.replace(/\s+/g,"-") ? 0 : x.slug.includes(wanted.replace(/\s+/g,"-")) ? 1 : 5;
    return score(a.slug)-score(b.slug);
  });
  return links[0].href;
}

export async function getEpisodeUrl(detailUrl, episode) {
  if (!episode) return detailUrl;
  const html = await request(detailUrl);
  const re = /href=["']([^"']+)["']/gi;
  let m;
  const candidates = [];
  while ((m = re.exec(html))) candidates.push(absoluteUrl(m[1]));
  for (const href of candidates) {
    const tail = href.split("?")[0].split("/").pop() || "";
    if (new RegExp("(?:episode|episodio|capitulo|capítulo|ep)[^0-9]{0,8}0*" + episode + "(?:[^0-9]|$)","i").test(tail)) return href;
    if (new RegExp("(^|[^0-9])0*" + episode + "([^0-9]|$)").test(tail) && /ver|episode|episodio|capitulo|watch/i.test(href)) return href;
  }
  return detailUrl;
}
