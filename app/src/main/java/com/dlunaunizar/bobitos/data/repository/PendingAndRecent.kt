package com.dlunaunizar.bobitos.data.repository

/**
 * Una lista que se lee con dos consultas: todas las pendientes (hasta un tope de seguridad) y las hechas más
 * recientes (por páginas). [hasMoreDone] dice que hay hechas más antiguas sin leer; [pendingCapReached], que hay más
 * pendientes de las que caben en el tope y se ha cortado (la pantalla lo avisa en vez de callarlo).
 */
data class PendingAndRecent<T>(
    val items: List<T>,
    val hasMoreDone: Boolean = false,
    val pendingCapReached: Boolean = false,
)

/**
 * Une el resultado de las dos consultas. Las consultas se leen con un elemento de más (`tope + 1`) para saber si hay
 * más sin pedir un recuento aparte.
 *
 * - [pending]: la consulta de pendientes, leída con `limit(pendingCap + 1)`.
 * - [recent]: la consulta de hechas, ordenada de la más reciente a la más antigua y leída con `limit(doneLimit + 1)`.
 *   Puede traer pendientes al final (las que tienen la fecha de hecha a null) o, durante un instante, la versión
 *   nueva de un elemento que la consulta de pendientes aún no ha soltado: se descartan las que no son [isDone] y las
 *   que ya están en [pending], que manda.
 *
 * Sin duplicados por [id] y en el orden de [order], que debe ser total (desempatar por id) para que la lista no salte
 * de una emisión a otra.
 */
fun <T> mergePendingAndRecent(
    pending: List<T>,
    recent: List<T>,
    pendingCap: Int,
    doneLimit: Int,
    id: (T) -> String,
    isDone: (T) -> Boolean,
    order: Comparator<in T>,
): PendingAndRecent<T> {
    val shownPending = pending.distinctBy(id).take(pendingCap)
    val pendingIds = shownPending.mapTo(HashSet(), id)
    val done = recent.filter { isDone(it) && id(it) !in pendingIds }.distinctBy(id)
    return PendingAndRecent(
        items = (shownPending + done.take(doneLimit)).sortedWith(order),
        hasMoreDone = done.size > doneLimit,
        pendingCapReached = pending.distinctBy(id).size > pendingCap,
    )
}
