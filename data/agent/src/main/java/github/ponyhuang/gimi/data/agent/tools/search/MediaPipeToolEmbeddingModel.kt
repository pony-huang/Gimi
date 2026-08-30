package github.ponyhuang.gimi.data.agent.tools.search

import android.content.Context
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 基于 MediaPipe Universal Sentence Encoder 的本地工具文本向量模型。
 *
 * 模型随应用资产分发，MediaPipe 在进程内延迟初始化并直接从资产读取。初始化由
 * 互斥锁保护；失败时不缓存无效实例，后续搜索仍可重新尝试。
 */
@Singleton
class MediaPipeToolEmbeddingModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ToolEmbeddingModel {
    override val dimensions: Int = ToolEmbeddingDimensions.MEDIA_PIPE_USE.toInt()
    override val version: String = MODEL_VERSION

    private val initializationMutex = Mutex()
    private var textEmbedder: TextEmbedder? = null

    override suspend fun encode(text: String): FloatArray = withContext(Dispatchers.Default) {
        val embedding = getOrCreateTextEmbedder()
            .embed(text)
            .embeddingResult()
            .embeddings()
            .firstOrNull()
            ?.floatEmbedding()
            ?: error("MediaPipe Text Embedder returned no float embedding.")
        require(embedding.size == dimensions) {
            "Expected $dimensions embedding dimensions, got ${embedding.size}."
        }
        require(embedding.all(Float::isFinite)) {
            "MediaPipe Text Embedder returned a non-finite value."
        }
        embedding
    }

    private suspend fun getOrCreateTextEmbedder(): TextEmbedder {
        textEmbedder?.let { return it }
        return initializationMutex.withLock {
            textEmbedder ?: createTextEmbedder().also { textEmbedder = it }
        }
    }

    private fun createTextEmbedder(): TextEmbedder {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath(MODEL_ASSET_PATH)
            .setDelegate(Delegate.CPU)
            .build()
        val options = TextEmbedder.TextEmbedderOptions.builder()
            .setBaseOptions(baseOptions)
            .setL2Normalize(false)
            .setQuantize(false)
            .build()
        return TextEmbedder.createFromOptions(context, options)
    }

    private companion object {
        const val MODEL_VERSION: String = "universal-sentence-encoder-float32-mediapipe-v1"
        const val MODEL_ASSET_PATH: String =
            "tool-embeddings/universal-sentence-encoder/universal_sentence_encoder.tflite"
    }
}
