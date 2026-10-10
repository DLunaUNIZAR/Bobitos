import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import {
  htmlToText,
  imageCandidates,
  inferType,
  isNearDuplicate,
  nearDuplicateKey,
  normalizeName,
  pickImage,
  slug,
  toCandidate,
  truncateText,
} from "../scripts/catalog/normalize.mjs";

const sample = JSON.parse(
  readFileSync(new URL("./fixtures/wger-exerciseinfo.sample.json", import.meta.url), "utf8"),
);
const byId = (id) => sample.find((e) => e.id === id);

test("htmlToText convierte párrafos, listas y entidades", () => {
  assert.equal(
    htmlToText("<p>Hola&nbsp;<strong>mundo</strong></p><ul><li>Uno</li><li>Dos</li></ul>"),
    "Hola mundo\n\n• Uno\n• Dos",
  );
  assert.equal(htmlToText("<p>Sost&eacute;n &#39;a&#39; &quot;b&quot; &amp; c &aacute;</p>"), "Sostén 'a' \"b\" & c á");
  assert.equal(htmlToText("<p>Uno<br>dos</p><p></p><p></p><p>tres</p>"), "Uno\ndos\n\ntres");
});

test("htmlToText ignora los saltos de línea del HTML de origen y deja cada viñeta en una línea", () => {
  assert.equal(
    htmlToText(
      "<ul>\n<li>\n<p><strong>Posición inicial:</strong></p>\n</li>\n<li>Uno.</li>\n<li>Dos.</li>\n</ul>",
    ),
    "• Posición inicial:\n• Uno.\n• Dos.",
  );
  assert.equal(htmlToText("<p>es un\nejercicio fundamental</p>"), "es un ejercicio fundamental");
  assert.equal(
    htmlToText("<ul><li><p>A</p></li><li><p>B</p></li><li><p>C</p></li></ul>"),
    "• A\n• B\n• C",
  );
  assert.equal(
    htmlToText("<p>Intro</p>\n<ul>\n<li><p>A</p></li>\n<li><p>B</p></li>\n</ul>\n<p>Fin</p>"),
    "Intro\n\n• A\n• B\n\nFin",
  );
});

test("truncateText respeta el máximo y corta en frase o palabra", () => {
  assert.equal(truncateText("Corto."), "Corto.");
  const t = truncateText("Primera frase. Segunda frase larga que no cabe.", 30);
  assert.equal(t, "Primera frase.");
  const w = truncateText("palabra ".repeat(20).trim(), 30);
  assert.ok(w.endsWith("…"));
  assert.ok(w.length <= 30);
  assert.ok(!w.includes("palabr…"));
});

test("normalizeName unifica mayúsculas y tildes", () => {
  assert.equal(normalizeName("Curl con Mancuernas en Banco Scott"), "Curl con mancuernas en banco Scott");
  assert.equal(normalizeName("  press   BANCA "), "Press banca");
  assert.equal(normalizeName("Press Francés con Barra SZ"), "Press francés con barra SZ");
  assert.equal(normalizeName("Jalón A La Cara"), "Jalón a la cara");
  assert.equal(normalizeName("extension de triceps en polea"), "Extensión de tríceps en polea");
});

test("slug coincide con el de Kotlin", () => {
  assert.equal(slug("Tomate frito"), "tomate-frito");
  assert.equal(slug("  Aceite  de   Oliva "), "aceite-de-oliva");
  assert.equal(slug("Jamón Serrano"), "jamon-serrano");
  assert.equal(slug("Ñoquis"), "noquis");
  assert.equal(slug("Café"), "cafe");
  assert.equal(slug("50% cacao!"), "50-cacao");
  assert.equal(slug("  ¡Sal!  "), "sal");
  assert.equal(slug("   "), "");
  assert.equal(slug("—··—"), "");
});

