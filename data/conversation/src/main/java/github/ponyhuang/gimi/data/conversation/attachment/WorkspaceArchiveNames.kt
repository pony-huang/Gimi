package github.ponyhuang.gimi.data.conversation.attachment

import java.io.File

/**
 * 工作区归档命名工具（纯 JVM，供发送链路与 JVM 单测共用）。
 *
 * 归档文件名统一为 `<清洗后的显示名>-<后缀>.<ext>`：
 * - 图片后缀 = sha256 前 16 位 hex（内容寻址），同显示名同内容跨会话复用同一文件；
 * - 文档/音频后缀 = 12 位随机 hex，冲突时重生成，配合 rename 保持零拷贝归档；
 * - 扩展名始终保留，供知乎等上传桥按文件名推断 Content-Type。
 */
private const val MAX_BASE_NAME_CHARS = 60

/** 显示名允许保留的字符：中英文、数字、空格与 `._-()`；其余一律替换为 `_`。 */
private val unsafeNameChars = Regex("""[^A-Za-z0-9\u4e00-\u9fff ._\-()]""")

/**
 * 清洗用于归档文件名的显示名：非法字符替换为 `_`，截断至 60 字符，清洗后为空则回退
 * 为 `file`。输入应先经 [archiveBaseName] 去掉扩展名，避免文件名出现双重扩展名。
 */
internal fun sanitizeWorkspaceBaseName(displayName: String): String {
    val cleaned = unsafeNameChars.replace(displayName, "_").trim()
    return cleaned.take(MAX_BASE_NAME_CHARS).ifEmpty { "file" }
}

/** 去掉原始扩展名并清洗，得到归档文件名中「显示名」部分。 */
internal fun archiveBaseName(displayName: String): String =
    sanitizeWorkspaceBaseName(displayName.substringBeforeLast('.').trim())

/** 用清洗后的显示名与后缀拼出归档文件名（不含目录）。 */
internal fun workspaceArchiveFileName(baseName: String, suffix: String, extension: String?): String =
    buildString {
        append(baseName)
        append('-')
        append(suffix)
        if (extension != null) append('.').append(extension)
    }

/** 12 位随机 hex 后缀，用于文档/音频等无法廉价内容寻址的归档。 */
internal fun randomWorkspaceSuffix(): String =
    java.util.UUID.randomUUID().toString().replace("-", "").take(12)

/**
 * 在工作区根目录下找出一个未被占用的归档目标文件。
 *
 * [preferredSuffix] 通常为图片的内容寻址后缀；当目标名已存在（如文档/音频的随机后缀
 * 撞名，或同显示名不同内容的小概率哈希前缀冲突）时重新生成随机后缀。
 */
internal fun resolveWorkspaceArchiveTarget(
    workspaceRoot: File,
    baseName: String,
    extension: String?,
    preferredSuffix: String? = null,
): File = generateSequence(preferredSuffix ?: randomWorkspaceSuffix()) { randomWorkspaceSuffix() }
    .map { suffix -> File(workspaceRoot, workspaceArchiveFileName(baseName, suffix, extension)) }
    .first { !it.exists() }

/**
 * 把压缩后的图片字节写入工作区，返回最终归档文件（内容寻址：同显示名同内容直接复用）。
 *
 * 写入采用「临时名 + rename」：两个会话并发归档同名同内容图片时，交错写同一目标可能
 * 产生损坏字节；各自先写 `<final>.tmp` 再原子改名可规避。rename 失败时检查目标是否
 * 已被并发写入方产出——是则直接复用（同名即同内容），否则视为真实 IO 失败抛出。
 */
internal fun writeWorkspaceImage(
    workspaceRoot: File,
    baseName: String,
    contentSuffix: String,
    extension: String?,
    bytes: ByteArray,
): File {
    val target = File(workspaceRoot, workspaceArchiveFileName(baseName, contentSuffix, extension))
    if (target.exists()) return target
    // 临时名必须带随机后缀：确定性的 `<final>.tmp` 会让并发归档同名图片的两个写方
    // 交错写同一个临时文件，反而制造损坏字节。
    val staging = File(workspaceRoot, "${target.name}.${randomWorkspaceSuffix()}.tmp")
    staging.writeBytes(bytes)
    if (!staging.renameTo(target)) {
        staging.delete()
        check(target.exists()) { "无法写入附件工作区文件: ${target.name}" }
    }
    return target
}
