package com.gearsales.leadengine.domain.model

data class LeadImportRow(
    val cnpj: String?,
    val razaoSocial: String?,
    val nomeFantasia: String?,
    val telefone: String?,
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
)
