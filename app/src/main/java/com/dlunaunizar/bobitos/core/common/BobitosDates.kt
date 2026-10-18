package com.dlunaunizar.bobitos.core.common

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.util.Locale

/**
 * Único punto de formato de fechas de la app: siempre en español, sea cual sea el idioma del dispositivo. Los nombres
 * van en tablas propias para que no dependan de la versión del JDK (el `MMM` cambia entre «sep.» y «sept»).
 */
@Suppress("TooManyFunctions")
object BobitosDates {
    val locale: Locale = Locale.forLanguageTag("es-ES")

    private val months = listOf(
        "enero", "febrero", "marzo", "abril", "mayo", "junio",
        "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
    )
    private val shortMonths = listOf(
        "ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sept", "oct", "nov", "dic",
    )
    private val days = listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")
    private val shortDays = listOf("lun", "mar", "mié", "jue", "vie", "sáb", "dom")
    private val initials = listOf("L", "M", "X", "J", "V", "S", "D")

    /** «Sábado, 17 de octubre». */
    fun longDay(date: LocalDate): String =
        capitalize("${days[date.dayOfWeek.value - 1]}, ${date.dayOfMonth} de ${months[date.monthValue - 1]}")

    /** «12 de octubre»; si no es del año de [today], «30 de diciembre de 2025». */
    fun dayMonth(date: LocalDate, today: LocalDate): String {
        val dayMonth = "${date.dayOfMonth} de ${months[date.monthValue - 1]}"
        return if (date.year == today.year) dayMonth else "$dayMonth de ${date.year}"
    }

    /** «Octubre de 2026». */
    fun month(month: YearMonth): String = capitalize("${months[month.monthValue - 1]} de ${month.year}")

    /** «12–18 oct 2026»; si cruza el mes, «28 sept – 4 oct 2026». */
    fun weekRange(start: LocalDate, end: LocalDate): String = if (start.month == end.month && start.year == end.year) {
        "${start.dayOfMonth}–${end.dayOfMonth} ${shortMonths[end.monthValue - 1]} ${end.year}"
    } else if (start.year == end.year) {
        "${start.dayOfMonth} ${shortMonths[start.monthValue - 1]} – " +
            "${end.dayOfMonth} ${shortMonths[end.monthValue - 1]} ${end.year}"
    } else {
        "${shortDate(start)} – ${shortDate(end)}"
    }

    /** «Lunes 12». */
    fun dayHeader(date: LocalDate): String = capitalize("${days[date.dayOfWeek.value - 1]} ${date.dayOfMonth}")

    /** «mar 13». */
    fun shortDay(date: LocalDate): String = "${shortDays[date.dayOfWeek.value - 1]} ${date.dayOfMonth}"

    /** «mar 13 – vie 16»; si es un solo día, «mar 13». */
    fun shortDayRange(start: LocalDate, end: LocalDate): String =
        if (start == end) shortDay(start) else "${shortDay(start)} – ${shortDay(end)}"

    /** «17 oct 2026». */
    fun shortDate(date: LocalDate): String = "${date.dayOfMonth} ${shortMonths[date.monthValue - 1]} ${date.year}"

    /** «17 oct, 10:30». */
    fun dateTime(dateTime: LocalDateTime): String =
        "${dateTime.dayOfMonth} ${shortMonths[dateTime.monthValue - 1]}, ${time(dateTime.toLocalTime())}"

    /** «10:30». */
    fun time(time: LocalTime): String = "%02d:%02d".format(Locale.ROOT, time.hour, time.minute)

    /** «sábado, 17 de octubre de 2026», para descripciones de accesibilidad. */
    fun accessibleDate(date: LocalDate): String =
        "${days[date.dayOfWeek.value - 1]}, ${date.dayOfMonth} de ${months[date.monthValue - 1]} de ${date.year}"

    /** «domingo, 18 de octubre», sin año ni mayúscula, para leer un día suelto en voz alta. */
    fun accessibleDay(date: LocalDate): String =
        "${days[date.dayOfWeek.value - 1]}, ${date.dayOfMonth} de ${months[date.monthValue - 1]}"

    /**
     * «del martes 13 al viernes 16 de octubre»; si cambia el mes, «del jueves 29 de octubre al lunes 2 de noviembre»
     * (con el año en cada extremo si cambia el año). Para descripciones de accesibilidad.
     */
    fun accessibleRange(start: LocalDate, end: LocalDate): String {
        val first = "${days[start.dayOfWeek.value - 1]} ${start.dayOfMonth}"
        val last = "${days[end.dayOfWeek.value - 1]} ${end.dayOfMonth} de ${months[end.monthValue - 1]}"
        return when {
            start.year != end.year ->
                "del $first de ${months[start.monthValue - 1]} de ${start.year} al $last de ${end.year}"
            start.month != end.month -> "del $first de ${months[start.monthValue - 1]} al $last"
            else -> "del $first al $last"
        }
    }

    /** L, M, X, J, V, S, D. */
    fun dayInitial(day: DayOfWeek): String = initials[day.value - 1]
}

private fun capitalize(text: String): String = text.replaceFirstChar { it.titlecase(BobitosDates.locale) }
