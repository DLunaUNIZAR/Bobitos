// Selección, validación y render del catálogo de ejercicios. Funciones puras.
import {
  BOBITOS_AUTHOR,
  EQUIPMENT,
  EXERCISE_TYPES,
  LICENSES,
  PROVIDERS,
  SET_MEASURES,
  STRENGTH_TYPES,
  WGER_MEDIA_PREFIX,
  groupBy,
  isNearDuplicate,
  foldText,
  nearDuplicateKey,
  pickImage,
  slug,
  sortEquipment,
} from "./normalize.mjs";
import { sha256Hex } from "./images.mjs";

const HASH_RE = /^[0-9a-f]{64}$/;
const ADMITTED_LICENSES = new Set(Object.values(LICENSES));

export function validateEntry(entry, { hashOptional = false } = {}) {
  const errs = [];
  const str = (v) => typeof v === "string";
  if (!str(entry.name) || entry.name.trim() === "") errs.push("name vacío");
  else if (entry.name.length > 120) errs.push("name > 120");
  if (!str(entry.id) || entry.id === "") errs.push("id vacío");
  else if (str(entry.name) && entry.id !== slug(entry.name)) errs.push(`id «${entry.id}» no es slug(name)`);
  if (!EXERCISE_TYPES.includes(entry.type)) errs.push(`type inválido: ${entry.type}`);
  if (!str(entry.muscleGroup) || entry.muscleGroup.trim() === "") errs.push("muscleGroup vacío");
  else if (entry.muscleGroup.length > 60) errs.push("muscleGroup > 60");
  if (!str(entry.description) || entry.description.trim() === "") errs.push("description vacía");
  else if (entry.description.length > 2000) errs.push("description > 2000");
  if (!Array.isArray(entry.equipment)) errs.push("equipment no es lista");
  else {
    for (const e of entry.equipment) if (!EQUIPMENT.includes(e)) errs.push(`equipment inválido: ${e}`);
    if (new Set(entry.equipment).size !== entry.equipment.length) errs.push("equipment repetido");
  }
  if (entry.measure !== undefined) {
    if (!SET_MEASURES.includes(entry.measure)) errs.push(`measure inválido: ${entry.measure}`);
    else if (entry.measure === "SECONDS" && !STRENGTH_TYPES.includes(entry.type)) {
      errs.push(`measure SECONDS no admitido en ${entry.type}`);
    }
  }
  const s = entry.source;
  if (!s || typeof s !== "object") errs.push("source ausente");
  else {
    if (!PROVIDERS.includes(s.provider)) errs.push(`source.provider no admitido: ${s.provider}`);
    if (!str(s.author) || s.author === "") errs.push("source.author vacío");
    else if (s.author.length > 200) errs.push("source.author > 200");
    if (!ADMITTED_LICENSES.has(s.license)) errs.push(`source.license no admitida: ${s.license}`);
    if (!Number.isInteger(s.id) || s.id <= 0) errs.push("source.id debe ser un entero > 0");
    if (s.provider === "bobitos") {
      if (s.url !== undefined) errs.push("source.url no se admite en fichas bobitos");
      if (s.license !== "CC-BY-SA-4.0") errs.push("source.license de bobitos debe ser CC-BY-SA-4.0");
      if (s.author !== BOBITOS_AUTHOR) errs.push(`source.author de bobitos debe ser «${BOBITOS_AUTHOR}»`);
    } else if (s.provider === "wger") {
      if (!str(s.url) || !s.url.startsWith("https://wger.de/")) errs.push("source.url no es de wger");
      else if (s.url.length > 200) errs.push("source.url > 200");
    }
  }
  if (entry.image !== undefined && entry.image !== null) {
    const im = entry.image;
    if (s?.provider !== "wger") errs.push("image solo se admite con proveedor wger");
    if (typeof im !== "object" || Array.isArray(im)) errs.push("image debe ser un mapa");
    else {
      for (const k of Object.keys(im)) if (!["hash", "author", "license", "sourceUrl"].includes(k)) errs.push(`image.${k} no admitido`);
      if (im.hash === undefined && hashOptional) {
        // Preparación de fuentes: el hash se completa al leer data/catalog/images.
      } else if (!str(im.hash) || !HASH_RE.test(im.hash)) errs.push("image.hash debe tener 64 caracteres hexadecimales en minúscula");
      if (!str(im.sourceUrl) || !im.sourceUrl.startsWith(WGER_MEDIA_PREFIX)) errs.push("image.sourceUrl no es de wger.de/media");
      else if (im.sourceUrl.length > 300) errs.push("image.sourceUrl > 300");
      if (im.author !== undefined && (!str(im.author) || im.author.length > 200)) errs.push("image.author inválido (≤ 200)");
      if (!ADMITTED_LICENSES.has(im.license)) errs.push(`image.license no admitida: ${im.license}`);
    }
  }
  return errs;
}

