package com.gearsales.leadengine.database.tables

import com.gearsales.leadengine.domain.model.LeadPriority
import com.gearsales.leadengine.domain.model.LeadStatus
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime

object LeadsTable : LongIdTable("leads") {
    val cnpj = varchar("cnpj", 20).uniqueIndex()
    val razaoSocial = varchar("razao_social", 512)
    val nomeFantasia = varchar("nome_fantasia", 512).nullable()
    val telefoneOriginal = varchar("telefone_original", 64).nullable()
    val telefoneNormalizado = varchar("telefone_normalizado", 64).nullable()
    val email = varchar("email", 320).nullable()
    val endereco = text("endereco").nullable()
    val cidade = varchar("cidade", 128).nullable()
    val estado = varchar("estado", 16).nullable()
    val dataAbertura = varchar("data_abertura", 32).nullable()
    val cnae = varchar("cnae", 32).nullable()
    val situacao = varchar("situacao", 128).nullable()
    val porte = varchar("porte", 64).nullable()
    val socio = text("socio").nullable()
    val capitalSocial = varchar("capital_social", 64).nullable()
    val tipo = varchar("tipo", 64).nullable()
    val score = integer("score").default(0)
    val prioridade = varchar("prioridade", 32).default(LeadPriority.BAIXA.name)
    val status = varchar("status", 32).default(LeadStatus.NOVO.name)
    val jaFoiSorteado = bool("ja_foi_sorteado").default(false)
    val primeiroSorteioEm = datetime("primeiro_sorteio_em").nullable()
    val quantidadeTentativas = integer("quantidade_tentativas").default(0)
    val respondeu = bool("respondeu").default(false)
    val interessado = bool("interessado").default(false)
    val observacoes = text("observacoes").nullable()
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    val updatedAt = datetime("updated_at").defaultExpression(CurrentDateTime)
}
