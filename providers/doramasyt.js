const BASE_URL = 'https://www.doramasyt.com';
const UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/122 Safari/537.36';

function get(url, json) {
  return fetch(url, {
    headers: {
      'User-Agent': UA,
      'Accept': json ? 'application/json' : 'text/html,application/xhtml+xml,*/*;q=0.8',
      'Accept-Language': 'es-ES,es;q=0.9,en;q=0.5',
      'Referer': BASE_URL + '/'
    },
    redirect: 'follow'
  }).then(function (r) {
    if (!r.ok) throw new Error('HTTP ' + r.status);
    return json ? r.json() : r.text();
  });
}

function clean(s) {
  return (s || '').replace(/<[^>]+>/g, ' ').replace(/&amp;/g, '&').replace(/&quot;/g, '"').replace(/&#39;/g, "'").replace(/\s+/g, ' ').trim();
}

function slug(s) {
  return clean(s).toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');
}

function findTitleFromTmdb(tmdbId, mediaType) {
  var type = mediaType === 'tv' || mediaType === 'series' ? 'tv' : 'movie';
  return get('https://www.themoviedb.org/' + type + '/' + tmdbId, false).then(function (html) {
    var m = html.match(/<meta[^>]+property=["']og:title["'][^>]+content=["']([^"']+)/i);
    if (!m) m = html.match(/<title>([^<]+)/i);
    if (!m) throw new Error('TMDB title not found');
    return clean(m[1]).replace(/\s*\|\s*TMDB.*$/i, '').trim();
  });
}

function searchSite(title) {
  return get(BASE_URL + '/?s=' + encodeURIComponent(title), false).then(function (html) {
    var links = [];
    var re = /<a[^>]+href=["']([^"']+)["'][^>]*>/gi;
    var m;
    while ((m = re.exec(html)) !== null) {
      var href = m[1];
      if (href.charAt(0) === '/') href = BASE_URL + href;
      if (href.indexOf(BASE_URL) !== 0) continue;
      if (/\/wp-|\/category\/|\/tag\/|\/page\//i.test(href)) continue;
      var s = slug(href.split('/').filter(Boolean).pop());
      if (s && s.indexOf('-') !== -1) links.push({ href: href.split('?')[0], slug: s });
    }
    var wanted = slug(title);
    links.sort(function (a, b) {
      var aa = a.slug === wanted ? 0 : (a.slug.indexOf(wanted) === 0 ? 1 : 2);
      var bb = b.slug === wanted ? 0 : (b.slug.indexOf(wanted) === 0 ? 1 : 2);
      return aa - bb;
    });
    if (!links.length) throw new Error('DoramaYT result not found');
    return links[0].href;
  });
}

function episodeUrl(detailUrl, episode) {
  if (!episode) return Promise.resolve(detailUrl);
  return get(detailUrl, false).then(function (html) {
    var re = /href=["']([^"']+)["'][^>]*>/gi;
    var m;
    while ((m = re.exec(html)) !== null) {
      var href = m[1];
      if (href.charAt(0) === '/') href = BASE_URL + href;
      if (href.indexOf(BASE_URL) !== 0) continue;
      if (new RegExp('(?:episode|episodio|capitulo|capítulo|ep)[^0-9]{0,5}0*' + episode + '(?:[^0-9]|$)', 'i').test(href)) return href;
      if (new RegExp('(^|[^0-9])0*' + episode + '([^0-9]|$)').test(href) && /episode|episodio|capitulo|capítulo|watch|ver/i.test(href)) return href;
    }
    return detailUrl;
  });
}

function extractStreams(url) {
  return get(url, false).then(function (html) {
    var out = [];
    var seen = {};
    var re = /(?:src|href|file|source)=["'](https?:\/\/[^"']+\.(?:m3u8|mp4)(?:\?[^"']*)?)["']/gi;
    var m;
    while ((m = re.exec(html)) !== null) {
      var u = m[1].replace(/&amp;/g, '&');
      if (!seen[u]) {
        seen[u] = true;
        out.push({ name: 'DoramaYT', title: 'Servidor DoramaYT', url: u, quality: 'Auto', headers: { Referer: BASE_URL + '/', 'User-Agent': UA } });
      }
    }
    return out;
  });
}

function getStreams(tmdbId, mediaType, season, episode) {
  return findTitleFromTmdb(tmdbId, mediaType)
    .then(searchSite)
    .then(function (detail) { return episodeUrl(detail, episode); })
    .then(extractStreams)
    .catch(function (e) {
      console.log('[DoramaYT] ' + e.message);
      return [];
    });
}

module.exports = { getStreams: getStreams };
