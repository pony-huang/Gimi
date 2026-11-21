package github.ponyhuang.gimi.domain.modelcatalog.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OmniModelsTest {

    @Test
    fun isOmniModelMatchesKnownIdsCaseInsensitively() {
        assertTrue(OmniModels.isOmniModel("mimo-v2.6-pro"))
        assertTrue(OmniModels.isOmniModel("MIMO-V2.6-FLASH"))
        assertTrue(OmniModels.isOmniModel("mimo-v2.6-pro-ultraspeed"))
        assertTrue(OmniModels.isOmniModel("Qwen3.8-Omni-Flash"))
        assertTrue(OmniModels.isOmniModel("qwen3.8-omni-flash"))

        assertFalse(OmniModels.isOmniModel("gpt-4o"))
        assertFalse(OmniModels.isOmniModel("deepseek-v3"))
        assertFalse(OmniModels.isOmniModel("mimo-v2.5-asr"))
    }

    @Test
    fun isOmniLlmRequiresLlmCapabilityAndOmniTrait() {
        val omniByName = Model(id = "mimo-v2.6-pro", name = "MiMo 2.6 Pro")
        assertTrue(omniByName.isOmniLlm)

        val omniByExplicitCapability = Model(
            id = "custom-omni",
            name = "Custom Omni",
            capabilities = setOf(ModelCapability.LLM, ModelCapability.Omni),
        )
        assertTrue(omniByExplicitCapability.isOmniLlm)

        val nonLlmWithOmniName = Model(
            id = "qwen3.8-omni-flash",
            name = "Qwen Omni",
            capabilities = setOf(ModelCapability.TTS),
        )
        assertFalse(nonLlmWithOmniName.isOmniLlm)

        val regularLlm = Model(id = "gpt-4o", name = "GPT-4o")
        assertFalse(regularLlm.isOmniLlm)
    }
}
