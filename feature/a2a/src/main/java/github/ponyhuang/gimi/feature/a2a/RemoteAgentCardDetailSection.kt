package github.ponyhuang.gimi.feature.a2a

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.a2a.model.RemoteAgentCardSnapshot
import github.ponyhuang.gimi.domain.a2a.model.RemoteAgentSkillSnapshot
import github.ponyhuang.gimi.ui.preference.PreferenceGroupCard
import github.ponyhuang.gimi.ui.preference.PreferenceSectionTitle

/** 编辑器内的 A2A agent card 详情区：展示最近一次探测成功裁剪出的可读信息。 */

@Composable
internal fun RemoteAgentCardDetailSection(snapshot: RemoteAgentCardSnapshot) {
    PreferenceSectionTitle(text = stringResource(R.string.a2a_card_section_title))
    PreferenceGroupCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CardHeader(snapshot)
            snapshot.description?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            CardCapabilityRow(
                streaming = snapshot.supportsStreaming,
                pushNotifications = snapshot.supportsPushNotifications,
                extendedCard = snapshot.supportsExtendedAgentCard,
            )
            snapshot.skills.forEachIndexed { index, skill ->
                CardSkillRow(skill = skill, showDivider = index > 0)
            }
            CardMetaLine(
                label = stringResource(R.string.a2a_card_input_output),
                value = snapshot.defaultInputModes.joinToString("、") + " → " +
                    snapshot.defaultOutputModes.joinToString("、"),
            )
            snapshot.providerOrganization?.let { organization ->
                CardMetaLine(
                    label = stringResource(R.string.a2a_card_provider),
                    value = listOfNotNull(organization, snapshot.providerUrl).joinToString(" · "),
                )
            }
            snapshot.documentationUrl?.let {
                CardMetaLine(label = stringResource(R.string.a2a_card_docs), value = it)
            }
        }
    }
}

@Composable
private fun CardHeader(snapshot: RemoteAgentCardSnapshot) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Hub,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = snapshot.name ?: stringResource(R.string.a2a_card_section_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        snapshot.versionBadge()?.let { badge ->
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
    }
}

/** 版本与协议徽章文本；两者都缺失时不展示。 */
@Composable
private fun RemoteAgentCardSnapshot.versionBadge(): String? {
    val protocol = protocolVersion?.let { stringResource(R.string.a2a_card_protocol_value, it) }
    val version = version?.let { "v$it" }
    return listOfNotNull(version, protocol).joinToString(" · ").ifBlank { null }
}

/** 能力支持行：圆点颜色区分支持与否，未声明的字段一律视为不支持。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardCapabilityRow(streaming: Boolean, pushNotifications: Boolean, extendedCard: Boolean) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        listOf(
            stringResource(R.string.a2a_card_capability_streaming) to streaming,
            stringResource(R.string.a2a_card_capability_push) to pushNotifications,
            stringResource(R.string.a2a_card_capability_extended) to extendedCard,
        ).forEach { (label, supported) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(
                            color = if (supported) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                            shape = CircleShape,
                        ),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (supported) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                )
            }
        }
    }
}

/** 单个技能块：技能名 + 描述 + 标签徽章；技能之间以细分割线隔开。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardSkillRow(skill: RemoteAgentSkillSnapshot, showDivider: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (showDivider) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.a2a_card_skill_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Text(
                text = skill.name ?: skill.id.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }
        skill.description?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 40.dp),
            )
        }
        if (skill.tags.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(start = 40.dp),
            ) {
                skill.tags.forEach { tag ->
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            text = tag,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

/** 元信息行：左侧固定标签，右侧值文本。 */
@Composable
private fun CardMetaLine(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
