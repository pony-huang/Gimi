package github.ponyhuang.gimi.feature.chat.a2ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import github.ponyhuang.gimi.feature.chat.R

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChipSelect(
    label: String,
    selectedValues: List<String>,
    options: List<Pair<String, String>>,
    update: ((List<String>?) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val selectedSet = selectedValues.toSet()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (label.isNotBlank() || selectedSet.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (label.isNotBlank()) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                        color = colors.onSurface,
                    )
                }
                if (selectedSet.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.chat_a2ui_selected_count, selectedSet.size),
                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            options.forEach { (optionValue, optionLabel) ->
                val isSelected = optionValue in selectedSet
                val bg = if (isSelected) Violet.copy(alpha = 0.14f) else colors.onSurface.copy(alpha = 0.03f)
                val border = if (isSelected) Violet.copy(alpha = 0.55f) else colors.outlineVariant
                val textColor = if (isSelected) Violet else colors.onSurface.copy(alpha = 0.85f)

                Row(
                    modifier = Modifier
                        .heightIn(min = 30.dp)
                        .clip(CircleShape)
                        .background(bg, CircleShape)
                        .border(1.dp, border, CircleShape)
                        .semantics { selected = isSelected }
                        .clickable(enabled = update != null, role = Role.Checkbox) {
                            val next = if (isSelected) {
                                selectedValues.filterNot { it == optionValue }
                            } else {
                                selectedValues + optionValue
                            }
                            update?.invoke(next)
                        }
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = Violet,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                    Text(
                        text = optionLabel.ifBlank { optionValue },
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        color = textColor,
                    )
                }
            }
        }
    }
}