test("nearDuplicateKey agrupa singular/plural y orden", () => {
  assert.equal(nearDuplicateKey("Press francés con mancuerna"), nearDuplicateKey("Press francés con mancuernas"));
  assert.equal(nearDuplicateKey("Remo con barra"), nearDuplicateKey("Barra remo"));
  assert.notEqual(nearDuplicateKey("Sentadilla frontal"), nearDuplicateKey("Sentadilla trasera"));
});

test("inferType aplica las reglas por orden", () => {
  const cases = [
    [{ categoryId: 15, equipment: ["KETTLEBELL"], name: "Swing" }, "CARDIO", "categoria"],
    [{ categoryId: 9, equipment: ["POLEA", "BARRA"], name: "Remo" }, "MAQUINA", "equipo"],
    [{ categoryId: 11, equipment: ["MANCUERNAS", "BANCO"], name: "Aperturas" }, "PESO_LIBRE", "equipo"],
    [{ categoryId: 12, equipment: ["BARRA_DOMINADAS"], name: "Dominadas" }, "PESO_CORPORAL", "equipo"],
    [{ categoryId: 9, equipment: [], name: "Prensa de piernas" }, "MAQUINA", "nombre"],
    [{ categoryId: 13, equipment: [], name: "Elevaciones frontales con disco" }, "PESO_LIBRE", "nombre"],
    [{ categoryId: 10, equipment: ["ESTERILLA"], name: "Plancha" }, "PESO_CORPORAL", "defecto"],
    [{ categoryId: 10, equipment: ["BANDA_ELASTICA"], name: "Tirón" }, "OTROS", "equipo"],
    [{ categoryId: 9, equipment: ["FITBALL", "MANCUERNAS"], name: "Curl" }, "PESO_LIBRE", "equipo"],
  ];
  for (const [input, type, reason] of cases) {
    const got = inferType(input);
    assert.equal(got.type, type, JSON.stringify(input));
    assert.equal(got.reason, reason, JSON.stringify(input));
  }
});

test("toCandidate descarta sin español, sin descripción u ODbL y conserva la atribución", () => {
  assert.equal(toCandidate(byId(900002)).rejected.reason, "sin-traduccion-es");
  assert.equal(toCandidate(byId(900001)).rejected.reason, "licencia-no-admitida");
  const empty = structuredClone(byId(238));
  empty.translations.find((t) => t.language === 4).description = "<p> </p>";
  assert.equal(toCandidate(empty).rejected.reason, "sin-descripcion");

  const { candidate: c } = toCandidate(byId(257));
  assert.equal(c.wgerId, 257);
  assert.equal(c.name, "Sentadilla frontal");
  assert.equal(c.slug, "sentadilla-frontal");
  assert.equal(c.muscleGroup, "Cuádriceps");
  assert.deepEqual(c.equipment, ["BARRA"]);
  assert.equal(c.type, "PESO_LIBRE");
  assert.equal(c.source.provider, "wger");
  assert.equal(c.source.author, "wgerjhn");
  assert.equal(c.source.license, "CC-BY-SA-4.0");
  assert.ok(c.source.url.startsWith("https://wger.de/"));
  assert.ok(!c.description.includes("<"));

  assert.equal(toCandidate(byId(177)).candidate.type, "CARDIO");
  assert.equal(toCandidate(byId(254)).candidate.type, "PESO_LIBRE");
  assert.equal(toCandidate(byId(254)).candidate.typeReason, "nombre");
});

test("toCandidate no deja correos en la autoría", () => {
  const authorOf = (history, licenseAuthor) => {
    const x = structuredClone(byId(257));
    const tr = x.translations.find((t) => t.language === 4);
    tr.author_history = history;
    tr.license_author = licenseAuthor;
    x.license_author = licenseAuthor;
    return toCandidate(x).candidate.source.author;
  };
  assert.equal(authorOf(["hpmbala@gmail.com"], "hpmbala@gmail.com"), "colaboradores de wger");
  assert.equal(authorOf(["Ana", "x@y.com"], "x@y.com"), "Ana");
  assert.equal(authorOf(["Ana <ana@x.es>"], ""), "Ana");
  assert.equal(authorOf([], "z@y.com"), "colaboradores de wger");
});

