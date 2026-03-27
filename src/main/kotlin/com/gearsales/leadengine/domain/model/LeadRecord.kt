package com.gearsales.leadengine.domain.model

data class LeadRecord(
    val id: Long,
    val cnpj: String,
    val razaoSocial: String,
    val nomeFantasia: String?,
    val telefoneOriginal: String?,
    val telefoneNormalizado: String?,
    val email: String?,
    val endereco: String?,
    val cidade: String?,
    val estado: String?,
    val dataAbertura: String?,
    val cnae: String?,
    val situacao: String?,
    val porte: String?,
    val socio: String?,
    val capitalSocial: String?,
    val tipo: String?,
    val score: Int,
    val prioridade: String,
    val status: String,
)
