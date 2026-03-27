package com.gearsales.leadengine.web.forms

data class LeadUpdateForm(
    val razaoSocial: String? = null,
    val nomeFantasia: String? = null,
    val telefoneOriginal: String? = null,
    val email: String? = null,
    val endereco: String? = null,
    val cidade: String? = null,
    val estado: String? = null,
    val dataAbertura: String? = null,
    val cnae: String? = null,
    val situacao: String? = null,
    val porte: String? = null,
    val socio: String? = null,
    val capitalSocial: String? = null,
    val tipo: String? = null,
    val observacoes: String? = null,
)
