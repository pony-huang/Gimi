package github.ponyhuang.gimi.data.agent.tools.official.minimax

import com.google.adk.kt.tools.FunctionTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.Schema
import com.google.adk.kt.types.Type
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolIds
import java.io.IOException

/** 通过 MiniMax 官方图像接口执行文生图或人物主体参考图生图。 */
internal class MinimaxImageGenerationTool private constructor(
    private val api: MinimaxImageGenerationApi,
    name: String,
    description: String,
    private val acceptsReferenceImage: Boolean,
) : FunctionTool(name = name, description = description) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = buildMap {
                put(ARG_PROMPT, Schema(type = Type.STRING, description = "图像描述，最多 1500 个字符"))
                put(ARG_MODEL, Schema(
                    type = Type.STRING,
                    description = "MiniMax 图像模型，默认 image-01",
                    enum = listOf("image-01", "image-01-live"),
                ))
                put(ARG_ASPECT_RATIO, Schema(
                    type = Type.STRING,
                    description = "输出图片宽高比，默认 1:1",
                    enum = ASPECT_RATIOS,
                ))
                if (acceptsReferenceImage) {
                    put(ARG_REFERENCE_IMAGE, Schema(
                        type = Type.STRING,
                        description = "单人正面参考图的 HTTPS URL 或 data:image/...;base64,... 数据 URL（小于 10MB）",
                    ))
                }
            },
            required = if (acceptsReferenceImage) {
                listOf(ARG_PROMPT, ARG_REFERENCE_IMAGE)
            } else {
                listOf(ARG_PROMPT)
            },
        ),
    )

    override suspend fun execute(context: ToolContext, args: Map<String, Any?>): Any {
        val prompt = (args[ARG_PROMPT] as? String)?.takeIf(String::isNotBlank)
            ?: return mapOf(ERROR_KEY to "Missing required argument: $ARG_PROMPT")
        val referenceImage = (args[ARG_REFERENCE_IMAGE] as? String)?.takeIf(String::isNotBlank)
        if (acceptsReferenceImage && referenceImage == null) {
            return mapOf(ERROR_KEY to "Missing required argument: $ARG_REFERENCE_IMAGE")
        }
        return try {
            mapOf(
                RESULT_KEY to api.generate(
                    prompt = prompt,
                    model = (args[ARG_MODEL] as? String)?.takeIf(String::isNotBlank) ?: DEFAULT_MODEL,
                    aspectRatio = (args[ARG_ASPECT_RATIO] as? String)?.takeIf(String::isNotBlank),
                    subjectReference = referenceImage,
                ),
            )
        } catch (error: IOException) {
            mapOf(ERROR_KEY to (error.message ?: "MiniMax image generation failed"))
        } catch (error: IllegalStateException) {
            mapOf(ERROR_KEY to (error.message ?: "MiniMax image generation failed"))
        }
    }

    companion object {
        fun textToImage(api: MinimaxImageGenerationApi): MinimaxImageGenerationTool =
            MinimaxImageGenerationTool(
                api = api,
                name = OfficialToolIds.MINIMAX_TEXT_TO_IMAGE,
                description = "使用 MiniMax 官方接口按文字描述生成图片。",
                acceptsReferenceImage = false,
            )

        fun imageToImage(api: MinimaxImageGenerationApi): MinimaxImageGenerationTool =
            MinimaxImageGenerationTool(
                api = api,
                name = OfficialToolIds.MINIMAX_IMAGE_TO_IMAGE,
                description = "使用 MiniMax 官方接口根据人物主体参考图和文字描述生成图片。",
                acceptsReferenceImage = true,
            )

        private const val ARG_PROMPT = "prompt"
        private const val ARG_MODEL = "model"
        private const val ARG_ASPECT_RATIO = "aspect_ratio"
        private const val ARG_REFERENCE_IMAGE = "reference_image"
        private const val DEFAULT_MODEL = "image-01"
        private const val RESULT_KEY = "image_urls"
        private const val ERROR_KEY = "error"
        private val ASPECT_RATIOS = listOf("1:1", "16:9", "4:3", "3:2", "2:3", "3:4", "9:16", "21:9")
    }
}
