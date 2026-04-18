package com.gearsales.leadengine.domain.service

enum class SendTrigger {
    /** Fila local / worker periódico */
    WORKER,

    /** Chamada direta (ex.: testes) */
    API_DIRECT,
}