/**
 * selection = {
 *   include: [{wgerId, name?, type?, muscleGroup?, equipment?, description?, measure?, image?: false | id}],
 *   exclude: [{wgerId, reason?}],
 *   custom?: [{bobitosId, name, type, muscleGroup, equipment, description, measure?}],
 * }
 */
/**
 * `imageBytes(id)`: bytes de data/catalog/images/<id>.webp (o undefined si falta). Con él, `image.hash` se
 * completa y su ausencia es un problema; sin él (preparación de las descargas) la imagen queda sin hash.
 */
export function buildCatalog({ candidates, selection, fetchedAt, imageBytes }) {
  const problems = [];
  const warnings = [];
  const include = selection.include ?? [];
  const exclude = selection.exclude ?? [];
  const excludedIds = new Set(exclude.map((e) => e.wgerId));
  const exercises = [];
  const seen = new Map();

  for (const item of include) {
    const id = item.wgerId;
    if (excludedIds.has(id)) {
      problems.push(`wger ${id}: seleccionado y excluido a la vez`);
      continue;
    }
    const c = candidates.get(id);
    if (!c) {
      problems.push(`wger ${id}: no existe como candidato (inexistente o sin traducción al español)`);
      continue;
    }
    const name = item.name ?? c.name;
    const entry = {
      id: slug(name),
      name,
      type: item.type ?? c.type,
      muscleGroup: item.muscleGroup ?? c.muscleGroup,
      description: item.description ?? c.description,
      equipment: sortEquipment(item.equipment ?? c.equipment),
      measure: item.measure ?? "REPS",
      source: { ...c.source },
    };
    const picked = pickImage(c.images ?? [], item.image);
    if (picked.problem) problems.push(`wger ${id} (${name}): ${picked.problem}`);
    entry.image = picked.image;
    if (entry.image && imageBytes) {
      const bytes = imageBytes(entry.id);
      if (bytes === undefined || bytes === null) problems.push(`wger ${id} (${name}): falta data/catalog/images/${entry.id}.webp`);
      else entry.image = { hash: sha256Hex(bytes), ...entry.image };
    }
    const errs = validateEntry(entry, { hashOptional: !imageBytes });
    if (errs.length) problems.push(`wger ${id} (${entry.name}): ${errs.join("; ")}`);
    if (seen.has(entry.id)) problems.push(`slug repetido «${entry.id}» (${seen.get(entry.id)} y wger ${id})`);
    else seen.set(entry.id, `wger ${id}`);
    if (item.type === undefined && (c.typeReason === "nombre" || c.typeReason === "defecto")) {
      warnings.push(`wger ${id} «${entry.name}»: tipo ${entry.type} inferido por «${c.typeReason}»`);
    }
    exercises.push(entry);
  }

  const bobitosIds = new Set();
  for (const item of selection.custom ?? []) {
    const label = `ficha propia ${item.bobitosId} (${item.name})`;
    if (!Number.isInteger(item.bobitosId) || item.bobitosId <= 0) {
      problems.push(`${label}: bobitosId debe ser un entero > 0`);
    } else if (bobitosIds.has(item.bobitosId)) problems.push(`${label}: bobitosId repetido`);
    else bobitosIds.add(item.bobitosId);
    const entry = {
      id: slug(item.name ?? ""),
      name: item.name,
      type: item.type,
      muscleGroup: item.muscleGroup,
      description: item.description,
      equipment: sortEquipment(item.equipment ?? []),
      measure: item.measure ?? "REPS",
      source: { provider: "bobitos", id: item.bobitosId, author: BOBITOS_AUTHOR, license: "CC-BY-SA-4.0" },
      image: null,
    };
    const errs = validateEntry(entry);
    if (errs.length) problems.push(`${label}: ${errs.join("; ")}`);
    if (seen.has(entry.id)) problems.push(`slug repetido «${entry.id}» (${seen.get(entry.id)} y ${label})`);
    else seen.set(entry.id, label);
    exercises.push(entry);
  }

  exercises.sort((a, b) => (a.id < b.id ? -1 : a.id > b.id ? 1 : 0));

  for (let i = 0; i < exercises.length; i++) {
    for (let j = i + 1; j < exercises.length; j++) {
      if (isNearDuplicate(exercises[i].name, exercises[j].name)) {
        warnings.push(`casi duplicados: «${exercises[i].name}», «${exercises[j].name}»`);
      }
    }
  }

  return {
    catalog: { schemaVersion: 1, license: "CC-BY-SA-4.0", source: "https://wger.de", fetchedAt, exercises },
    problems,
    warnings,
  };
}

