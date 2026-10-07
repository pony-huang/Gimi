package github.ponyhuang.gimi.data.modelcatalog.local

import github.ponyhuang.gimi.domain.modelcatalog.model.LocalModelBackend
import github.ponyhuang.gimi.domain.modelcatalog.model.LocalModelVariant

/** 固定下载来源与哈希；变体 id 只来自内置清单，不能作为任意文件路径输入。 */
internal data class LocalModelDownloadSpec(
    val variant: LocalModelVariant,
    val url: String,
    val sha256: String,
)

/** 官方 LiteRT community 模型文件快照，后端版本均使用 Gemma 4 混合量化。 */
internal object Gemma4Catalog {
    val specs = listOf(
        spec("E2B", LocalModelBackend.CPU, 2588147712L, "b3ca0d2f076785a8f4b2219ddbd2bdb99954eae1", "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c"),
        spec("E2B", LocalModelBackend.GPU, 2008432640L, "b3ca0d2f076785a8f4b2219ddbd2bdb99954eae1", "a53a59001894c58e6bdb5b9b227709f91a2e3e556baa7d85acf9c55402ba5cf5"),
        spec("E4B", LocalModelBackend.CPU, 3659530240L, "2eee7ac325f20eb8c9ac1d0e972f7c84663062da", "0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0"),
        spec("E4B", LocalModelBackend.GPU, 2969059328L, "2eee7ac325f20eb8c9ac1d0e972f7c84663062da", "4912bb5a9c30993c51a7711f763212077458529312175df0573a78323a2bb7ff"),
    )

    private fun spec(size: String, backend: LocalModelBackend, bytes: Long, revision: String, hash: String): LocalModelDownloadSpec {
        val suffix = if (backend == LocalModelBackend.GPU) "-gpu" else ""
        return LocalModelDownloadSpec(
            variant = LocalModelVariant("gemma4-${size.lowercase()}-${backend.name.lowercase()}", "gemma4", "Gemma 4 $size · ${backend.name}", backend, bytes,
                modelPageUrl = "https://huggingface.co/litert-community/gemma-4-$size-it-litert-lm"),
            url = "https://huggingface.co/litert-community/gemma-4-$size-it-litert-lm/resolve/$revision/gemma-4-$size-it$suffix.litertlm",
            sha256 = hash,
        )
    }
}
