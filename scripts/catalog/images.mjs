// Miniaturas de los ejercicios: descarga única desde wger, conversión a WebP pequeño y versionado en
// data/catalog/images/<id>.webp. La app nunca pide imágenes a wger. Las E/S se inyectan para probarlas.
import { createHash } from "node:crypto";

export const IMAGE_MAX_SIDE = 400;
export const IMAGE_QUALITY = 80;
export const IMAGE_MAX_BYTES = 200 * 1024;

export const sha256Hex = (buf) => createHash("sha256").update(buf).digest("hex");

/** PNG/JPEG/WebP → WebP de ≤400 px de lado (sin ampliar), calidad 80 y sin metadatos. */
export async function toWebp(buf, { sharpImpl } = {}) {
  const sharp = sharpImpl ?? (await import("sharp")).default;
  const { data, info } = await sharp(buf)
    .rotate()
    .resize({ width: IMAGE_MAX_SIDE, height: IMAGE_MAX_SIDE, fit: "inside", withoutEnlargement: true })
    .webp({ quality: IMAGE_QUALITY })
    .toBuffer({ resolveWithObject: true });
  return { data, width: info.width, height: info.height };
}

/**
 * Descarga y convierte las imágenes que aún no están en disco.
 * `readExisting()` → Set de nombres de fichero (`<id>.webp`); `writeImage(nombre, bytes)` escribe.
 * Los ficheros que ya no usa ninguna ficha se informan en `unused` y no se borran.
 */
export async function buildImages({ entries, fetchBinary, sharpImpl, readExisting, writeImage }) {
  const existing = await readExisting();
  const result = { written: [], skipped: [], problems: [], unused: [] };
  const used = new Set();
  for (const { id, image } of entries) {
    if (!image?.sourceUrl) continue;
    used.add(`${id}.webp`);
    if (existing.has(`${id}.webp`)) {
      result.skipped.push(id);
      continue;
    }
    try {
      const { data } = await toWebp(await fetchBinary(image.sourceUrl), { sharpImpl });
      if (data.length > IMAGE_MAX_BYTES) {
        result.problems.push(`${id}: ${data.length} bytes superan el máximo de ${IMAGE_MAX_BYTES} (${image.sourceUrl})`);
        continue;
      }
      await writeImage(`${id}.webp`, data);
      result.written.push(id);
    } catch (e) {
      result.problems.push(`${id}: ${e.message}`);
    }
  }
  result.unused = [...existing].filter((n) => n.endsWith(".webp") && !used.has(n)).map((n) => n.slice(0, -5)).sort();
  return result;
}
