package github.ponyhuang.gimi.feature.chat.a2ui.components

import androidx.a2ui.compose.runtime.A2uiComponentProperties
import androidx.a2ui.compose.runtime.A2uiComponentScope
import androidx.a2ui.compose.runtime.A2uiProperty
import androidx.a2ui.compose.runtime.DynamicA2uiProperty
import androidx.a2ui.compose.ui.A2uiComponent
import androidx.a2ui.model.schema.A2uiAnySchema
import androidx.a2ui.model.schema.A2uiArraySchema
import androidx.a2ui.model.schema.A2uiObjectSchema
import androidx.a2ui.model.schema.A2uiSchemaKeyword
import androidx.a2ui.model.schema.A2uiStringSchema
import androidx.a2ui.model.schema.commontypes.A2uiDataBindingSchema
import androidx.a2ui.model.schema.commontypes.A2uiFunctionCallSchema
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.StarHalf
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import github.ponyhuang.gimi.ui.theme.LocalGimiDarkTheme
import java.util.Locale

private val StarGold = Color(0xFFF59E0B)

internal data class EntityBadge(
    val label: String,
    val color: String = "",
)

internal data class EntityAction(
    val label: String,
    val href: String = "",
    val variant: String = "",
)

internal object ChatEntityCard : A2uiComponent {
    override val name = "EntityCard"
    override val description =
        "Structured entity card with title, subtitle, image, badge, rating, meta, and up to 3 action buttons."

    private val titleProp = A2uiProperty.dynamicString(key = "title", required = true)
    private val subtitleProp = A2uiProperty.dynamicString(key = "subtitle")
    private val descriptionProp = A2uiProperty.dynamicString(key = "description")
    private val imageProp = A2uiProperty.dynamicString(key = "image")
    private val imageAspectRatioProp = A2uiProperty.dynamicString(key = "imageAspectRatio")
    private val metaProp = A2uiProperty.dynamicString(key = "meta")
    private val ratingProp = A2uiProperty.dynamicNumber(key = "rating")
    private val ratingCountProp = A2uiProperty.dynamicNumber(key = "ratingCount")
    private val hrefProp = A2uiProperty.dynamicString(key = "href")
    private val layoutProp = A2uiProperty.dynamicString(key = "layout")

    private val badgeProp = A2uiProperty.dynamicCustom<Map<*, *>>(
        key = "badge",
        schema = A2uiAnySchema(
            keywords = listOf(
                A2uiSchemaKeyword.OneOf(
                    listOf(
                        A2uiObjectSchema(
                            properties = mapOf(
                                "label" to A2uiStringSchema.INSTANCE,
                                "color" to A2uiStringSchema.INSTANCE,
                            ),
                            required = setOf("label"),
                            isAdditionalPropertiesAllowed = false,
                        ),
                        A2uiDataBindingSchema.DEFAULT_INSTANCE,
                        A2uiFunctionCallSchema.DEFAULT_INSTANCE,
                    ),
                ),
            ),
        ),
        safeCast = { it as? Map<*, *> },
    )

    private val actionsProp = A2uiProperty.dynamicCustom<List<*>>(
        key = "actions",
        schema = A2uiAnySchema(
            keywords = listOf(
                A2uiSchemaKeyword.OneOf(
                    listOf(
                        A2uiArraySchema(
                            items = A2uiObjectSchema(
                                properties = mapOf(
                                    "label" to A2uiStringSchema.INSTANCE,
                                    "href" to A2uiStringSchema.INSTANCE,
                                    "variant" to A2uiStringSchema.INSTANCE,
                                ),
                                required = setOf("label"),
                                isAdditionalPropertiesAllowed = false,
                            ),
                            maxItems = 3,
                        ),
                        A2uiDataBindingSchema.DEFAULT_INSTANCE,
                        A2uiFunctionCallSchema.DEFAULT_INSTANCE,
                    ),
                ),
            ),
        ),
        safeCast = { (it as? List<*>)?.take(3) },
    )

    private val onActionProp = A2uiProperty.dynamicCustom<Map<*, *>>(
        key = "onAction",
        schema = A2uiObjectSchema(properties = emptyMap(), isAdditionalPropertiesAllowed = true),
        safeCast = { it as? Map<*, *> },
    )

    private val onClickProp = A2uiProperty.dynamicCustom<Map<*, *>>(
        key = "onClick",
        schema = A2uiObjectSchema(properties = emptyMap(), isAdditionalPropertiesAllowed = true),
        safeCast = { it as? Map<*, *> },
    )

