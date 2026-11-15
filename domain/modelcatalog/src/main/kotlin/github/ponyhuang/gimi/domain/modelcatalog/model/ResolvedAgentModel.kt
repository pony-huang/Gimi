package github.ponyhuang.gimi.domain.modelcatalog.model

/** Agent 运行时解析后的模型配置（跨 data 模块传递，不暴露 data 层类型）。 */
data class ResolvedAgentModel(
    /** 模型服务实例 ID；官方工具凭据与运行元数据按实例归属解析。 */
    val serviceId: String,
    /** 厂商模板 ID；厂商工具支持矩阵与协议特例按模板匹配。null 表示自定义服务。 */
    val templateId: String? = null,
    val protocol: ApiProtocol,
    val modelId: String,
    val apiKey: String,
    /** 已 trimEnd('/') 的生效端点。 */
    val modelBaseUrl: String,
    /** 该模型是否可接收图片输入。 */
    val supportsImages: Boolean = false,
    /**
     * 文档附件的进入方式：[FileIngestMode.FILE_REFERENCE] 表示实例已开启厂商文件上传，
     * 协议适配器需支持 file 引用块；[FileIngestMode.PATH_TEXT] 表示文档不传输内容、
     * 以本地路径文本送达模型；[FileIngestMode.INLINE] 维持 base64 内联语义。
     */
    val documentIngestMode: FileIngestMode = FileIngestMode.INLINE,
)