test("normalizeName corrige plurales, derivados y -ción", () => {
  assert.equal(normalizeName("Sentadillas bulgaras en maquinas"), "Sentadillas búlgaras en máquinas");
  assert.equal(normalizeName("Rotacion de tronco"), "Rotación de tronco");
  assert.equal(normalizeName("Elevaciones laterales"), "Elevaciones laterales");
  assert.equal(normalizeName("Plancha isometrica"), "Plancha isométrica");
  assert.equal(normalizeName("Bicicleta eliptica"), "Bicicleta elíptica");
});

test("isNearDuplicate detecta variantes que solo añaden material", () => {
  assert.equal(isNearDuplicate("Press banca", "Press de banca con barra"), true);
  assert.equal(isNearDuplicate("Press de banca con barra", "Press banca"), true);
  assert.equal(isNearDuplicate("Press francés con mancuerna", "Press francés con mancuernas"), true);
  assert.equal(isNearDuplicate("Plancha", "Plancha lateral"), false);
  assert.equal(isNearDuplicate("Remo con barra", "Remo con barra con agarre supino"), false);
});

test("imageCandidates descarta IA, sin miniatura y licencia no admitida, y ordena principal primero y luego por id", () => {
  assert.deepEqual(imageCandidates(byId(257)).map((c) => c.id), [11]);
  assert.deepEqual(imageCandidates(byId(238)).map((c) => c.id), [22]);
  const list = imageCandidates(byId(245));
  assert.deepEqual(list.map((c) => c.id), [30, 31, 33]);
  assert.deepEqual(list.map((c) => c.isMain), [true, true, false]);
  assert.deepEqual(imageCandidates(byId(254)), []);
  assert.deepEqual(imageCandidates({ id: 1 }), []);
  const first = list[0];
  assert.equal(first.url, "https://wger.de/media/exercise-images/245/img30.png.400x400_q85.png");
  assert.equal(first.license, "CC-BY-SA-4.0");
  const foreign = structuredClone(byId(245));
  foreign.images.find((i) => i.id === 30).thumbnails.medium = "https://evil.example/a.png";
  assert.ok(!imageCandidates(foreign).some((c) => c.id === 30));
});

test("imageCandidates quita correos y usa «colaboradores de wger» si falta autor", () => {
  assert.equal(imageCandidates(byId(257))[0].author, "Ana");
  assert.equal(imageCandidates(byId(245))[0].author, "Eva");
  const [c] = imageCandidates(byId(211));
  assert.equal(c.author, "colaboradores de wger");
  assert.equal(c.license, "CC-BY-SA-3.0");
  const mail = structuredClone(byId(211));
  mail.images[0].license_author = "x@y.com";
  assert.equal(imageCandidates(mail)[0].author, "colaboradores de wger");
});

test("pickImage elige la primera; false no pone imagen; un id elige esa y un id inexistente es problema", () => {
  const images = imageCandidates(byId(245));
  assert.deepEqual(pickImage(images), {
    image: { sourceUrl: images[0].url, author: "Eva", license: "CC-BY-SA-4.0" },
    imageId: 30,
  });
  assert.deepEqual(pickImage([]), { image: null });
  assert.deepEqual(pickImage(images, false), { image: null });
  assert.equal(pickImage(images, 33).image.sourceUrl, images[2].url);
  assert.ok(!("url" in pickImage(images, 33).image));
  assert.equal(pickImage(images, 33).imageId, 33);
  const bad = pickImage(images, 999);
  assert.equal(bad.image, null);
  assert.match(bad.problem, /999/);
  assert.ok(pickImage([], 5).problem);
});
