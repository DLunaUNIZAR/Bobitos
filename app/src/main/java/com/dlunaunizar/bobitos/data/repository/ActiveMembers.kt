package com.dlunaunizar.bobitos.data.repository

import com.google.firebase.firestore.DocumentSnapshot

/** Membresía leída en la transacción; null si el documento no existe. */
internal data class MembershipRecord(val userId: String?, val status: String?, val displayName: String?)

internal data class ActiveMember(val id: String, val name: String)

private const val MEMBERSHIP_ACTIVE = "ACTIVE"

/**
 * Los de [ids] que siguen siendo miembros activos, en su orden y con el nombre de su membresía.
 * [records] va en el mismo orden que [ids]. Activo: existe, `userId == id` y `status == "ACTIVE"`.
 */
internal fun activeMembers(ids: List<String>, records: List<MembershipRecord?>): List<ActiveMember> =
    ids.zip(records).mapNotNull { (id, record) ->
        record?.takeIf { it.userId == id && it.status == MEMBERSHIP_ACTIVE }
            ?.let { ActiveMember(id, it.displayName.orEmpty()) }
    }

/** Como [activeMembers], pero si alguno de [ids] ya no es miembro activo lanza lo que devuelva [invalid]. */
internal fun activeMembersOrThrow(
    ids: List<String>,
    records: List<MembershipRecord?>,
    invalid: () -> Throwable,
): List<ActiveMember> = activeMembers(ids, records).also { if (it.size != ids.size) throw invalid() }

internal fun DocumentSnapshot.toMembershipRecord(): MembershipRecord? = if (exists()) {
    MembershipRecord(getString("userId"), getString("status"), getString("displayName"))
} else {
    null
}
