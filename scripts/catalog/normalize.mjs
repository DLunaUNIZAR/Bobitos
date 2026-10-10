// Normalización de ejercicios de wger al formato del catálogo común de Bobitos.
// Funciones puras y sin dependencias.

export const SPANISH = 4;
export const EXERCISE_TYPES = ["MAQUINA", "PESO_LIBRE", "PESO_CORPORAL", "CARDIO", "OTROS"];
// Mismo orden que Kotlin `ExerciseEquipment`.
export const EQUIPMENT = [
  "BARRA",
  "BARRA_Z",
  "MANCUERNAS",
  "KETTLEBELL",
  "DISCO",
  "POLEA",
  "MAQUINA",
  "BANCO",
  "BANCO_INCLINADO",
  "BARRA_DOMINADAS",
  "ESTERILLA",
  "FITBALL",
  "BANDA_ELASTICA",
];
export const WGER_EQUIPMENT = {
  1: "BARRA",
  2: "BARRA_Z",
  3: "MANCUERNAS",
  4: "ESTERILLA",
  5: "FITBALL",
  6: "BARRA_DOMINADAS",
  7: null,
  8: "BANCO",
  9: "BANCO_INCLINADO",
  10: "KETTLEBELL",
  11: "BANDA_ELASTICA",
  12: "POLEA",
};
export const CATEGORY_GROUP = {
  8: "Brazos",
  9: "Piernas",
  10: "Abdomen",
  11: "Pecho",
  12: "Espalda",
  13: "Hombros",
  14: "Gemelos",
  15: null,
};
// Afina solo las categorías 8 (brazos) y 9 (piernas).
export const PRIMARY_MUSCLE_GROUP = {
  1: "Bíceps",
  13: "Bíceps",
  5: "Tríceps",
  10: "Cuádriceps",
  11: "Isquiotibiales",
  8: "Glúteos",
  7: "Gemelos",
  15: "Gemelos",
};
export const LICENSES = { 1: "CC-BY-SA-3.0", 2: "CC-BY-SA-4.0", 3: "CC0-1.0", 4: "CC-BY-4.0" };
export const KEEP_CASE = ["Scott", "Arnold", "Pallof", "Zottman", "Smith", "Jefferson", "Bulgaria", "Romano", "Landmine"];
export const ACCENT_FIXES = {
  jalon: "jalón",
  extension: "extensión",
  triceps: "tríceps",
  biceps: "bíceps",
  elevacion: "elevación",
  flexion: "flexión",
  frances: "francés",
  cuadriceps: "cuádriceps",
  gluteo: "glúteo",
  gluteos: "glúteos",
  bulgara: "búlgara",
  pajaro: "pájaro",
  maquina: "máquina",
  hiperextension: "hiperextensión",
  traccion: "tracción",
};

const STOP_WORDS = new Set([
  "a", "al", "con", "de", "del", "e", "el", "en", "la", "las", "lo", "los", "o", "para", "por", "u", "un", "una", "y",
]);

/** Única fuente de la URL de la ficha pública. Formato no verificado (wger responde con un control antibots). */
export function wgerExerciseUrl(id) {
  return `https://wger.de/es/exercise/${id}/view/`;
}

const NAMED_ENTITIES = {
  nbsp: " ", amp: "&", lt: "<", gt: ">", quot: '"', apos: "'", hellip: "…", ndash: "–", mdash: "—",
  laquo: "«", raquo: "»", deg: "°", times: "×", middot: "·", bull: "•", lsquo: "‘", rsquo: "’", ldquo: "“", rdquo: "”",
  aacute: "á", eacute: "é", iacute: "í", oacute: "ó", uacute: "ú", Aacute: "Á", Eacute: "É", Iacute: "Í", Oacute: "Ó",
  Uacute: "Ú", ntilde: "ñ", Ntilde: "Ñ", uuml: "ü", Uuml: "Ü", iexcl: "¡", iquest: "¿", ccedil: "ç",
  agrave: "à", egrave: "è", ograve: "ò", auml: "ä", ouml: "ö", szlig: "ß", frac12: "½",
};

