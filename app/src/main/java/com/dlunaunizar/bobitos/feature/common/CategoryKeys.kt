package com.dlunaunizar.bobitos.feature.common

import com.dlunaunizar.bobitos.core.common.BobitosDates
import java.text.Collator

// Compartido entre llamadas: RuleBasedCollator.compare está sincronizado.
internal val CategoryCollator: Collator = Collator.getInstance(BobitosDates.locale)

/** Clave para juntar las categorías que solo difieren en mayúsculas o en espacios en los extremos. */
internal fun String.categoryKey(): String = trim().lowercase(BobitosDates.locale)

/** Las categorías de [this] sin vacíos ni repetidas (las que solo difieren en mayúsculas o espacios se juntan), ordenadas. */
internal fun <T> Iterable<T>.categoryOptions(categoryOf: (T) -> String?): List<String> = mapNotNull {
    categoryOf(it)?.trim()?.takeIf(String::isNotEmpty)
}
    .distinctBy { it.categoryKey() }
    .sortedWith(CategoryCollator)

/** Misma categoría sin mayúsculas ni espacios en los extremos; null solo es igual a null. */
internal fun String?.sameCategoryAs(other: String?): Boolean = this?.categoryKey() == other?.categoryKey()