    override val properties: List<A2uiProperty<*>> = listOf(
        titleProp,
        subtitleProp,
        descriptionProp,
        imageProp,
        imageAspectRatioProp,
        metaProp,
        badgeProp,
        ratingProp,
        ratingCountProp,
        hrefProp,
        layoutProp,
        actionsProp,
        onActionProp,
        onClickProp,
    )

    @Composable
    override fun A2uiComponentScope.isReady(properties: A2uiComponentProperties): Boolean =
        properties.bind(titleProp) != null

    @Composable
    @Suppress("UNCHECKED_CAST")
    override fun A2uiComponentScope.Content(properties: A2uiComponentProperties, modifier: Modifier) {
        val title = properties.bind(titleProp).orEmpty()
        val subtitle = properties.bind(subtitleProp).orEmpty()
        val description = properties.bind(descriptionProp).orEmpty()
        val image = properties.bind(imageProp).orEmpty()
        val aspectRatio = properties.bind(imageAspectRatioProp).orEmpty()
        val meta = properties.bind(metaProp).orEmpty()
        val rating = properties.bind(ratingProp)?.toDouble()
        val ratingCount = properties.bind(ratingCountProp)?.toInt()
        val href = properties.bind(hrefProp).orEmpty()
        val layout = properties.bind(layoutProp).orEmpty()

        val badgeMap = properties.bind(badgeProp as DynamicA2uiProperty<Map<*, *>>)
        val badge = badgeMap?.let {
            val label = it["label"] as? String ?: ""
            if (label.isBlank()) null else EntityBadge(label = label, color = it["color"] as? String ?: "")
        }

        val actionList = (properties.bind(actionsProp as DynamicA2uiProperty<List<*>>)).orEmpty()
            .mapNotNull { raw ->
                val map = raw as? Map<*, *> ?: return@mapNotNull null
                val label = map["label"] as? String ?: return@mapNotNull null
                EntityAction(
                    label = label,
                    href = map["href"] as? String ?: "",
                    variant = map["variant"] as? String ?: "",
                )
            }

        val onActionPayload = properties.bind(onActionProp as DynamicA2uiProperty<Map<*, *>>)
        val onClickPayload = properties.bind(onClickProp as DynamicA2uiProperty<Map<*, *>>)

        fun openUrl(url: String) {
            dispatchAction(
                mapOf(
                    "functionCall" to mapOf(
                        "call" to "openUrl",
                        "args" to mapOf("url" to url),
                    ),
                ),
            )
        }

        fun sendEvent(payload: Map<*, *>?, fallbackName: String, context: Map<String, Any>) {
            val resolved = payload?.let { map ->
                map.entries.associate { (k, v) -> k.toString() to (v ?: "") }
            } ?: mapOf(
                "event" to mapOf(
                    "name" to fallbackName,
                    "context" to context,
                ),
            )
            dispatchAction(resolved)
        }

        EntityCard(
            title = title,
            subtitle = subtitle,
            description = description,
            imageUrl = image,
            imageAspectRatio = aspectRatio,
            meta = meta,
            badge = badge,
            rating = rating,
            ratingCount = ratingCount,
            horizontal = layout.equals("horizontal", ignoreCase = true),
            actions = actionList,
            onCardClick = when {
                href.isNotBlank() -> ({ openUrl(href) })
                onClickPayload != null -> ({ sendEvent(onClickPayload, "onClick", mapOf("title" to title)) })
                else -> null
            },
            onActionClick = { action ->
                if (action.href.isNotBlank()) {
                    openUrl(action.href)
                } else {
                    sendEvent(onActionPayload, "onAction", mapOf("label" to action.label, "title" to title))
                }
            },
            modifier = modifier,
        )
    }
}

