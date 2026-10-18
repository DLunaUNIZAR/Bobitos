package com.dlunaunizar.bobitos.feature.common

import com.dlunaunizar.bobitos.core.common.BobitosDates
import java.text.Collator

// Compartido entre llamadas: RuleBasedCollator.compare está sincronizado.
internal val CategoryCollator: Collator = Collator.getInstance(BobitosDates.locale)

/** Clave para juntar las categorías que solo difieren en mayúsculas o en espacios en los extremos. */
internal fun String.categoryKey(): String = trim().lowercase(BobitosDates.locale)
