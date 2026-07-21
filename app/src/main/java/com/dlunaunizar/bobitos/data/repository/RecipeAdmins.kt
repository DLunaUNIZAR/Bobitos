package com.dlunaunizar.bobitos.data.repository

/**
 * Allowlist de cuentas que pueden publicar recetas del catálogo común (`visibility == GLOBAL`).
 *
 * Es solo un *gate* de interfaz (enseñar u ocultar la opción «publicar en el catálogo común»): la
 * frontera de seguridad real es `recipeAdmins()` en `firestore.rules`. Ambas listas DEBEN
 * mantenerse sincronizadas. Para añadir o rotar un admin ver `docs/RECIPES_ADMIN.md`.
 */
object RecipeAdmins {
    val uids: Set<String> = setOf(
        // luna.cerralbo.davi@gmail.com
        "dWWH7eRhHEPopJf5BHPB3Dp6fry1",
    )
}
