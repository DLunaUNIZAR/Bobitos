import assert from "node:assert/strict";
import test from "node:test";
import { assertWgerApiUrl, assertWgerMediaUrl, fetchBinary, fetchJson } from "../scripts/catalog/wger-http.mjs";

const URL_OK = "https://wger.de/api/v2/exerciseinfo/?limit=200";
const res = (status, body = {}, headers = {}) => ({
  ok: status >= 200 && status < 300,
  status,
  headers: { get: (k) => headers[k.toLowerCase()] ?? headers[k] ?? null },
  json: async () => body,
});
// Dobles sin red ni esperas reales.
const scripted = (...steps) => {
  const calls = [];
  const fetchImpl = async (url, opts) => {
    calls.push({ url, opts });
    const step = steps[Math.min(calls.length - 1, steps.length - 1)];
    if (step instanceof Error) throw step;
    return step;
  };
  return { fetchImpl, calls };
};
const sleeps = () => {
  const list = [];
  return { sleep: async (ms) => void list.push(ms), list };
};

test("fetchJson no reintenta un 404 permanente", async () => {
  const { fetchImpl, calls } = scripted(res(404));
  const s = sleeps();
  await assert.rejects(fetchJson(URL_OK, { fetchImpl, sleep: s.sleep }), /HTTP 404/);
  assert.equal(calls.length, 1);
  assert.deepEqual(s.list, []);
});

test("fetchJson reintenta un 503 y devuelve el JSON", async () => {
  const { fetchImpl, calls } = scripted(res(503), res(200, { ok: 1 }));
  const s = sleeps();
  assert.deepEqual(await fetchJson(URL_OK, { fetchImpl, sleep: s.sleep }), { ok: 1 });
  assert.equal(calls.length, 2);
  assert.deepEqual(s.list, [1000]);
});

test("fetchJson espera Retry-After en un 429 y lo limita a 60 s", async () => {
  const { fetchImpl } = scripted(res(429, {}, { "retry-after": "5" }), res(429, {}, { "retry-after": "600" }), res(200, { ok: 2 }));
  const s = sleeps();
  assert.deepEqual(await fetchJson(URL_OK, { fetchImpl, sleep: s.sleep }), { ok: 2 });
  assert.deepEqual(s.list, [5000, 60000]);
});

test("fetchJson reintenta errores de red y se rinde tras 3 intentos con la URL en el mensaje", async () => {
  const { fetchImpl, calls } = scripted(new Error("ECONNRESET"));
  const s = sleeps();
  await assert.rejects(fetchJson(URL_OK, { fetchImpl, sleep: s.sleep }), (e) => {
    assert.ok(e.message.includes(URL_OK));
    assert.match(e.message, /ECONNRESET/);
    return true;
  });
  assert.equal(calls.length, 3);
  assert.equal(s.list.length, 2);
});

test("assertWgerApiUrl acepta /api/v2/ de wger.de y rechaza otro host, http:// u otra ruta", () => {
  assert.doesNotThrow(() => assertWgerApiUrl(URL_OK));
  assert.throws(() => assertWgerApiUrl("https://evil.example/api/v2/exerciseinfo/"));
  assert.throws(() => assertWgerApiUrl("https://wger.de.evil.example/api/v2/x/"));
  assert.throws(() => assertWgerApiUrl("http://wger.de/api/v2/exerciseinfo/"));
  assert.throws(() => assertWgerApiUrl("https://wger.de/media/x.png"));
  assert.throws(() => assertWgerApiUrl("no es una url"));
});

test("assertWgerMediaUrl acepta /media/ de wger.de y rechaza otro host o ruta", () => {
  const ok = "https://wger.de/media/exercise-images/1/a.png.400x400_q85.png";
  assert.equal(assertWgerMediaUrl(ok), ok);
  assert.throws(() => assertWgerMediaUrl("https://evil.example/media/a.png"));
  assert.throws(() => assertWgerMediaUrl("https://wger.de.evil.example/media/a.png"));
  assert.throws(() => assertWgerMediaUrl("http://wger.de/media/a.png"));
  assert.throws(() => assertWgerMediaUrl("https://wger.de:8443/media/a.png"));
  assert.throws(() => assertWgerMediaUrl("https://wger.de/api/v2/exerciseinfo/"));
  assert.throws(() => assertWgerMediaUrl("no es una url"));
});

const binRes = (status, bytes = [1, 2, 3]) => ({
  ok: status >= 200 && status < 300,
  status,
  headers: { get: () => null },
  arrayBuffer: async () => new Uint8Array(bytes).buffer,
});
const MEDIA = "https://wger.de/media/exercise-images/1/a.png";

test("fetchBinary devuelve los bytes y envía el User-Agent", async () => {
  const { fetchImpl, calls } = scripted(binRes(200, [9, 8, 7]));
  const buf = await fetchBinary(MEDIA, { fetchImpl, sleep: sleeps().sleep });
  assert.deepEqual([...buf], [9, 8, 7]);
  assert.match(calls[0].opts.headers["User-Agent"], /Bobitos/);
});

test("fetchBinary no reintenta un 404", async () => {
  const { fetchImpl, calls } = scripted(binRes(404));
  const s = sleeps();
  await assert.rejects(fetchBinary(MEDIA, { fetchImpl, sleep: s.sleep }), /HTTP 404/);
  assert.equal(calls.length, 1);
  assert.deepEqual(s.list, []);
});

test("fetchBinary reintenta un 503 y rechaza una URL que no es de wger.de/media", async () => {
  const { fetchImpl, calls } = scripted(binRes(503), binRes(200));
  assert.deepEqual([...(await fetchBinary(MEDIA, { fetchImpl, sleep: sleeps().sleep }))], [1, 2, 3]);
  assert.equal(calls.length, 2);
  await assert.rejects(fetchBinary("https://evil.example/media/a.png", { fetchImpl }));
});
