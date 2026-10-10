// Selección, validación y render del catálogo de ejercicios. Funciones puras.
import { EQUIPMENT, EXERCISE_TYPES, LICENSES, nearDuplicateKey, foldText, slug } from "./normalize.mjs";

const ADMITTED_LICENSES = new Set(Object.values(LICENSES));

export function validateEntry(entry) {
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
  const s = entry.source;
  if (!s || typeof s !== "object") errs.push("source ausente");
  else {
    if (s.provider !== "wger") errs.push("source.provider debe ser wger");
    if (!str(s.author) || s.author === "") errs.push("source.author vacío");
    else if (s.author.length > 200) errs.push("source.author > 200");
    if (!ADMITTED_LICENSES.has(s.license)) errs.push(`source.license no admitida: ${s.license}`);
    if (!str(s.url) || !s.url.startsWith("https://wger.de/")) errs.push("source.url no es de wger");
    else if (s.url.length > 200) errs.push("source.url > 200");
  }
  return errs;
}

/**
 * selection = {include: [{wgerId, name?, type?, muscleGroup?, equipment?, description?}], exclude: [{wgerId, reason?}]}
 */
export function buildCatalog({ candidates, selection, fetchedAt }) {
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
      equipment: [...new Set(item.equipment ?? c.equipment)].sort((a, b) => EQUIPMENT.indexOf(a) - EQUIPMENT.indexOf(b)),
      source: { ...c.source },
    };
    const errs = validateEntry(entry);
    if (errs.length) problems.push(`wger ${id} (${entry.name}): ${errs.join("; ")}`);
    if (seen.has(entry.id)) problems.push(`slug repetido «${entry.id}» (wger ${seen.get(entry.id)} y ${id})`);
    else seen.set(entry.id, id);
    if (item.type === undefined && (c.typeReason === "nombre" || c.typeReason === "defecto")) {
      warnings.push(`wger ${id} «${entry.name}»: tipo ${entry.type} inferido por «${c.typeReason}»`);
    }
    exercises.push(entry);
  }

  exercises.sort((a, b) => (a.id < b.id ? -1 : a.id > b.id ? 1 : 0));

  const groups = new Map();
  for (const e of exercises) {
    const k = nearDuplicateKey(e.name);
    groups.set(k, [...(groups.get(k) ?? []), e]);
  }
  for (const list of groups.values()) {
    if (list.length > 1) warnings.push(`casi duplicados: ${list.map((e) => `«${e.name}»`).join(", ")}`);
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
  const groups = new Map();
  for (const x of list) {
    const k = nearDuplicateKey(x.c.name);
    groups.set(k, [...(groups.get(k) ?? []), x]);
  }
  const ordered = [...groups.values()]
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
  const byReason = new Map();
  for (const r of rejected) byReason.set(r.reason, (byReason.get(r.reason) ?? 0) + 1);
  lines.push("", "## Descartados", "");
  for (const [reason, n] of byReason) lines.push(`- ${reason}: ${n}`);
  return `${lines.join("\n")}\n`;
}

function tally(items, key) {
  const m = new Map();
  for (const it of items) m.set(key(it), (m.get(key(it)) ?? 0) + 1);
  return [...m].sort((a, b) => String(a[0]).localeCompare(String(b[0]), "es"));
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
  if (warnings.length) lines.push("## Avisos", "", ...warnings.map((w) => `- ${w}`), "");
  if (notes.length) lines.push("## Notas", "", ...notes.map((n) => `- ${n}`), "");
  const groups = [...new Set(ex.map((e) => e.muscleGroup))].sort((a, b) => a.localeCompare(b, "es"));
  for (const g of groups) {
    lines.push(`## ${g}`, "", "| Nombre | Tipo | Material | wger | Licencia · autor | Descripción |", "|---|---|---|---|---|---|");
    for (const e of ex.filter((x) => x.muscleGroup === g)) {
      lines.push(
        `| ${esc(e.name)} | ${e.type} | ${e.equipment.join(", ")} | [${e.source.id}](${e.source.url}) | ${e.source.license} · ${esc(e.source.author)} | ${esc(clip(e.description, 160))} |`,
      );
    }
    lines.push("");
  }
  if (excluded.length) {
    lines.push("## Anexo: excluidos", "");
    for (const x of excluded) lines.push(`- ${x.wgerId}${x.name ? ` «${esc(x.name)}»` : ""}: ${esc(x.reason ?? "sin motivo")}`);
    lines.push("");
  }
  return `${lines.join("\n")}\n`;
}
