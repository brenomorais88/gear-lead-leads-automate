package com.gearsales.leadengine.domain.service

import kotlin.test.Test
import kotlin.test.assertTrue

class WhatsAppOperationalSettingsValidatorTest {

    @Test
    fun rejectsEmptyPhone() {
        val e = WhatsAppOperationalSettingsValidator.validateUpdate(
            phoneNumberId = "  ",
            defaultTemplateName = "t",
            defaultTemplateLanguage = "pt_BR",
            dailySendLimit = 10,
            sendDelayMinMinutes = 1,
            sendDelayMaxMinutes = 2,
        )
        assertTrue(e.any { it.contains("Phone", ignoreCase = true) })
    }

    @Test
    fun rejectsNonPositiveDailyLimit() {
        val e = WhatsAppOperationalSettingsValidator.validateUpdate(
            phoneNumberId = "1",
            defaultTemplateName = "t",
            defaultTemplateLanguage = "pt_BR",
            dailySendLimit = 0,
            sendDelayMinMinutes = 0,
            sendDelayMaxMinutes = 1,
        )
        assertTrue(e.any { it.contains("diário", ignoreCase = true) })
    }

    @Test
    fun rejectsMaxLessThanMin() {
        val e = WhatsAppOperationalSettingsValidator.validateUpdate(
            phoneNumberId = "1",
            defaultTemplateName = "t",
            defaultTemplateLanguage = "pt_BR",
            dailySendLimit = 5,
            sendDelayMinMinutes = 5,
            sendDelayMaxMinutes = 2,
        )
        assertTrue(e.any { it.contains("máximo", ignoreCase = true) })
    }

    @Test
    fun acceptsValid() {
        val e = WhatsAppOperationalSettingsValidator.validateUpdate(
            phoneNumberId = "974487825757994",
            defaultTemplateName = "tpl",
            defaultTemplateLanguage = "pt_BR",
            dailySendLimit = 10,
            sendDelayMinMinutes = 1,
            sendDelayMaxMinutes = 2,
        )
        assertTrue(e.isEmpty())
    }
}
