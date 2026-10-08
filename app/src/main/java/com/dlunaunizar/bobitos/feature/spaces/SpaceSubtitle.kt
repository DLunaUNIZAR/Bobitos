package com.dlunaunizar.bobitos.feature.spaces

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.model.SpaceRole
import com.dlunaunizar.bobitos.core.model.SpaceSummary

/** «Propietario · 3 miembros»: el rol de la persona en el espacio y cuántos miembros tiene. */
@Composable
internal fun SpaceSummary.roleAndMembers(): String {
    val roleText = stringResource(
        if (role ==
            SpaceRole.OWNER
        ) {
            R.string.space_role_owner
        } else {
            R.string.space_role_member
        },
    )
    val membersText = pluralStringResource(R.plurals.space_members, memberCount, memberCount)
    return "$roleText · $membersText"
}