const COMMON_PATTERNS = [
  /press/, /sentadilla/, /peso muerto/, /remo/, /dominada/, /curl/, /fondos/, /zancada/, /plancha/, /hip thrust/,
  /jalon/, /elevacion/, /apertura/, /extension/, /prensa/, /crunch/, /face pull/, /encogimiento/,
];
const EXCLUDE_PATTERNS = [/estiramiento/, /movilidad/, /\d{3,}/, /rehabilitacion/, /yoga/];

export function scoreCandidate(c) {
  const folded = foldText(c.name);
  let score = 0;
  for (const p of COMMON_PATTERNS) if (p.test(folded)) score += 2;
  if ((c.description ?? "").length >= 200) score += 1;
  if (c.source?.license === "CC-BY-SA-4.0") score += 1;
  for (const p of EXCLUDE_PATTERNS) if (p.test(folded)) score -= 3;
  return score;
}

const esc = (s) => String(s ?? "").replace(/\|/g, "\\|").replace(/\s*\n\s*/g, " ");
const clip = (s, n) => (s.length > n ? `${s.slice(0, n - 1)}…` : s);

export function renderCandidates(candidates, rejected) {
  const list = [...candidates].map((c) => ({ c, score: scoreCandidate(c) }));
  const ordered = [...groupBy(list, (x) => nearDuplicateKey(x.c.name)).values()]
    .map((g) => g.sort((a, b) => b.score - a.score || a.c.wgerId - b.c.wgerId))
    .sort((a, b) => b[0].score - a[0].score || a[0].c.name.localeCompare(b[0].c.name, "es"));
  const lines = ["# Candidatos de wger", "", `${list.length} candidatos, ${rejected.length} descartados.`, ""];
  lines.push("| wger | Puntos | Nombre | Tipo | Grupo | Material | Licencia | Descripción |", "|---|---|---|---|---|---|---|---|");
  for (const g of ordered) {
    for (const [i, { c, score }] of g.entries()) {
      const mark = i > 0 ? "↳ " : "";
      lines.push(
        `| ${c.wgerId} | ${score} | ${mark}${esc(c.name)} | ${c.type}${c.typeReason === "nombre" || c.typeReason === "defecto" ? `? (${c.typeReason})` : ""} | ${esc(c.muscleGroup)} | ${c.equipment.join(", ")} | ${c.source.license} | ${esc(clip(c.description, 120))} |`,
      );
    }
  }
  lines.push("", "## Descartados", "");
  for (const [reason, n] of countBy(rejected, (r) => r.reason)) lines.push(`- ${reason}: ${n}`);
  return `${lines.join("\n")}\n`;
}

