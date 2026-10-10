// Descarga el catálogo completo de ejercicios de wger a build/catalog/wger-exerciseinfo.json.
import { mkdir, writeFile } from "node:fs/promises";

const START = "https://wger.de/api/v2/exerciseinfo/?limit=200";
const HEADERS = { "User-Agent": "BobitosCatalogBuilder/1.0 (+https://github.com/DLunaUNIZAR/Bobitos)" };
const OUT_DIR = new URL("../../build/catalog/", import.meta.url);

async function getJson(url) {
  let last;
  for (let attempt = 1; attempt <= 3; attempt++) {
    try {
      const res = await fetch(url, { headers: HEADERS });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      return await res.json();
    } catch (e) {
      last = e;
      await new Promise((r) => setTimeout(r, 1000 * attempt));
    }
  }
  throw new Error(`No se pudo descargar ${url}: ${last?.message}`);
}

let url = START;
let count = null;
const results = [];
while (url) {
  const page = await getJson(url);
  count ??= page.count;
  results.push(...page.results);
  console.log(`${results.length}/${count}`);
  url = page.next;
}
if (results.length !== count) {
  console.error(`Se esperaban ${count} ejercicios y se recibieron ${results.length}`);
  process.exit(1);
}
await mkdir(OUT_DIR, { recursive: true });
await writeFile(new URL("wger-exerciseinfo.json", OUT_DIR), JSON.stringify({ fetchedAt: new Date().toISOString(), count, results }));
console.log(`Guardados ${count} ejercicios en build/catalog/wger-exerciseinfo.json`);
