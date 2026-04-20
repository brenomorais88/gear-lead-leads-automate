package com.gearsales.leadengine.domain.model

object LeadInteractionTypes {
    const val STATUS_CHANGE = "MUDANCA_STATUS"
    const val EDIT_LEAD = "EDICAO_LEAD"
    const val OBSERVATION = "OBSERVACAO"
    const val SYSTEM = "SYSTEM"
    const val WHATSAPP_TEMPLATE_SENT = "WHATSAPP_TEMPLATE_SENT"
    const val WHATSAPP_SEND_FAILED = "WHATSAPP_SEND_FAILED"
    const val WHATSAPP_INBOUND_MESSAGE = "WHATSAPP_INBOUND_MESSAGE"
    const val WHATSAPP_MANUAL_SENT = "WHATSAPP_MANUAL_SENT"
}

object InteractionDirection {
    const val OUTBOUND = "OUTBOUND"
    const val INBOUND = "INBOUND"
}
