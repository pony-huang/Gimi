package github.ponyhuang.gimi.data.agent.tools.mcp

/** MCP 资源受众角色；保留未知字符串，避免新版服务端角色导致解析失败。 */
@JvmInline
internal value class McpRole(val value: String)

/**
 * MCP 资源或模板携带的可选提示。
 *
 * @property audience 资源面向的角色。
 * @property priority 资源优先级，缺失时为 `null`。
 * @property lastModified 服务端声明的 ISO 8601 修改时间。
 */
internal data class McpAnnotations(
    val audience: List<McpRole> = emptyList(),
    val priority: Double? = null,
    val lastModified: String? = null,
)

/**
 * MCP SDK 图标的内部表示，避免底层 SDK 类型泄漏给调用方。
 *
 * @property src 图标 URI。
 * @property mimeType 图标 MIME 类型。
 * @property sizes 可用尺寸声明。
 * @property theme 图标适用主题。
 */
internal data class McpIcon(
    val src: String,
    val mimeType: String? = null,
    val sizes: List<String> = emptyList(),
    val theme: String? = null,
)

/**
 * MCP 服务端发布的资源信息。
 *
 * @property name 资源名称，不保证唯一。
 * @property uri 资源的唯一读取标识。
 * @property title 面向用户的显示标题。
 * @property description 资源描述。
 * @property mimeType 资源 MIME 类型。
 * @property size 原始资源字节数。
 * @property annotations 服务端资源提示。
 * @property meta 服务端 `_meta` 数据。
 * @property icons 服务端声明的图标。
 */
internal data class McpResourceInfo(
    val name: String,
    val uri: String,
    val title: String? = null,
    val description: String? = null,
    val mimeType: String? = null,
    val size: Long? = null,
    val annotations: McpAnnotations? = null,
    val meta: Map<String, Any?>? = null,
    val icons: List<McpIcon> = emptyList(),
)

/**
 * 一页 MCP 资源。
 *
 * @property resources 当前页资源。
 * @property nextCursor 下一页游标，末页为 `null`。
 */
internal data class McpResourceListing(
    val resources: List<McpResourceInfo>,
    val nextCursor: String? = null,
)

/**
 * MCP 服务端发布的资源模板。
 *
 * @property name 模板名称。
 * @property uriTemplate RFC 6570 URI 模板。
 * @property title 面向用户的显示标题。
 * @property description 模板描述。
 * @property mimeType 模板资源的 MIME 类型。
 * @property annotations 服务端模板提示。
 * @property meta 服务端 `_meta` 数据。
 * @property icons 服务端声明的图标。
 */
internal data class McpResourceTemplateInfo(
    val name: String,
    val uriTemplate: String,
    val title: String? = null,
    val description: String? = null,
    val mimeType: String? = null,
    val annotations: McpAnnotations? = null,
    val meta: Map<String, Any?>? = null,
    val icons: List<McpIcon> = emptyList(),
)

/**
 * 一页 MCP 资源模板。
 *
 * @property resourceTemplates 当前页模板。
 * @property nextCursor 下一页游标，末页为 `null`。
 */
internal data class McpResourceTemplateListing(
    val resourceTemplates: List<McpResourceTemplateInfo>,
    val nextCursor: String? = null,
)

/** MCP 资源内容的内部密封表示，调用方无需依赖底层 MCP SDK 类型。 */
internal sealed interface McpResourceContent {
    /** 服务端返回的资源 URI。 */
    val uri: String

    /** 内容 MIME 类型。 */
    val mimeType: String?

    /** 服务端 `_meta` 数据。 */
    val meta: Map<String, Any?>?

    /**
     * 文本资源内容。
     *
     * @property uri 服务端返回的资源 URI。
     * @property mimeType 内容 MIME 类型。
     * @property text 文本内容。
     * @property meta 服务端 `_meta` 数据。
     */
    data class Text(
        override val uri: String,
        override val mimeType: String?,
        val text: String,
        override val meta: Map<String, Any?>? = null,
    ) : McpResourceContent

    /**
     * 二进制资源内容。
     *
     * @property uri 服务端返回的资源 URI。
     * @property mimeType 内容 MIME 类型。
     * @property blobBase64 服务端返回的 Base64 数据。
     * @property meta 服务端 `_meta` 数据。
     */
    data class Blob(
        override val uri: String,
        override val mimeType: String?,
        val blobBase64: String,
        override val meta: Map<String, Any?>? = null,
    ) : McpResourceContent
}
