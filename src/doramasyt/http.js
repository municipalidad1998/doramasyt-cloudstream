export const BASE_URL = "https://www.doramasyt.com";
export const UA = "Mozilla/5.0 (Android 13) AppleWebKit/537.36 Chrome/122 Mobile Safari/537.36";

export const HEADERS = {
  "User-Agent": UA,
  "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
  "Accept-Language": "es-ES,es;q=0.9,en;q=0.5",
  "Referer": BASE_URL + "/"
};

export async function request(url, options = {}) {
  const response = await fetch(url, {
    method: options.method || "GET",
    headers: { ...HEADERS, ...(options.headers || {}) },
    body: options.body
  });
  if (!response.ok) throw new Error("HTTP " + response.status + " for " + url);
  return options.json ? response.json() : response.text();
}

export function clean(value) {
  return String(value || "")
    .replace(/<[^>]+>/g, " ")
    .replace(/&amp;/g, "&")
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
    .replace(/\s+/g, " ")
    .trim();
}

export function absoluteUrl(href, base = BASE_URL) {
  if (!href) return "";
  const value = String(href).trim();
  try {
    return new URL(value, base).toString();
  } catch (_) {
    if (value.startsWith("//")) return "https:" + value;
    if (/^https?:\/\//i.test(value)) return value;
    if (value.startsWith("/")) return BASE_URL + value;
    return BASE_URL + "/" + value;
  }
}
