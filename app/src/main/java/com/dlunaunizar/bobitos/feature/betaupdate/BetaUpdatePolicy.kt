package com.dlunaunizar.bobitos.feature.betaupdate

import com.dlunaunizar.bobitos.data.betaupdate.BetaRelease
import java.time.LocalDate

/** La comprobación automática solo corre con sesión de tester y como mucho una vez al día. */
internal fun shouldAutoCheck(
    available: Boolean,
    testerSignedIn: Boolean,
    today: LocalDate,
    lastAutoCheck: LocalDate?,
): Boolean = available && testerSignedIn && lastAutoCheck != today

/** Avisa de una versión salvo que sea la pospuesta; una más nueva sí avisa. */
internal fun shouldPrompt(release: BetaRelease?, postponedVersionCode: Long?): Boolean =
    release != null && (postponedVersionCode == null || release.versionCode > postponedVersionCode)
