const BASE_URL = 'https://www.doramasyt.com';
const UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/122 Safari/537.36';

function request(url, json) {
  return fetch(url, {
    method: 'GET',
    headers: {
      'User-Agent': UA,
      'Accept': json ? 'application/json' : 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
      'Accept-Language': 'es-ES,es;q=0.9,en;q=0.5',
      'Referer': BASE_URL + '/'
    }
  }).then(function (res) {
    if (!res.ok) throw new Error('HTTP ' + res.status);
    return json ? res.json() : res.text();
  });
}

function clean(s) {
  return (s || '').replace(/<[^>]+>/g, ' ').replace(/&amp;/g, '&').replace(/&quot;/g, '"').replace(/&#39;/g, "'").replace(/\s+/g, ' ').trim();
}

function slugify(s) {
  return clean(s).toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '');
}

function absoluteUrl(href) {
  if (!href) return '';
  if (href.indexOf('//') === 0) return 'https:' + href;
  if (/^https?:\/\//i.test(href)) return href;
  if (href.charAt(0) === '/') return BASE_URL + href;
  return BASE_URL + '/' + href;
}

function titleFromTmdb(tmdbId, mediaType) {
  var type = mediaType === 'tv' || mediaType === 'series' ? 'tv' : 'movie';
  return request('https://www.themoviedb.org/' + type + '/' + tmdbId, false).then(function (html) {
    var m = html.match(/<meta[^>]+property=["']og:title["'][^>]+content=["']([^"']+)/i);
    if (!m) m = html.match(/<title>([^<]+)/i);
    if (!m) throw new Error('TMDB title not found');
    return clean(m[1]).replace(/\s*\|\s*TMDB.*$/i, '').trim();
  });
}

function searchSite(title) {
  return request(BASE_URL + '/?s=' + encodeURIComponent(title), false).then(function (html) {
    var links = [];
    var seen = {};
    var re = /<a[^>]+href=["']([^"']+)["'][^>]*>/gi;
    var m;
    while ((m = re.exec(html)) !== null) {
      var href = absoluteUrl(m[1]);
      if (href.indexOf(BASE_URL) !== 0) continue;
      if (/\/(category|tag|page|author|feed|wp-|login|register|contacto|dmca)/i.test(href)) continue;
      var path = href.split('?')[0].replace(/\/+$/, '');
      var last = path.split('/').pop();
      var s = slugify(last);
      if (!s || seen[path]) continue;
      seen[path] = true;
      links.push({ href: path, slug: s });
    }

    var wanted = slugify(title);
    links.sort(function (a, b) {
      function score(x) {
        if (x === wanted) return 0;
        if (x.indexOf(wanted) === 0) return 1;
        var parts = wanted.split('-').filter(function (p) { return p.length > 1; });
        var hits = parts.filter(function (p) { return x.split('-').indexOf(p) >= 0; }).length;
        return parts.length && hits === parts.length ? 2 : 9;
      }
      return score(a.slug) - score(b.slug);
    });

    if (!links.length) throw new Error('DoramaYT result not found');
    return links[0].href;
  });
}

function resolveEpisode(detailUrl, episode) {
  if (!episode) return Promise.resolve(detailUrl);
  return request(detailUrl, false).then(function (html) {
    var re = /href=["']([^"']+)["'][^>]*>/gi;
    var m;
    while ((m = re.exec(html)) !== null) {
      var href = absoluteUrl(m[1]);
      if (href.indexOf(BASE_URL) !== 0) continue;
      var tail = href.split('?')[0].split('/').pop();
      if (new RegExp('(?:episode|episodio|capitulo|capítulo|ep)[^0-9]{0,8}0*' + episode + '(?:[^0-9]|$)', 'i').test(tail)) return href;
      if (new RegExp('(^|[^0-9])0*' + episode + '([^0-9]|$)').test(tail) && /ver|episode|episodio|capitulo|watch/i.test(href)) return href;
    }
    return detailUrl;
  });
}

function decodeUrl(s) {
  return (s || '').replace(/&amp;/gi, '&').replace(/\\u002F/gi, '/').replace(/\\\//g, '/');
}

function mediaUrls(html) {
  var out = [];
  var seen = {};
  var re = /["'(](https?:\/\/[^"')\s]+?\.(?:m3u8|mp4)(?:\?[^"')\s]*)?)["')]/gi;
  var m;
  while ((m = re.exec(html)) !== null) {
    var u = decodeUrl(m[1]);
    if (!seen[u]) {
      seen[u] = true;
      out.push(u);
    }
  }
  return out;
}

function iframeUrls(html) {
  var out = [];
  var seen = {};
  var re = /<iframe[^>]+src=["']([^"']+)["']/gi;
  var m;
  while ((m = re.exec(html)) !== null) {
    var u = decodeUrl(m[1]);
    if (u.indexOf('//') === 0) u = 'https:' + u;
    if (/^https?:\/\//i.test(u) && !seen[u]) {
      seen[u] = true;
      out.push(u);
    }
  }
  return out;
}

function extractStreams(pageUrl) {
  return request(pageUrl, false).then(function (html) {
    var streams = [];
    var seen = {};

    mediaUrls(html).forEach(function (u) {
      if (!seen[u]) {
        seen[u] = true;
        streams.push({ name: 'DoramaYT', title: 'Servidor directo', url: u, quality: 'Auto', headers: { 'Referer': BASE_URL + '/', 'User-Agent': UA } });
      }
    });

    var frames = iframeUrls(html).slice(0, 5);
    return Promise.all(frames.map(function (frame) {
      return request(frame, false).then(function (frameHtml) {
        mediaUrls(frameHtml).forEach(function (u) {
          if (!seen[u]) {
            seen[u] = true;
            streams.push({ name: 'DoramaYT', title: 'Servidor embebido', url: u, quality: 'Auto', headers: { 'Referer': frame, 'User-Agent': UA } });
          }
        });
      }).catch(function () { return null; });
    })).then(function () { return streams; });
  });
}

function getStreams(tmdbId, mediaType, season, episode) {
  return titleFromTmdb(tmdbId, mediaType)
    .then(function (title) {
      console.log('[DoramaYT] TMDB: ' + title);
      return searchSite(title);
    })
    .then(function (detailUrl) {
      if (mediaType === 'tv' && episode) return resolveEpisode(detailUrl, episode);
      return detailUrl;
    })
    .then(extractStreams)
    .catch(function (err) {
      console.log('[DoramaYT] Error: ' + err.message);
      return [];
    });
}

module.exports = { getStreams: getStreams };
