package github.ponyhuang.gimi.agent.tools.search

import android.content.Context
import com.ml.shubham0204.sentence_embeddings.SentenceEmbedding
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 基于 all-MiniLM-L6-v2 的本地工具文本向量模型。
 *
 * ONNX 模型随应用资产分发；首次使用时复制到内部文件目录，因为 ONNX Runtime
 * 需要真实文件路径。初始化和复制由互斥锁保护，跨会话并发搜索只会执行一次。
 */
@Singleton
class MiniLmToolEmbeddingModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ToolEmbeddingModel {
    override val dimensions: Int = ToolEmbeddingDimensions.MINI_LM.toInt()
    override val version: String = MODEL_VERSION

    private val initializationMutex = Mutex()
    private val model = SentenceEmbedding()
    private var initialized = false

    override suspend fun encode(text: String): FloatArray = withContext(Dispatchers.IO) {
        ensureInitialized()
        model.encode(text)
    }

    private suspend fun ensureInitialized() {
        if (initialized) return
        initializationMutex.withLock {
            if (initialized) return
            val modelFile = File(context.filesDir, "$MODEL_DIRECTORY/$MODEL_FILE_NAME")
            if (!modelFile.isFile) {
                modelFile.parentFile?.mkdirs()
                context.assets.open("$ASSET_DIRECTORY/$MODEL_FILE_NAME").use { input ->
                    modelFile.outputStream().use(input::copyTo)
                }
            }
            val tokenizerBytes = context.assets
                .open("$ASSET_DIRECTORY/$TOKENIZER_FILE_NAME")
                .use { input -> input.readBytes() }
            model.init(
                modelFilepath = modelFile.absolutePath,
                tokenizerBytes = tokenizerBytes,
                useTokenTypeIds = true,
                outputTensorName = "last_hidden_state",
                useFP16 = false,
                useXNNPack = false,
                normalizeEmbeddings = true,
            )
            initialized = true
        }
    }

    private companion object {
        const val MODEL_VERSION: String = "all-MiniLM-L6-v2-qint8-arm64-v2"
        const val ASSET_DIRECTORY: String = "tool-embeddings/all-minilm-l6-v2"
        const val MODEL_DIRECTORY: String = "tool-embeddings/all-minilm-l6-v2"
        const val MODEL_FILE_NAME: String = "model.onnx"
        const val TOKENIZER_FILE_NAME: String = "tokenizer.json"
    }
}
