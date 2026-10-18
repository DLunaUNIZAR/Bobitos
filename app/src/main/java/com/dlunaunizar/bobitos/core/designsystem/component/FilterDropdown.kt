@file:Suppress("MatchingDeclarationName") // FilterOption es solo el dato de FilterDropdown.

package com.dlunaunizar.bobitos.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dlunaunizar.bobitos.core.designsystem.theme.Spacing

/** Etiqueta del icono de una opción de [FilterDropdown] que lleva [FilterOption.icon]. */
@Suppress("TopLevelPropertyNaming", "ktlint:standard:property-naming")
const val FilterOptionIconTag = "filterOptionIcon"

/**
 * Una opción de un [FilterDropdown]: su texto, si es la elegida y qué hacer al elegirla. El [icon] opcional,
 * decorativo y en [iconTint] (por defecto el color de contenido), solo se muestra en los desplegables de opción
 * simple; en los múltiples va la casilla.
 */
class FilterOption(
    val text: String,
    val selected: Boolean,
    val icon: ImageVector? = null,
    val iconTint: Color? = null,
    val onSelect: () -> Unit,
)

/**
 * Filtro de lista como desplegable con su nombre siempre a la vista y el valor elegido dentro. Con [active]
 * (valor distinto del de por defecto) contorno y etiqueta van en `primary`, que dentro de un módulo es su acento,
 * y el campo se rellena con `secondaryContainer` y lleva el valor en negrita, para no depender solo del color.
 * Ni el nombre ni el valor se cortan con «…»: el valor pasa a una segunda línea. Elegir una opción cierra el menú.
 */
@Composable
fun FilterDropdown(
    label: String,
    value: String,
    options: List<FilterOption>,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true,
    tag: String? = null,
) {
    FilterDropdownBase(label, value, options, modifier, active, enabled, tag, multiple = false)
}

/** Como [FilterDropdown], pero cada opción lleva una casilla y marcarla no cierra el menú. */
@Composable
fun MultiSelectFilterDropdown(
    label: String,
    value: String,
    options: List<FilterOption>,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true,
    tag: String? = null,
) {
    FilterDropdownBase(label, value, options, modifier, active, enabled, tag, multiple = true)
}

/**
 * Reparte filtros de dos en dos con el mismo ancho; el que no cabe pasa a ocupar su fila. Cada desplegable que se
 * ponga dentro lleva `Modifier.weight(1f)` para repartirse el ancho de la fila.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterDropdownRow(modifier: Modifier = Modifier, content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        maxItemsInEachRow = 2,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        content = content,
    )
}

/** Ancho mínimo de un desplegable dentro de [FilterDropdownRow]; por debajo de él pasa a su propia fila. */
private val FilterMinWidth = 150.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterDropdownBase(
    label: String,
    value: String,
    options: List<FilterOption>,
    modifier: Modifier,
    active: Boolean,
    enabled: Boolean,
    tag: String?,
    multiple: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    // Al cerrar el menú la casilla no debe quedarse enfocada (borde y etiqueta en color primario).
    fun close() {
        expanded = false
        focusManager.clearFocus()
    }
    val accent = MaterialTheme.colorScheme.primary
    val colors = if (active) {
        val container = MaterialTheme.colorScheme.secondaryContainer
        val onContainer = MaterialTheme.colorScheme.onSecondaryContainer
        OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = accent,
            unfocusedLabelColor = accent,
            disabledBorderColor = accent,
            unfocusedContainerColor = container,
            focusedContainerColor = container,
            disabledContainerColor = container,
            unfocusedTextColor = onContainer,
            focusedTextColor = onContainer,
        )
    } else {
        OutlinedTextFieldDefaults.colors()
    }
    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (it && enabled) expanded = true else close() },
        modifier = modifier.width(FilterMinWidth),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            maxLines = 2,
            label = { Text(label) },
            textStyle = if (active) {
                LocalTextStyle.current.copy(
                    fontWeight = FontWeight.SemiBold,
                )
            } else {
                LocalTextStyle.current
            },
            colors = colors,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && enabled) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled)
                .fillMaxWidth()
                .let { if (tag != null) it.testTag(tag) else it },
        )
        ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = ::close) {
            options.forEach { option ->
                FilterMenuItem(option, multiple, onSelected = { if (!multiple) close() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterMenuItem(option: FilterOption, multiple: Boolean, onSelected: () -> Unit) {
    DropdownMenuItem(
        text = { Text(option.text) },
        onClick = {
            onSelected()
            option.onSelect()
        },
        leadingIcon = when {
            multiple -> {
                { Checkbox(checked = option.selected, onCheckedChange = null) }
            }
            option.icon != null -> {
                {
                    Icon(
                        imageVector = option.icon,
                        contentDescription = null,
                        tint = option.iconTint ?: LocalContentColor.current,
                        modifier = Modifier.testTag(FilterOptionIconTag),
                    )
                }
            }
            else -> null
        },
        trailingIcon = if (!multiple && option.selected) {
            { Icon(Icons.Rounded.Check, contentDescription = null) }
        } else {
            null
        },
        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
        modifier = Modifier.semantics { selected = option.selected },
    )
}