function decodeEntities(s) {
  return s.replace(/&(?:#(\d+)|#[xX]([0-9a-fA-F]+)|([A-Za-z][A-Za-z0-9]*));/g, (m, dec, hex, name) => {
    if (dec || hex) {
      const cp = dec ? parseInt(dec, 10) : parseInt(hex, 16);
      try {
        return String.fromCodePoint(cp);
      } catch {
        return m;
      }
    }
    return Object.hasOwn(NAMED_ENTITIES, name) ? NAMED_ENTITIES[name] : m;
  });
}

export function htmlToText(html) {
  if (!html) return "";
  let s = String(html)
    .replace(/\s+/g, " ")
    .replace(/<br\s*\/?>/gi, "\n")
    .replace(/<\/p\s*>/gi, "\n\n")
    .replace(/<\/?(ul|ol)\b[^>]*>/gi, "\n")
    .replace(/<li\b[^>]*>/gi, "\n• ")
    .replace(/<\/(div|h[1-6]|tr)\s*>/gi, "\n")
    .replace(/<[^>]*>/g, "");
  s = decodeEntities(s)
    .replace(/[ \t ]+/g, " ")
    .split("\n")
    .map((l) => l.trim())
    .join("\n")
    .replace(/(?<=^|\n)(• [^\n]*)\n+(?=• )/g, "$1\n")
    .replace(/\n{3,}/g, "\n\n")
    .trim();
  return s;
}

export function truncateText(text, max = 1500) {
  if (text.length <= max) return text;
  const head = text.slice(0, max);
  const re = /(?<!(?:^|\s)\d{1,2})[.!?…](?=\s|$)/g;
  let last = -1;
  for (const m of head.matchAll(re)) last = m.index;
  if (last > 0) return head.slice(0, last + 1);
  const cut = text.slice(0, max - 1);
  const sp = cut.search(/\s\S*$/);
  const base = (sp > 0 ? cut.slice(0, sp) : cut).replace(/[\s,;:.\-–]+$/, "");
  return `${base}…`;
}

export function foldText(s) {
  return String(s).normalize("NFD").replace(/\p{M}+/gu, "").toLowerCase();
}

/** Agrupa conservando el orden de aparición de claves y de elementos. */
export function groupBy(items, keyFn) {
  const groups = new Map();
  for (const item of items) {
    const k = keyFn(item);
    const list = groups.get(k);
    if (list) list.push(item);
    else groups.set(k, [item]);
  }
  return groups;
}

/** Elimina repetidos y ordena según `EQUIPMENT`. */
export function sortEquipment(list) {
  return [...new Set(list)].sort((a, b) => EQUIPMENT.indexOf(a) - EQUIPMENT.indexOf(b));
}

/** Copia exacta de `slug` de CatalogIngredient.kt. */
export function slug(name) {
  return foldText(String(name).trim())
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
}

const KEEP_CASE_BY_FOLD = new Map(KEEP_CASE.map((w) => [foldText(w), w]));

export function normalizeName(raw) {
  const words = String(raw).trim().split(/\s+/).filter(Boolean);
  const out = words.map((word) => {
    const letters = word.replace(/[^\p{L}]/gu, "");
    const key = foldText(letters);
    if (KEEP_CASE_BY_FOLD.has(key)) return word.replace(letters, KEEP_CASE_BY_FOLD.get(key));
    if (letters.length >= 2 && letters.length <= 3 && letters === letters.toUpperCase() && !STOP_WORDS.has(key)) {
      return word;
    }
    const lower = word.toLowerCase();
    if (Object.hasOwn(ACCENT_FIXES, key) && lower.includes(letters.toLowerCase())) {
      return lower.replace(letters.toLowerCase(), ACCENT_FIXES[key]);
    }
    return lower;
  });
  if (out.length > 0 && !/^[A-ZÁÉÍÓÚÑ]{2,3}$/.test(out[0])) {
    const first = out[0];
    const i = first.search(/\p{L}/u);
    if (i >= 0 && !KEEP_CASE_BY_FOLD.has(foldText(first.replace(/[^\p{L}]/gu, "")))) {
      out[0] = first.slice(0, i) + first[i].toUpperCase() + first.slice(i + 1);
    }
  }
  return out.join(" ");
}

