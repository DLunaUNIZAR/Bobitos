package com.dlunaunizar.bobitos.data.recipeimport

/** Motivo por el que falló una importación desde una web. La UI lo traduce a un mensaje. */
enum class ImportFailure {
    /** La URL está vacía, mal formada o no es https. */
    InvalidUrl,

    /** No se pudo descargar la página (sin conexión, timeout, error del servidor). */
    Network,

    /** La respuesta no es HTML (p. ej. un PDF o una imagen). */
    NotHtml,

    /** La página no expone datos schema.org/Recipe (o son ilegibles). */
    NoRecipeFound,

    /** La página es demasiado grande para procesarla en el dispositivo. */
    TooLarge,
}

class RecipeImportException(val failure: ImportFailure, cause: Throwable? = null) : Exception(cause)
