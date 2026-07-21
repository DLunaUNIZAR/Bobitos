package com.dlunaunizar.bobitos.core.model

/**
 * Ingrediente embebido en una receta. `quantity` y `unit` son texto libre y opcionales
 * («200» + «g», «1/2» + «cucharadita», o solo `name` para «al gusto»).
 */
data class Ingredient(val name: String, val quantity: String? = null, val unit: String? = null)
