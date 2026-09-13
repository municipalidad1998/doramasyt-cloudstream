var BASE_URL = 'https://www.doramasyt.com';
var UA = 'Mozilla/5.0 (Android 13) AppleWebKit/537.36 Chrome/122 Mobile Safari/537.36';

function request(url) {
  return fetch(url, {
    method: 'GET',
    headers: {
      'User-Agent': UA,
      'Accept': 'text/html,application/xhtml+xml,*/*;q=0.8',
      'Accept-Language': 'es-ES,es;q=0.9,en;q=0.5',
      'Referer': BASE_URL + '/'
    }
  }).then(function (r) {
    return r.text();
  });
}

function strip(s) {
  return String(s || '')
    .replace(/<[^>]*>/g, ' ')
    .replace(/&amp;/g, '&')
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
    .replace(/\s+/g, ' ')
    .trim();
}

function simpleSlug(s) {
  return strip(s).toLowerCase()
    .replace(/[áàäâ]/g, 'a')
    .replace(/[éèëê]/g, 'e')
    .replace(/[íìïî]/g, 'i')
    .replace(/[óòöô]/g, 'o')
    .replace(/[úùüû]/g, 'u')
    .replace(/ñ/g, 'n')
    .replace(/[^a-z0-9]+/g, '-');
}

function abs(href) {
  if (!href) return '';
  if (/^https?:\/\//i.test(href)) return href;
  if (href.indexOf('//') === 0) return 'https:' + href;
  if (href.charAt(0) === '/') return BASE_URL + href;
  return BASE_URL + '/' + href;
}

function tmdbTitle(id, type) {
  var kind = type === 'tv' || type === 'series' ? 'tv' : 'movie';
  return request('https://www.themoviedb.org/' + kind + '/' + id).then(function (html) {
    var m = html.match(/property=["']og:title["'][^>]+content=["']([^"']+)/i);
    if (!m) m = html.match(/<title[^>]*>([^<]+)/i);
    if (!m) throw new Error('TMDB title not found');
    return strip(m[1]).replace(/\s*\|\s*TMDB.*$/i, '');
  });
}

function findDorama(title) {
  return request(BASE_URL + '/?s=' + encodeURIComponent(title)).then(function (html) {
    var wanted = simpleSlug(title);
    var links = [];
    var seen = {};
    var re = /<a[^>]+href=["']([^"']+)["'][^>]*>/gi;
    var m;

    while ((m = re.exec(html)) !== null) {
      var u = abs(m[1]).split('?')[0].replace(/#.*$/, '').replace(/\/$/, '');
      if (u.indexOf(BASE_URL) !== 0) continue;
      if (/\/(category|tag|page|author|wp-|login|register|contact|dmca)\//i.test(u)) continue;
      if (seen[u]) continue;
      seen[u] = true;
      var last = u.split('/').pop();
      if (!last || last.indexOf('-') < 0) continue;
      var s = simpleSlug(last);
      var score = s === wanted ? 0 : (s.indexOf(wanted) === 0 ? 1 : 9);
      links.push({ url: u, score: score });
    }

    links.sort(function (a, b) { return a.score - b.score; });
    if (!links.length) throw new Error('DoramaYT result not found');
    return links[0].url;
  });
}

function findEpisode(detail, episode) {
  if (!episode) return Promise.resolve(detail);
  return request(detail).then(function (html) {
    var re = /<a[^>]+href=["']([^"']+)["'][^>]*>/gi;
    var m;
    var ep = String(episode);
    while ((m = re.exec(html)) !== null) {
      var u = abs(m[1]);
      if (u.indexOf(BASE_URL) !== 0) continue;
      if (!/(ver|episode|episodio|capitulo|capítulo|watch)/i.test(u)) continue;
      var tail = u.split('?')[0].split('/').pop();
      if (new RegExp('(^|[^0-9])0*' + ep + '([^0-9]|$)', 'i').test(tail)) return u;
    }
    return detail;
  });
}

function directUrls(html) {
  var result = [];
  var seen = {};
  var re = /(https?:\/\/[^\s"'<>]+(?:\.m3u8|\.mp4)(?:\?[^\s"'<>]*)?)/gi;
  var m;
  while ((m = re.exec(html)) !== null) {
    var u = m[1].replace(/&amp;/g, '&').replace(/\\\//g, '/');
    if (!seen[u]) {
      seen[u] = true;
      result.push(u);
    }
  }
  return result;
}

function iframeUrls(html) {
  var result = [];
  var seen = {};
  var re = /<iframe[^>]+src=["']([^"']+)["']/gi;
  var m;
  while ((m = re.exec(html)) !== null) {
    var u = abs(m[1]);
    if (!/^https?:\/\//i.test(u) || seen[u]) continue;
    seen[u] = true;
    result.push(u);
  }
  return result;
}

function streamsFromPage(url) {
  return request(url).then(function (html) {
    var out = [];
    var seen = {};
    directUrls(html).forEach(function (u) {
      if (seen[u]) return;
      seen[u] = true;
      out.push({
        name: 'DoramaYT',
        title: 'DoramaYT directo',
        url: u,
        quality: 'Auto',
        headers: { 'Referer': url, 'User-Agent': UA }
      });
    });

    var frames = iframeUrls(html).slice(0, 3);
    return Promise.all(frames.map(function (frame) {
      return request(frame).then(function (frameHtml) {
        directUrls(frameHtml).forEach(function (u) {
          if (seen[u]) return;
          seen[u] = true;
          out.push({
            name: 'DoramaYT',
            title: 'DoramaYT embebido',
            url: u,
            quality: 'Auto',
            headers: { 'Referer': frame, 'User-Agent': UA }
          });
        });
      }).catch(function () {});
    })).then(function () { return out; });
  });
}

function getStreams(tmdbId, mediaType, season, episode) {
  console.log('[DoramaYT] start ' + tmdbId + ' ' + mediaType + ' ' + season + ' ' + episode);
  return tmdbTitle(tmdbId, mediaType)
    .then(function (title) {
      console.log('[DoramaYT] title ' + title);
      return findDorama(title);
    })
    .then(function (detail) {
      if (mediaType === 'tv' || mediaType === 'series') return findEpisode(detail, episode);
      return detail;
    })
    .then(function (page) {
      console.log('[DoramaYT] page ' + page);
      return streamsFromPage(page);
    })
    .then(function (streams) {
      console.log('[DoramaYT] streams ' + streams.length);
      return streams;
    })
    .catch(function (err) {
      console.log('[DoramaYT] ERROR ' + String(err && err.message ? err.message : err));
      return [];
    });
}

module.exports = { getStreams: getStreams };
