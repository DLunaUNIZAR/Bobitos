# Licencias del catálogo

La licencia CC BY-SA 4.0 de esta sección se refiere al **catálogo de ejercicios**. Los ingredientes tienen su propia sección más abajo.

El contenido del catálogo de ejercicios se publica bajo la licencia
[Creative Commons Atribución-CompartirIgual 4.0 (CC BY-SA 4.0)](https://creativecommons.org/licenses/by-sa/4.0/deed.es).

Es una obra derivada de [wger](https://wger.de) y de sus autores (datos de la API pública de ejercicios).

Cada entrada conserva su licencia y su autor de origen en el campo `source` (`license`, `author` y `url` de la ficha en wger).

## Cambios realizados

- El HTML de las descripciones se ha pasado a texto plano.
- Las descripciones largas se han recortado (hasta 1500 caracteres).
- Los nombres se han normalizado (mayúsculas y tildes).
- Se han asignado el tipo, el grupo muscular y el material de cada ejercicio.
- Se ha hecho una selección de los ejercicios incluidos.

## Fichas propias

Seis fichas (ergómetro de remo, dominadas con lastre, elevación de gemelos de pie en máquina, curl femoral de pie, natación y caminata) no proceden de wger: son texto original del proyecto, redactado para este catálogo y publicado bajo CC BY-SA 4.0. Llevan `source.provider` = `bobitos`, el autor «Catálogo Bobitos» y no tienen `url` ni imagen.

## Imágenes

Las imágenes de las fichas de wger se redistribuyen como **adaptaciones** (redimensionadas a 400 px como máximo y convertidas a WebP) de obras con licencia CC BY-SA 3.0 o CC BY-SA 4.0. Cada una conserva su autor, su licencia y su procedencia (`sourceUrl`, la dirección original en wger) en el campo `image` de su ficha, y se publica bajo la misma licencia que la obra original. Las copias se versionan en `data/catalog/images/` y el importador las almacena en Firestore. Se excluyen las imágenes generadas por inteligencia artificial.

## Ingredientes

`ingredients.json` es una lista propia de Bobitos. No deriva de wger ni de ninguna otra base de datos, y la licencia CC BY-SA del principio de este fichero se refiere solo a los ejercicios.
