package com.wanderlog.android.presentation.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class OpenAiModelsTest {
    @Test fun newFamilySurvivesBothSettingsSanitizers() {
        for (model in listOf("gpt-5.6-sol", "gpt-5.6-terra", "gpt-5.6-luna")) {
            assertEquals(model, OpenAiModels.sanitize(" $model "))
            assertEquals(model, OpenAiModels.sanitizeParsingModel(" $model "))
        }
    }
    @Test fun savedLegacyChoicesRemainSelectable() {
        assertEquals("gpt-5.4-mini", OpenAiModels.sanitize("gpt-5.4-mini"))
        assertEquals("gpt-4o-mini", OpenAiModels.sanitizeParsingModel("gpt-4o-mini"))
    }
    @Test fun geminiModelsAndProviderSanitizeCorrectly() {
        assertEquals(AiProvider.OPENAI, AiProvider.sanitize(null))
        assertEquals(AiProvider.OPENAI, AiProvider.sanitize("unknown"))
        assertEquals(AiProvider.GEMINI, AiProvider.sanitize(" gemini "))

        for (model in listOf("gemini-4-argon", "gemini-3.8-flash", "gemini-3.6-flash", "gemini-3.5-flash-lite")) {
            assertEquals(model, GeminiModels.sanitize(" $model "))
            assertEquals(model, GeminiModels.sanitizeParsingModel(" $model "))
        }
        assertEquals(GeminiModels.DEFAULT_MODEL, GeminiModels.sanitize("invalid"))
        assertEquals(GeminiModels.DEFAULT_PARSING_MODEL, GeminiModels.sanitizeParsingModel("invalid"))
    }
}
