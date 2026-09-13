const BASE_URL = 'https://www.doramasyt.com';
const UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/122 Safari/537.36';

function get(url) {
  return fetch(url, {
    method: 'GET',
    headers: {
      'User-Agent': UA,
      'Accept': 'text/html,application/xhtml+xml,*/*;q=0.8',
      'Accept-Language': 'es-ES,es;q=0.9,en;q=0.5',
      'Referer': BASE_URL + '/'
    },
    redirect: 'follow'
  }).then(function (r) {
    if (!r.ok) throw new Error('HTTP ' + r.status);
    return r.text();
  });
}

function clean(s) {
  return (s || '')
    .replace(/<[^>]+>/g, ' ')
    .replace(/&amp;/g, '&')
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
    .replace(/\s+/g, ' ')
    .trim();
}

function slug(s) {
  return clean(s).toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-|-$/g, '');
}

function absoluteUrl(href) {
  if (!href) return '';
  if (href.indexOf('//') === 0) return 'https:' + href;
  if (href.indexOf('http://') === 0 || href.indexOf('https://') === 0) return href;
  if (href.charAt(0) === '/') return BASE_URL + href;
  return BASE_URL + '/' + href;
}

function findTitleFromTmdb(tmdbId, mediaType) {
  var type = mediaType === 'tv' || mediaType === 'series' ? 'tv' : 'movie';
  return get('https://www.themoviedb.org/' + type + '/' + tmdbId)
    .then(function (html) {
      var m = html.match(/<meta[^>]+property=["']og:title["'][^>]+content=["']([^"']+)/i);
      if (!m) m = html.match(/<title>([^<]+)/i);
      if (!m) throw new Error('TMDB title not found');
      return clean(m[1]).replace(/\s*\|\s*TMDB.*$/i, '').trim();
    });
}

function searchSite(title) {
  return get(BASE_URL + '/?s=' + encodeURIComponent(title))
    .then(function (html) {
      var links = [];
      var seen = {};
      var re = /<a[^>]+href=["']([^"']+)["'][^>]*>/gi;
      var m;
      while ((m = re.exec(html)) !== null) {
        var href = absoluteUrl(m[1]);
        if (href.indexOf(BASE_URL) !== 0) continue;
        if (/\/wp-|\/category\/|\/tag\/|\/page\//i.test(href)) continue;
        var s = slug(href.split('/').filter(Boolean).pop());
        if (!s || s.indexOf('-') === -1 || seen[href]) continue;
        seen[href] = true;
        links.push({ href: href.split('?')[0], slug: s });
      }

      var wanted = slug(title);
      links.sort(function (a, b) {
        function score(x) {
          if (x === wanted) return 0;
          if (x.indexOf(wanted) === 0) return 1;
          var wt = wanted.split('-').filter(Boolean);
          var hits = wt.filter(function (t) { return x.split('-').indexOf(t) >= 0; }).length;
          return hits === wt.length && wt.length ? 2 : 9;
        }
        return score(a.slug) - score(b.slug);
      });

      if (!links.length || links[0].slug === '') throw new Error('DoramaYT result not found');
      return links[0].href;
    });
}

function episodeUrl(detailUrl, episode) {
  if (!episode) return Promise.resolve(detailUrl);

  return get(detailUrl).then(function (html) {
    var candidates = [];
    var seen = {};
    var re = /href=["']([^"']+)["'][^>]*>/gi;
    var m;

    while ((m = re.exec(html)) !== null) {
      var href = absoluteUrl(m[1]);
      if (href.indexOf(BASE_URL) !== 0) continue;
      if (seen[href]) continue;
      seen[href] = true;
      candidates.push(href);
    }

    var ep = String(episode);
    for (var i = 0; i < candidates.length; i++) {
      var u = candidates[i];
      if (new RegExp('(?:episode|episodio|capitulo|capítulo|ep)[^0-9]{0,8}0*' + ep + '(?:[^0-9]|$)', 'i').test(u)) {
        return u;
      }
      if (new RegExp('(^|[^0-9])0*' + ep + '([^0-9]|$)').test(u) &&
          /episode|episodio|capitulo|capítulo|watch|ver/i.test(u)) {
        return u;
      }
    }

    return detailUrl;
  });
}

function decodeEntities(s) {
  return (s || '')
    .replace(/&amp;/gi, '&')
    .replace(/&quot;|&#34;|&#x22;/gi, '"')
    .replace(/&#39;|&#x27;|&#x2F;/gi, function (m) {
      return /2f/i.test(m) ? '/' : "'";
    });
}

function extractCandidates(html) {
  var urls = [];
  var seen = {};
  var i;
  var patterns = [
    /(?:src|href|file|source|data-file|data-src)=["']([^"']+)["']/gi,
    /https?:\\/\\/[^"'\s<>]+/gi
  ];

  patterns.forEach(function (re) {
    var m;
    while ((m = re.exec(html)) !== null) {
      var raw = decodeEntities(m[1] || m[0]).replace(/\\\\/g, '/').replace(/\\\//g, '/');
      if (raw.indexOf('//') === 0) raw = 'https:' + raw;
      if (!/^https?:\/\//i.test(raw)) continue;

      if (/\.(m3u8|mp4)(?:$|[?#])/i.test(raw)) {
        if (!seen[raw]) {
          seen[raw] = true;
          urls.push(raw);
        }
        continue;
      }

      if (/filemoon|streamtape|mixdrop|gofile|pixeldrain|uqload|voe|dood|streamwish|vidhide|filelions|ok.ru|mega/i.test(raw)) {
        if (!seen[raw]) {
          seen[raw] = true;
          urls.push(raw);
        }
      }
    }
  });

  var iframeRe = /<iframe[^>]+src=["']([^"']+)["']/gi;
  var im;
  while ((im = iframeRe.exec(html)) !== null) {
    var iframe = absoluteUrl(decodeEntities(im[1]));
    if (/^https?:\/\//i.test(iframe) && !seen[iframe]) {
      seen[iframe] = true;
      urls.push(iframe);
    }
  }

  return urls;
}

function extractStreams(url, depth) {
  if (depth > 2) return Promise.resolve([]);
  return get(url).then(function (html) {
    var candidates = extractCandidates(html);
    var streams = [];
    var seen = {};

    candidates.forEach(function (u) {
      if (/\.(m3u8|mp4)(?:$|[?#])/i.test(u)) {
        if (!seen[u]) {
          seen[u] = true;
          streams.push({
            name: 'DoramaYT',
            title: 'Servidor directo',
            url: u,
            quality: 'Auto',
            headers: {
              Referer: url,
              'User-Agent': UA
            }
          });
        }
      }
    });

    var embedded = candidates.filter(function (u) {
      return !/\.(m3u8|mp4)(?:$|[?#])/i.test(u);
    }).slice(0, 6);

    if (!embedded.length) return streams;

    return Promise.all(embedded.map(function (u) {
      return extractStreams(u, depth + 1).catch(function () { return []; });
    })).then(function (nested) {
      nested.forEach(function (items) {
        items.forEach(function (item) {
          if (!seen[item.url]) {
            seen[item.url] = true;
            streams.push(item);
          }
        });
      });
      return streams;
    });
  });
}

function getStreams(tmdbId, mediaType, season, episode) {
  return findTitleFromTmdb(tmdbId, mediaType)
    .then(searchSite)
    .then(function (detail) {
      return episodeUrl(detail, episode);
    })
    .then(function (pageUrl) {
      return extractStreams(pageUrl, 0);
    })
    .catch(function (e) {
      console.log('[DoramaYT] ' + e.message);
      return [];
    });
}

module.exports = { getStreams: getStreams };