/** Recuento por clave, en orden de primera aparición. */
export function countBy(items, key) {
  const m = new Map();
  for (const it of items) {
    const k = key(it);
    m.set(k, (m.get(k) ?? 0) + 1);
  }
  return m;
}

/** Recuento por clave, ordenado alfabéticamente (es). */
export function tally(items, key) {
  return [...countBy(items, key)].sort((a, b) => String(a[0]).localeCompare(String(b[0]), "es"));
}

export function renderReview(catalog, warnings = [], notes = [], excluded = []) {
  const ex = catalog.exercises;
  const lines = ["# Revisión del catálogo de ejercicios", "", `${ex.length} ejercicios, obtenidos de wger el ${catalog.fetchedAt}.`, ""];
  for (const [title, key] of [
    ["Por grupo", (e) => e.muscleGroup],
    ["Por tipo", (e) => e.type],
    ["Por licencia", (e) => e.source.license],
  ]) {
    lines.push(`## ${title}`, "");
    for (const [k, n] of tally(ex, key)) lines.push(`- ${k}: ${n}`);
    lines.push("");
  }
  const withImage = ex.filter((e) => e.image).length;
  lines.push("## Imágenes", "", `${withImage} de ${ex.length} ejercicios con imagen.`, "");
  lines.push(`- Medida en segundos: ${ex.filter((e) => e.measure === "SECONDS").length}`, "");
  if (warnings.length) lines.push("## Avisos", "", ...warnings.map((w) => `- ${w}`), "");
  if (notes.length) lines.push("## Notas", "", ...notes.map((n) => `- ${n}`), "");
  const byGroup = groupBy(ex, (e) => e.muscleGroup);
  const groups = [...byGroup.keys()].sort((a, b) => a.localeCompare(b, "es"));
  for (const g of groups) {
    lines.push(
      `## ${g}`,
      "",
      "| Nombre | Tipo | Medida | Material | wger | Imagen | Licencia · autor | Descripción |",
      "|---|---|---|---|---|---|---|---|",
    );
    for (const e of byGroup.get(g)) {
      const origin = e.source.url ? `[${e.source.id}](${e.source.url})` : "propia";
      const image = e.image ? `[${e.image.hash.slice(0, 8)}](${e.image.sourceUrl}) · ${esc(e.image.author)} · ${e.image.license}` : "—";
      lines.push(
        `| ${esc(e.name)} | ${e.type} | ${e.measure ?? "REPS"} | ${e.equipment.join(", ")} | ${origin} | ${image} | ${e.source.license} · ${esc(e.source.author)} | ${esc(clip(e.description, 160))} |`,
      );
    }
    lines.push("");
  }
  const own = ex.filter((e) => e.source.provider === "bobitos");
  if (own.length) {
    lines.push("## Fichas propias (Catálogo Bobitos)", "");
    for (const e of own) lines.push(`### ${e.name}`, "", `${e.type} · ${e.muscleGroup} · ${e.measure ?? "REPS"}`, "", e.description, "");
  }
  if (excluded.length) {
    lines.push("## Anexo: excluidos", "");
    for (const x of excluded) lines.push(`- ${x.wgerId}${x.name ? ` «${esc(x.name)}»` : ""}: ${esc(x.reason ?? "sin motivo")}`);
    lines.push("");
  }
  return `${lines.join("\n")}\n`;
}
