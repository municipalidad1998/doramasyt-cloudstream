function normalizeStream(stream) {
  if (!stream || !stream.url) return null;

  const headers = { ...(stream.headers || {}) };
  const referer = headers.Referer || headers.referer || "https://www.doramasyt.com/";
  headers.Referer = referer;

  try {
    const streamOrigin = new URL(stream.url).origin;
    const refererOrigin = new URL(referer).origin;
    if (streamOrigin !== refererOrigin) headers.Origin = refererOrigin;
  } catch (_) {}

  return { ...stream, headers };
}

export function prepareStreams(streams) {
  const result = [];
  const seen = new Set();

  for (const stream of Array.isArray(streams) ? streams : []) {
    const normalized = normalizeStream(stream);
    if (!normalized || seen.has(normalized.url)) continue;
    seen.add(normalized.url);
    result.push(normalized);
  }

  return result;
}