export function nearDuplicateKey(name) {
  return slug(name)
    .split("-")
    .filter((t) => t && !STOP_WORDS.has(t))
    .map((t) => (t.length > 3 && t.endsWith("s") && !t.endsWith("ss") ? t.slice(0, -1) : t))
    .sort()
    .join(" ");
}

const MACHINE_WORDS =
  /maquina|polea|prensa|smith|multipower|contractora|pec ?deck|jalon|hack|extension de (piernas|cuadriceps)|curl femoral|abductor|aductor/;
const FREE_WEIGHT_WORDS = /mancuerna|barra|disco|kettlebell|pesa rusa|landmine/;

export function inferType({ categoryId, equipment = [], name = "" }) {
  const has = (...e) => e.some((x) => equipment.includes(x));
  if (categoryId === 15) return { type: "CARDIO", reason: "categoria" };
  if (has("POLEA", "MAQUINA")) return { type: "MAQUINA", reason: "equipo" };
  if (has("BARRA", "BARRA_Z", "MANCUERNAS", "KETTLEBELL", "DISCO")) return { type: "PESO_LIBRE", reason: "equipo" };
  if (has("BARRA_DOMINADAS")) return { type: "PESO_CORPORAL", reason: "equipo" };
  const folded = foldText(name);
  if (MACHINE_WORDS.test(folded)) return { type: "MAQUINA", reason: "nombre" };
  if (FREE_WEIGHT_WORDS.test(folded)) return { type: "PESO_LIBRE", reason: "nombre" };
  if (has("BANDA_ELASTICA", "FITBALL")) return { type: "OTROS", reason: "equipo" };
  return { type: "PESO_CORPORAL", reason: "defecto" };
}

function muscleGroupOf(info) {
  const categoryId = info.category?.id;
  const base = CATEGORY_GROUP[categoryId];
  if (categoryId === 8 || categoryId === 9) {
    for (const m of info.muscles ?? []) {
      if (PRIMARY_MUSCLE_GROUP[m.id]) return PRIMARY_MUSCLE_GROUP[m.id];
    }
  }
  return base ?? "Cardio";
}

// La autoría no debe exponer correos: se quita la dirección (y los < > que la rodean).
function stripEmail(value) {
  return String(value ?? "")
    .replace(/<?[^\s<>@,;]+@[^\s<>@,;]+>?/g, "")
    .replace(/\s+/g, " ")
    .trim();
}

export function toCandidate(info) {
  const wgerId = info.id;
  const spanish = (info.translations ?? []).filter((t) => t.language === SPANISH);
  const reject = (reason, name = "") => ({ rejected: { wgerId, name, reason } });
  if (spanish.length === 0) return reject("sin-traduccion-es");

  const withText = spanish.map((t) => ({ t, text: htmlToText(t.description) }));
  const { t, text } = withText.reduce((best, x) => (x.text.length > best.text.length ? x : best));

  const licenseId = t.license ?? info.license?.id;
  const license = LICENSES[licenseId];
  if (!license) return reject("licencia-no-admitida", t.name);
  if (text.length < 20) return reject("sin-descripcion", t.name);

  const name = normalizeName(t.name ?? "");
  const equipment = sortEquipment((info.equipment ?? []).map((e) => WGER_EQUIPMENT[e.id]).filter(Boolean));
  const { type, reason } = inferType({ categoryId: info.category?.id, equipment, name });

  const history = [...new Set((t.author_history ?? []).map(stripEmail).filter(Boolean))];
  const author =
    history.length > 0
      ? history.join(", ")
      : stripEmail(t.license_author || info.license_author) || "colaboradores de wger";

  const flags = [];
  if (spanish.length > 1) flags.push("traduccion-es-multiple");
  if (equipment.length === 0) flags.push("sin-material");
  return {
    candidate: {
      wgerId,
      uuid: info.uuid,
      name,
      slug: slug(name),
      type,
      typeReason: reason,
      muscleGroup: muscleGroupOf(info),
      description: truncateText(text, 1500),
      equipment,
      source: { provider: "wger", id: wgerId, author, license, url: wgerExerciseUrl(wgerId) },
      flags,
    },
  };
}
