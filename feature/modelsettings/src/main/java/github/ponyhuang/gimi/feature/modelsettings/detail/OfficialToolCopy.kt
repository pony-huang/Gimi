package github.ponyhuang.gimi.feature.modelsettings.detail

import androidx.annotation.StringRes
import github.ponyhuang.gimi.feature.modelsettings.R

/**
 * 厂商工具的界面文案：按目录 ID 给出无厂商前缀的短名称与具体作用描述。
 *
 * 工具能力页的分组标题已是厂商名，行内不再重复；目录里的 [OfficialToolSpec.displayName]
 * 面向 Agent 检索，保留英文，界面一律走这张表。未收录的工具回落到 displayName。
 */
internal data class OfficialToolCopy(
    @param:StringRes val nameRes: Int,
    @param:StringRes val descriptionRes: Int,
)

internal val officialToolCopyById: Map<String, OfficialToolCopy> = mapOf(
    "deepseek_web_search" to OfficialToolCopy(
        R.string.modelsettings_tool_name_web_search,
        R.string.modelsettings_tool_desc_independent_cross_model,
    ),
    "deepseek_web_fetch" to OfficialToolCopy(
        R.string.modelsettings_tool_name_web_fetch,
        R.string.modelsettings_tool_desc_deepseek_fetch,
    ),
    "anthropic_web_search" to OfficialToolCopy(
        R.string.modelsettings_tool_name_web_search,
        R.string.modelsettings_tool_desc_native_web_search,
    ),
    "minimax_web_search" to OfficialToolCopy(
        R.string.modelsettings_tool_name_web_search,
        R.string.modelsettings_tool_desc_native_web_search,
    ),
    "minimax_image_generation" to OfficialToolCopy(
        R.string.modelsettings_tool_name_image_generation,
        R.string.modelsettings_tool_desc_image_generation,
    ),
    "gemini_web_search" to OfficialToolCopy(
        R.string.modelsettings_tool_name_google_search,
        R.string.modelsettings_tool_desc_gemini_native,
    ),
    "gemini_url_context" to OfficialToolCopy(
        R.string.modelsettings_tool_name_url_context,
        R.string.modelsettings_tool_desc_gemini_native,
    ),
    "gemini_google_maps" to OfficialToolCopy(
        R.string.modelsettings_tool_name_google_maps,
        R.string.modelsettings_tool_desc_gemini_native,
    ),
    "glm_web_search" to OfficialToolCopy(
        R.string.modelsettings_tool_name_glm_web,
        R.string.modelsettings_tool_desc_glm_web,
    ),
    "kimi_web_tools" to OfficialToolCopy(
        R.string.modelsettings_tool_name_kimi_web,
        R.string.modelsettings_tool_desc_independent_cross_model,
    ),
)
