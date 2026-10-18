package com.dlunaunizar.bobitos.feature.common

/** Cabecera de un grupo de catálogo; la pantalla decide el texto de [Mine] y [Other]. */
sealed interface CatalogHeader {
    data object Mine : CatalogHeader

    data class Category(val name: String) : CatalogHeader

    data object Other : CatalogHeader
}

data class CatalogGroup<T>(val header: CatalogHeader?, val items: List<T>)

enum class CatalogLayout { BY_CATEGORY, FLAT }

/**
 * Agrupa un catálogo ya filtrado por la búsqueda y ordenado. En [CatalogLayout.FLAT] devuelve un
 * grupo sin cabecera con los tuyos primero. En [CatalogLayout.BY_CATEGORY] con [selectedCategory]
 * devuelve solo esa categoría; sin ella, «Mis…» primero, después una por categoría (orden es-ES,
 * juntando las que solo difieren en mayúsculas) y «Otras» al final. Nunca devuelve grupos vacíos.
 */
fun <T> groupCatalog(
    items: List<T>,
    isMine: (T) -> Boolean,
    categoryOf: (T) -> String?,
    layout: CatalogLayout,
    selectedCategory: String? = null,
): List<CatalogGroup<T>> {
    fun key(item: T) = categoryOf(item)?.categoryKey()?.takeIf { it.isNotEmpty() }
    val (mine, others) = items.partition(isMine)
    val groups = when {
        layout == CatalogLayout.FLAT -> listOf(CatalogGroup(null, mine + others))
        selectedCategory != null -> {
            val wanted = selectedCategory.categoryKey()
            listOf(
                CatalogGroup(
                    CatalogHeader.Category(selectedCategory.trim()),
                    (mine + others).filter {
                        key(it) ==
                            wanted
                    },
                ),
            )
        }
        else -> {
            val byCategory = others.filter { key(it) != null }.groupBy { key(it) }.values
                .map { CatalogGroup(CatalogHeader.Category(categoryOf(it.first())!!.trim()), it) }
                .sortedWith { a, b ->
                    CategoryCollator.compare(
                        (a.header as CatalogHeader.Category).name,
                        (b.header as CatalogHeader.Category).name,
                    )
                }
            listOf(CatalogGroup(CatalogHeader.Mine, mine)) + byCategory +
                CatalogGroup(CatalogHeader.Other, others.filter { key(it) == null })
        }
    }
    return groups.filter { it.items.isNotEmpty() }
}
