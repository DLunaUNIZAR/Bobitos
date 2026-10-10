import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import {
  IMAGE_MAX_BYTES,
  IMAGE_MAX_SIDE,
  IMAGE_QUALITY,
  buildImages,
  sha256Hex,
  toWebp,
} from "../scripts/catalog/images.mjs";

const entry = (id, url = `https://wger.de/media/exercise-images/1/${id}.png`) => ({
  id,
  image: { author: "Ana", license: "CC-BY-SA-4.0", sourceUrl: url },
});

// Dobles sin red ni disco. `convert` sustituye a sharp: devuelve bytes derivados de la entrada.
const fakeSharp = (size = 10) => () => {
  const chain = {
    rotate: () => chain,
    resize: () => chain,
    webp: () => chain,
    toBuffer: async () => ({ data: Buffer.alloc(size, 7), info: { width: 4, height: 3 } }),
  };
  return chain;
};
const harness = ({ existing = [], size } = {}) => {
  const written = new Map();
  const fetched = [];
  return {
    written,
    fetched,
    deps: {
      outDir: "/out",
      fetchBinary: async (url) => (fetched.push(url), Buffer.from("png")),
      sharpImpl: fakeSharp(size),
      readExisting: async () => new Set(existing),
      writeImage: async (name, data) => void written.set(name, data),
    },
  };
};

test("constantes del formato: 400 px, calidad 80 y 200 KB", () => {
  assert.equal(IMAGE_MAX_SIDE, 400);
  assert.equal(IMAGE_QUALITY, 80);
  assert.equal(IMAGE_MAX_BYTES, 200 * 1024);
});

test("sha256Hex es estable", () => {
  assert.equal(sha256Hex(Buffer.from("abc")), "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
  assert.equal(sha256Hex(Buffer.from("abc")), sha256Hex(Buffer.from("abc")));
  assert.match(sha256Hex(Buffer.alloc(0)), /^[0-9a-f]{64}$/);
});

test("toWebp convierte tiny.png a WebP de ≤400 px", async () => {
  const png = await readFile(new URL("./fixtures/tiny.png", import.meta.url));
  const { data, width, height } = await toWebp(png);
  assert.equal(data.subarray(0, 4).toString("latin1"), "RIFF");
  assert.equal(data.subarray(8, 12).toString("latin1"), "WEBP");
  assert.ok(width <= 400 && height <= 400);
  assert.deepEqual([width, height], [8, 6]); // sin ampliar
});

test("buildImages no descarga si el fichero ya existe", async () => {
  const h = harness({ existing: ["a.webp"] });
  const r = await buildImages({ entries: [entry("a")], ...h.deps });
  assert.deepEqual(h.fetched, []);
  assert.equal(h.written.size, 0);
  assert.deepEqual(r, { written: [], skipped: ["a"], problems: [], unused: [] });
});

test("buildImages descarga, convierte y escribe con un fetch falso; ignora las fichas sin imagen", async () => {
  const h = harness();
  const r = await buildImages({
    entries: [entry("a"), { id: "b", image: null }, entry("c")],
    ...h.deps,
  });
  assert.deepEqual(h.fetched, [entry("a").image.sourceUrl, entry("c").image.sourceUrl]);
  assert.deepEqual([...h.written.keys()], ["a.webp", "c.webp"]);
  assert.deepEqual(r.written, ["a", "c"]);
  assert.deepEqual(r.problems, []);
});

test("buildImages informa de un tamaño > 200 KB, de una descarga fallida y de ficheros sin usar, sin escribir lo defectuoso", async () => {
  const big = harness({ existing: ["vieja.webp"], size: IMAGE_MAX_BYTES + 1 });
  const r = await buildImages({ entries: [entry("a")], ...big.deps });
  assert.equal(big.written.size, 0);
  assert.equal(r.problems.length, 1);
  assert.match(r.problems[0], /a/);
  assert.match(r.problems[0], new RegExp(String(IMAGE_MAX_BYTES + 1)));
  assert.deepEqual(r.unused, ["vieja"]);

  const h = harness();
  h.deps.fetchBinary = async (url) => {
    if (url.includes("/a.")) throw new Error("HTTP 404");
    return Buffer.from("png");
  };
  const r2 = await buildImages({ entries: [entry("a"), entry("c")], ...h.deps });
  assert.deepEqual(r2.written, ["c"]);
  assert.equal(r2.problems.length, 1);
  assert.match(r2.problems[0], /HTTP 404/);
});

test("buildImages exactamente 200 KB es válido", async () => {
  const h = harness({ size: IMAGE_MAX_BYTES });
  const r = await buildImages({ entries: [entry("a")], ...h.deps });
  assert.deepEqual(r.problems, []);
  assert.deepEqual(r.written, ["a"]);
});
