@file:Suppress("MatchingDeclarationName")

package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.Recipe
import com.dlunaunizar.bobitos.core.model.RecipeVisibility
import java.time.Instant

/** Dueño, autor, fechas e importación con que «Deshacer» recrea una receta. Una fecha null es la hora del servidor. */
internal data class RecipeRestoreAudit(
    val ownerUid: String,
    val createdBy: String,
    val createdAt: Instant?,
    val updatedBy: String,
    val updatedAt: Instant?,
    val importedAt: Instant?,
)

/**
 * Una común vuelve tal como era (dueño, autor, fechas y `source.importedAt`, salvo una edición aún sin confirmar, que
 * pasa a la hora del servidor), aunque la deshaga otro admin: así el importador la sigue tratando como antes del
 * borrado (reglas: `validRecipeRestore`). Si su dueño o su último editor ya no es admin, pasa a quien deshace (las
 * reglas exigen admins). Una privada vuelve como receta nueva de quien deshace.
 */
internal fun Recipe.restoreAudit(restorerUid: String, admins: Set<String>): RecipeRestoreAudit = when (visibility) {
    RecipeVisibility.GLOBAL -> {
        val owner = ownerUid.takeIf { it in admins } ?: restorerUid
        val editor = updatedBy.takeIf { it in admins } ?: restorerUid
        RecipeRestoreAudit(owner, owner, createdAt, editor, lastEditToRestore(), importedAt)
    }
    RecipeVisibility.PRIVATE -> RecipeRestoreAudit(restorerUid, restorerUid, null, restorerUid, null, null)
}

// Última edición con que vuelve una común: null (hora del servidor) si aún no había llegado al servidor o si la fecha leída
// es anterior a la importación (las reglas exigen importedAt ≤ updatedAt). Así cumple las reglas y el importador la sigue
// viendo editada en la app, sin pisar la edición.
private fun Recipe.lastEditToRestore(): Instant? =
    updatedAt.takeUnless { updatePending || (importedAt != null && importedAt > it) }
