package com.dlunaunizar.bobitos.data.repository

internal data class ActiveMembership(val spaceId: String, val isOwner: Boolean)

// Pasos del borrado de cuenta; los implementa FirebaseAccountRepository con Firestore/Auth.
internal interface AccountDeletionSteps {
    suspend fun requireWritable()

    // Lanza NotAuthenticated si no hay usuario.
    suspend fun reauthenticate(password: String)

    suspend fun activeMemberships(): List<ActiveMembership>

    // Lo personal de users/{uid}, las preferencias y las fichas de ejercicio heredadas del catálogo común.
    suspend fun deletePersonalData()

    // Anonimiza los nombres del usuario en el espacio y sale de él.
    suspend fun leaveSpace(spaceId: String)

    suspend fun anonymizeOrDeleteOwnedRecipes()

    // Rutinas: borra las PRIVATE y anonimiza el autor de las GLOBAL.
    suspend fun anonymizeOrDeleteOwnedRoutines()

    suspend fun deleteUser()
}

internal suspend fun deleteAccountWith(steps: AccountDeletionSteps, password: String) {
    try {
        runAccountDeletion(steps, password)
    } catch (error: AccountRepositoryException) {
        throw error
    } catch (@Suppress("TooGenericExceptionCaught") error: Throwable) {
        throw AccountRepositoryException(error.failure(), error)
    }
}

private suspend fun runAccountDeletion(steps: AccountDeletionSteps, password: String) {
    if (password.isBlank()) {
        throw AccountRepositoryException(AccountFailure.PasswordRequired)
    }
    steps.requireWritable()
    steps.reauthenticate(password)
    val memberships = steps.activeMemberships()
    if (memberships.any { it.isOwner }) {
        throw AccountRepositoryException(AccountFailure.OwnerSpacesRemaining)
    }
    // Primero comprobar los espacios propios (si no, el propietario perdería lo personal y la cuenta
    // seguiría viva). Lo que puede fallar por permisos va antes de lo irreversible (salir de espacios,
    // anonimizar recetas y rutinas, borrar el usuario); además es repetible.
    steps.deletePersonalData()
    memberships.forEach { steps.leaveSpace(it.spaceId) }
    steps.anonymizeOrDeleteOwnedRecipes()
    steps.anonymizeOrDeleteOwnedRoutines()
    steps.deleteUser()
}
