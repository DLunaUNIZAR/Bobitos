// Descarga el catálogo completo de ejercicios de wger a build/catalog/wger-exerciseinfo.json.
import { mkdir, writeFile } from "node:fs/promises";
import { assertWgerApiUrl, fetchJson } from "./wger-http.mjs";

const START = "https://wger.de/api/v2/exerciseinfo/?limit=200";
const OUT_DIR = new URL("../../build/catalog/", import.meta.url);

let url = assertWgerApiUrl(START);
let count = null;
const results = [];
while (url) {
  const page = await fetchJson(url);
  count ??= page.count;
  results.push(...page.results);
  console.log(`${results.length}/${count}`);
  url = page.next ? assertWgerApiUrl(page.next) : null;
}
if (results.length !== count) {
  console.error(`Se esperaban ${count} ejercicios y se recibieron ${results.length}`);
  process.exit(1);
}
await mkdir(OUT_DIR, { recursive: true });
await writeFile(new URL("wger-exerciseinfo.json", OUT_DIR), JSON.stringify({ fetchedAt: new Date().toISOString(), count, results }));
console.log(`Guardados ${count} ejercicios en build/catalog/wger-exerciseinfo.json`);
