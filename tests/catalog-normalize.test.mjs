import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import {
  htmlToText,
  inferType,
  nearDuplicateKey,
  normalizeName,
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
