package github.ponyhuang.adkagui

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import github.ponyhuang.asssistantai.agent.model.Claude
import github.ponyhuang.asssistantai.agent.model.Openai
import java.io.File
import java.util.Properties

class PropertiesUtils {

    companion object {

        private val properties = Properties()

        init {
            var currentDir: File? = File(System.getProperty("user.dir") ?: ".")
            while (currentDir != null) {
                val file = File(currentDir, "local.properties")
                if (file.exists()) {
                    file.inputStream().use { properties.load(it) }
                    break
                }
                currentDir = currentDir.parentFile
            }
        }

        fun get(key: String, defaultValue: String = ""): String {
            return properties.getProperty(key)
                ?: System.getenv(key)
                ?: defaultValue
        }

        fun claudeModel() = Claude(
            "deepseek-v4-pro", AnthropicOkHttpClient.builder()
                .baseUrl("https://api.deepseek.com/anthropic")
                .apiKey(PropertiesUtils.get("DEEPSEEK_API_KEY"))
                .build()
        )

        fun openaiModel() = Openai(
            "deepseek-v4-pro", OpenAIOkHttpClient.builder()
                .baseUrl("https://api.deepseek.com")
                .apiKey(PropertiesUtils.get("DEEPSEEK_API_KEY"))
                .build()
        )

        fun kimiForCodingModel() = Claude(
            "kimi-for-coding", AnthropicOkHttpClient.builder()
                .baseUrl("https://api.kimi.com/coding/")
                .apiKey(PropertiesUtils.get("KIMI_API_KEY"))
                .build()
        )
    }


}