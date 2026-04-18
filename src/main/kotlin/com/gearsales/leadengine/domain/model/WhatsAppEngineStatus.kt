package com.gearsales.leadengine.domain.model

import kotlinx.serialization.Serializable

/**
 * Estado operacional consolidado do motor de disparo WhatsApp (fila + worker + cota).
 */
@Serializable
enum class WhatsAppEngineStatus {
    PAUSED,
    /** Envios automáticos suspensos fora da janela diária configurada. */
    OUTSIDE_EXECUTION_WINDOW,
    IDLE,
    WAITING_NEXT_SEND,
    READY_TO_SEND,
    PROCESSING,
    DAILY_LIMIT_REACHED,
    MISCONFIGURED,
    PENDING_WITHOUT_SCHEDULE,
    ERROR,
}
