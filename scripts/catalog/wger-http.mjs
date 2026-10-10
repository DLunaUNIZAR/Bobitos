// Descarga robusta de la API de wger: reintentos acotados y validación de URL. Sin dependencias.

export const DEFAULT_HEADERS = { "User-Agent": "BobitosCatalogBuilder/1.0 (+https://github.com/DLunaUNIZAR/Bobitos)" };
const MAX_RETRY_AFTER_MS = 60_000;

/** Solo se descarga de https://wger.de/api/v2/…; `page.next` viene de la red y no es de fiar. */
export function assertWgerApiUrl(url) {
  let u;
  try {
    u = new URL(url);
  } catch {
    throw new Error(`URL no válida: ${url}`);
  }
  if (u.protocol !== "https:" || u.hostname !== "wger.de" || u.port !== "" || !u.pathname.startsWith("/api/v2/")) {
    throw new Error(`URL fuera de https://wger.de/api/v2/: ${url}`);
  }
  return u.href;
}

/** Milisegundos de espera antes del siguiente intento, o null si el error es permanente. */
export function retryDelayMs(res, attempt) {
  if (res.status === 429) {
    const raw = res.headers?.get?.("retry-after");
    let ms = NaN;
    if (raw != null && raw !== "") {
      ms = /^\d+$/.test(String(raw).trim()) ? Number(raw) * 1000 : Date.parse(raw) - Date.now();
    }
    if (!Number.isFinite(ms) || ms < 0) ms = 1000 * attempt;
    return Math.min(ms, MAX_RETRY_AFTER_MS);
  }
  if (res.status >= 400 && res.status < 500) return null;
  return 1000 * attempt;
}

const realSleep = (ms) => new Promise((r) => setTimeout(r, ms));

export async function fetchJson(
  url,
  { fetchImpl = fetch, sleep = realSleep, attempts = 3, timeoutMs = 30_000, headers = DEFAULT_HEADERS } = {},
) {
  let last;
  for (let attempt = 1; attempt <= attempts; attempt++) {
    let delay = 1000 * attempt;
    try {
      const res = await fetchImpl(url, { headers, signal: AbortSignal.timeout(timeoutMs) });
      if (!res.ok) {
        last = new Error(`HTTP ${res.status}`);
        delay = retryDelayMs(res, attempt);
        if (delay === null) break;
      } else {
        return await res.json();
      }
    } catch (e) {
      last = e;
    }
    if (attempt < attempts) await sleep(delay);
  }
  throw new Error(`No se pudo descargar ${url}: ${last?.message}`);
}
