var BASE_URL = 'https://www.doramasyt.com';
var UA = 'Mozilla/5.0 (Android 13) AppleWebKit/537.36 Chrome/122 Safari/537.36';
var VIDEO_RE = /\.(m3u8|mpd|mp4|mkv|webm|m4v|mov|ts|avi|flv)(?:$|[?#])/i;

function request(url) {
  return fetch(url, {
    method: 'GET',
    headers: {
      'User-Agent': UA,
      'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
      'Accept-Language': 'es-ES,es;q=0.9,en;q=0.5',
      'Referer': BASE_URL + '/'
    }
  }).then(function (r) {
    if (!r.ok) throw new Error('HTTP ' + r.status);
    return r.text();
  });
}

function clean(s) {
  return (s || '').replace(/<[^>]+>/g, ' ').replace(/&amp;/g, '&').replace(/&quot;/g, '"').replace(/&#39;/g, "'").replace(/\s+/g, ' ').trim();
}

function absolute(href) {
  if (!href) return '';
  if (href.indexOf('//') === 0) return 'https:' + href;
  if (/^https?:\/\//i.test(href)) return href;
  if (href.charAt(0) === '/') return BASE_URL + href;
  return BASE_URL + '/' + href;
}

function slug(s) {
  return clean(s).toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '');
}

function titleFromTmdb(id, type) {
  var media = type === 'tv' || type === 'series' ? 'tv' : 'movie';
  return request('https://www.themoviedb.org/' + media + '/' + id).then(function (html) {
    var m = html.match(/<meta[^>]+property=["']og:title["'][^>]+content=["']([^"']+)/i) || html.match(/<title>([^<]+)/i);
    if (!m) throw new Error('TMDB title not found');
    return clean(m[1]).replace(/\s*\|\s*TMDB.*$/i, '').trim();
  });
}

function searchSite(title) {
  return request(BASE_URL + '/?s=' + encodeURIComponent(title)).then(function (html) {
    var links = [];
    var seen = {};
    var re = /<a[^>]+href=["']([^"']+)["']/gi;
    var m;
    while ((m = re.exec(html)) !== null) {
      var u = absolute(m[1]).split('?')[0].split('#')[0];
      if (u.indexOf(BASE_URL) !== 0 || seen[u]) continue;
      if (/\/(category|tag|page|author|feed|wp-|login|register|contacto|dmca)\//i.test(u)) continue;
      var s = slug(u.split('/').pop());
      if (!s) continue;
      seen[u] = true;
      links.push({ url: u, slug: s });
    }
    var wanted = slug(title);
    links.sort(function (a, b) {
      function score(x) {
        if (x === wanted) return 0;
        if (x.indexOf(wanted) === 0) return 1;
        var p = wanted.split('-').filter(function (v) { return v.length > 1; });
        var hits = p.filter(function (v) { return x.split('-').indexOf(v) >= 0; }).length;
        return p.length && hits === p.length ? 2 : 9;
      }
      return score(a.slug) - score(b.slug);
    });
    if (!links.length) throw new Error('DoramaYT result not found');
    return links[0].url;
  });
}

function episodeUrl(detail, episode) {
  if (!episode) return Promise.resolve(detail);
  return request(detail).then(function (html) {
    var re = /href=["']([^"']+)["']/gi;
    var m;
    while ((m = re.exec(html)) !== null) {
      var u = absolute(m[1]);
      if (u.indexOf(BASE_URL) !== 0) continue;
      var tail = u.split('?')[0].split('/').pop();
      if (new RegExp('(?:episode|episodio|capitulo|capítulo|ep)[^0-9]{0,8}0*' + episode + '(?:[^0-9]|$)', 'i').test(tail)) return u;
      if (new RegExp('(^|[^0-9])0*' + episode + '([^0-9]|$)').test(tail) && /ver|episode|episodio|capitulo|watch/i.test(u)) return u;
    }
    return detail;
  });
}

function decode(s) {
  return (s || '').replace(/&amp;/gi, '&').replace(/&quot;/gi, '"').replace(/&#39;/gi, "'").replace(/\\u002F/gi, '/').replace(/\\\//g, '/');
}

function extractUrls(html) {
  var out = [];
  var seen = {};
  var re = /(?:src|href|file|source|data-src|data-file)=["']([^"']+)["']/gi;
  var m;
  while ((m = re.exec(html)) !== null) {
    var u = decode(m[1]);
    if (u.indexOf('//') === 0) u = 'https:' + u;
    if (!/^https?:\/\//i.test(u) || seen[u]) continue;
    if (VIDEO_RE.test(u)) {
      seen[u] = true;
      out.push(u);
    }
  }
  return out;
}

function extractStreams(pageUrl) {
  return request(pageUrl).then(function (html) {
    var streams = [];
    var seen = {};
    extractUrls(html).forEach(function (u) {
      if (seen[u]) return;
      seen[u] = true;
      var ext = (u.match(/\.([a-z0-9]+)(?:$|[?#])/i) || [])[1] || 'video';
      streams.push({
        name: 'DoramaYT',
        title: 'DoramaYT ' + ext.toUpperCase(),
        url: u,
        quality: 'Auto',
        headers: { Referer: pageUrl, 'User-Agent': UA }
      });
    });
    return streams;
  });
}

function getStreams(tmdbId, mediaType, season, episode) {
  return titleFromTmdb(tmdbId, mediaType)
    .then(searchSite)
    .then(function (detail) { return mediaType === 'tv' && episode ? episodeUrl(detail, episode) : detail; })
    .then(extractStreams)
    .catch(function (e) {
      console.log('[DoramaYT] ' + e.message);
      return [];
    });
}

module.exports = { getStreams: getStreams };