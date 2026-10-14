package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.PantryEntry
import com.dlunaunizar.bobitos.core.model.PantryItem
import kotlinx.coroutines.flow.Flow

/** La despensa del espacio: lo que hay en casa (`spaces/{spaceId}/pantryItems`). */
interface PantryRepository {
    /** Escucha en vivo, con 1000 entradas como máximo y sin orden (lo pone la pantalla). */
    fun items(spaceId: String): Flow<List<PantryItem>>

    /** Ids de lo que hay, leídos del servidor: el primer snapshot de [items] puede salir de una caché vieja. */
    suspend fun currentIds(spaceId: String): Set<String>

    /** Apunta [entries]; las que ya están (mismo `pantryId`) no se tocan. */
    suspend fun add(spaceId: String, entries: List<PantryEntry>)

    /** Quita las entradas [ids]; las que ya no están no dan error. */
    suspend fun remove(spaceId: String, ids: List<String>)
}

enum class PantryFailure {
    NameRequired,
    NameTooLong,
    NotAuthenticated,
    EmailNotVerified,
    SpaceNotFound,
    PermissionDenied,
    Network,
    Unknown,
}

class PantryRepositoryException(val failure: PantryFailure, cause: Throwable? = null) : Exception(cause)
