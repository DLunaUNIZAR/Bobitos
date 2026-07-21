# Recetas: catálogo común y administración

El Recetario tiene dos tipos de receta, distinguidos por el campo `visibility` de
`recipes/{recipeId}` (colección top-level, no por espacio):

- **`PRIVATE`** — receta personal. La crea cualquier usuario verificado, solo la ve su dueño y
  aparece en todos sus espacios. También es el resultado de un *fork* («Guardar como mía»).
- **`GLOBAL`** — receta del **catálogo común**, visible para todos los usuarios. Solo la puede
  **publicar o curar** una cuenta administradora.

No hay Cloud Functions (plan Spark, coste 0): la curación se hace **in-app** con una cuenta admin.

## Quién es admin (allowlist de UID)

La lista de administradores se define por **UID de Firebase Auth** en **dos sitios que deben estar
sincronizados**:

1. **`firestore.rules`** → función `recipeAdmins()`. Es la **frontera de seguridad real**: aunque
   alguien manipule la app, las reglas rechazan crear/editar/borrar una receta `GLOBAL` si su UID no
   está aquí.
2. **`app/.../data/repository/RecipeAdmins.kt`** → `RecipeAdmins.uids`. Es solo un *gate* de
   interfaz: decide si el editor de recetas enseña el interruptor «Publicar en el catálogo común».
   No aporta seguridad; si se olvidara, las reglas seguirían protegiendo el catálogo.

Un UID de Firebase **no es un secreto** (ya aparece en campos como `createdBy`), por eso puede vivir
en las reglas y en el repositorio.

### Admins actuales

| UID | Cuenta |
| --- | --- |
| `dWWH7eRhHEPopJf5BHPB3Dp6fry1` | `luna.cerralbo.davi@gmail.com` |

## Añadir o rotar un admin

1. Obtén el UID en **Firebase Console → proyecto → Authentication → pestaña Users → columna
   «User UID»** de la cuenta.
2. Añádelo (o quítalo) en **los dos** sitios: `recipeAdmins()` de `firestore.rules` y
   `RecipeAdmins.uids` de `RecipeAdmins.kt`.
3. Despliega las reglas:
   ```bash
   npx firebase deploy --only firestore:rules --project dev
   ```
4. Publica una nueva versión de la app (el interruptor del editor depende de `RecipeAdmins.kt`).
5. Actualiza la tabla «Admins actuales» de este documento.

> El test de emulador (`tests/firebase-emulators.test.mjs`) usa la constante `RECIPE_ADMIN_UID`, que
> debe coincidir con `recipeAdmins()`. Si cambias el admin de referencia, actualízala también.

## Sembrar el catálogo común

1. Inicia sesión en la app con una cuenta admin (email verificado).
2. Abre **Comidas → Recetario → Crear receta**.
3. El editor muestra el interruptor **«Publicar en el catálogo común»** (solo visible para admins y
   solo al crear; la visibilidad de una receta ya existente está congelada por las reglas).
4. Actívalo y guarda: la receta se crea con `visibility = GLOBAL` y aparece en la sección
   «Recetas comunes» de todos los usuarios.

Para **corregir o retirar** una receta común, el admin la abre desde «Recetas comunes» y usa
Editar/Eliminar (las reglas permiten a un admin editar/borrar cualquier `GLOBAL`, aunque la haya
creado otra cuenta).

## Notas de seguridad y coste

- Las reglas de `recipes` **no usan `get()`/`exists()`**: validan solo con `request.auth` y
  `request.resource.data`, así que ninguna operación del catálogo consume lecturas extra.
- Publicar `GLOBAL` exige `ownerUid == request.auth.uid` **y** `uid ∈ recipeAdmins()`; un usuario
  normal solo puede crear `PRIVATE`. Editar una receta no puede cambiar `ownerUid` ni `visibility`.
