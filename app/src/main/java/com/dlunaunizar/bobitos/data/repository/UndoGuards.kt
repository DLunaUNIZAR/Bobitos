package com.dlunaunizar.bobitos.data.repository

/** Deshacer un alta solo borra si quien deshace es el último que escribió el elemento. */
internal fun isLastWriter(updatedBy: String?, userId: String): Boolean = updatedBy == userId