@Composable
internal fun EntityCard(
    title: String,
    subtitle: String = "",
    description: String = "",
    imageUrl: String = "",
    imageAspectRatio: String = "",
    meta: String = "",
    badge: EntityBadge? = null,
    rating: Double? = null,
    ratingCount: Int? = null,
    horizontal: Boolean = false,
    actions: List<EntityAction> = emptyList(),
    onCardClick: (() -> Unit)? = null,
    onActionClick: (EntityAction) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val darkTheme = LocalGimiDarkTheme.current
    val colors = MaterialTheme.colorScheme
    val cardBg = if (darkTheme) colors.surfaceContainerLow else colors.surfaceContainerLowest
    val aspect = parseAspectRatio(imageAspectRatio)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onCardClick != null) Modifier.clickable(onClick = onCardClick) else Modifier),
        shape = MaterialTheme.shapes.large,
        color = cardBg,
        contentColor = colors.onSurface,
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        if (horizontal) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                if (imageUrl.isNotBlank()) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(104.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.surfaceContainer),
                    )
                }
                EntityCardBody(
                    title = title,
                    subtitle = subtitle,
                    description = description,
                    meta = meta,
                    badge = badge,
                    rating = rating,
                    ratingCount = ratingCount,
                    actions = actions,
                    onActionClick = onActionClick,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (imageUrl.isNotBlank()) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(aspect)
                            .background(colors.surfaceContainer),
                    )
                }
                EntityCardBody(
                    title = title,
                    subtitle = subtitle,
                    description = description,
                    meta = meta,
                    badge = badge,
                    rating = rating,
                    ratingCount = ratingCount,
                    actions = actions,
                    onActionClick = onActionClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EntityCardBody(
    title: String,
    subtitle: String,
    description: String,
    meta: String,
    badge: EntityBadge?,
    rating: Double?,
    ratingCount: Int?,
    actions: List<EntityAction>,
    onActionClick: (EntityAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (subtitle.isNotBlank() || badge != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = if (badge != null) 8.dp else 0.dp),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Box(Modifier.weight(1f))
                }
                badge?.let { EntityBadgePill(it) }
            }
        }

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = colors.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        if (rating != null) {
            EntityRatingRow(rating = rating, ratingCount = ratingCount)
        }

        if (description.isNotBlank()) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                ),
                color = colors.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (meta.isNotBlank()) {
            Text(
                text = meta,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (actions.isNotEmpty()) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                actions.forEach { action ->
                    val isSolid = action.variant.equals("solid", ignoreCase = true) ||
                        action.variant.equals("primary", ignoreCase = true)
                    if (isSolid) {
                        Button(
                            onClick = { onActionClick(action) },
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                            modifier = Modifier.heightIn(min = 34.dp),
                        ) {
                            Text(
                                text = action.label,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                            )
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onActionClick(action) },
                            shape = CircleShape,
                            border = BorderStroke(1.dp, colors.outlineVariant),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.onSurface),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                            modifier = Modifier.heightIn(min = 34.dp),
                        ) {
                            Text(
                                text = action.label,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EntityBadgePill(badge: EntityBadge) {
    val tint = when (badge.color.lowercase()) {
        "success", "green", "positive" -> Emerald
        "warning", "amber", "yellow" -> StarGold
        "error", "danger", "red", "negative" -> Rose
        "info", "blue", "sky" -> Sky
        else -> Violet
    }
    Text(
        text = badge.label,
        modifier = Modifier
            .background(tint.copy(alpha = 0.14f), CircleShape)
            .border(1.dp, tint.copy(alpha = 0.35f), CircleShape)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
        color = tint,
        maxLines = 1,
    )
}

@Composable
private fun EntityRatingRow(rating: Double, ratingCount: Int?) {
    val colors = MaterialTheme.colorScheme
    val clamped = rating.coerceIn(0.0, 5.0)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for (i in 1..5) {
            val icon = when {
                clamped >= i - 0.25 -> Icons.Rounded.Star
                clamped >= i - 0.75 -> Icons.AutoMirrored.Rounded.StarHalf
                else -> Icons.Rounded.StarOutline
            }
            val tint = if (clamped >= i - 0.75) StarGold else colors.outlineVariant
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(15.dp),
            )
        }
        val ratingText = buildString {
            append(String.format(Locale.US, "%.1f", clamped))
            if (ratingCount != null && ratingCount > 0) {
                append(" ($ratingCount)")
            }
        }
        Text(
            text = ratingText,
            modifier = Modifier.padding(start = 4.dp),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 12.sp,
                fontFeatureSettings = "tnum",
            ),
            color = colors.onSurfaceVariant,
        )
    }
}

private fun parseAspectRatio(raw: String): Float {
    if (raw.isBlank()) return 4f / 3f
    val parts = raw.split('/', ':').mapNotNull { it.trim().toFloatOrNull() }
    return if (parts.size == 2 && parts[0] > 0f && parts[1] > 0f) {
        (parts[0] / parts[1]).coerceIn(0.5f, 3f)
    } else {
        raw.trim().toFloatOrNull()?.coerceIn(0.5f, 3f) ?: (4f / 3f)
    }
}
