package com.gearsales.leadengine.database.repositories

import com.gearsales.leadengine.database.tables.LeadsTable
import com.gearsales.leadengine.domain.model.LeadImportRow
import com.gearsales.leadengine.domain.model.LeadPriority
import com.gearsales.leadengine.domain.model.LeadRecord
import com.gearsales.leadengine.domain.model.LeadStatus
import org.jetbrains.exposed.sql.Case
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNotNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.intLiteral
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime

data class LeadListFilters(
    val q: String? = null,
    val status: String? = null,
    val prioridade: String? = null,
)

data class LeadDashboardCounts(
    val totalLeads: Long,
    val totalNovos: Long,
    val totalSorteados: Long,
    val totalContatados: Long,
    val totalRespondeu: Long,
    val totalInteressados: Long,
    val totalSemInteresse: Long,
    val totalNumeroInvalido: Long,
    val restantesParaSorteio: Long,
)

class LeadRepository {

    fun findByCnpj(cnpj: String): LeadRecord? = dbQuery {
        LeadsTable.selectAll().where { LeadsTable.cnpj eq cnpj }.firstOrNull()?.toLeadRecord()
    }

    fun insert(
        row: LeadImportRow,
        normalizedCnpj: String,
        telefoneNormalizado: String?,
        score: Int,
        prioridade: LeadPriority,
    ): Long = dbQuery {
        val insertedId = LeadsTable.insert {
            it[cnpj] = normalizedCnpj
            it[razaoSocial] = row.razaoSocial!!.trim()
            it[nomeFantasia] = row.nomeFantasia?.trim()?.takeIf { s -> s.isNotEmpty() }
            it[telefoneOriginal] = row.telefone?.trim()?.takeIf { s -> s.isNotEmpty() }
            it[LeadsTable.telefoneNormalizado] = telefoneNormalizado
            it[email] = row.email?.trim()?.takeIf { s -> s.isNotEmpty() }
            it[endereco] = row.endereco?.trim()?.takeIf { s -> s.isNotEmpty() }
            it[cidade] = row.cidade?.trim()?.takeIf { s -> s.isNotEmpty() }
            it[estado] = row.estado?.trim()?.takeIf { s -> s.isNotEmpty() }
            it[dataAbertura] = row.dataAbertura?.trim()?.takeIf { s -> s.isNotEmpty() }
            it[cnae] = row.cnae?.trim()?.takeIf { s -> s.isNotEmpty() }
            it[situacao] = row.situacao?.trim()?.takeIf { s -> s.isNotEmpty() }
            it[porte] = row.porte?.trim()?.takeIf { s -> s.isNotEmpty() }
            it[socio] = row.socio?.trim()?.takeIf { s -> s.isNotEmpty() }
            it[capitalSocial] = row.capitalSocial?.trim()?.takeIf { s -> s.isNotEmpty() }
            it[tipo] = row.tipo?.trim()?.takeIf { s -> s.isNotEmpty() }
            it[LeadsTable.score] = score
            it[LeadsTable.prioridade] = prioridade.name
            it[status] = LeadStatus.NOVO.name
            it[jaFoiSorteado] = false
            it[primeiroSorteioEm] = null
            it[quantidadeTentativas] = 0
            it[respondeu] = false
            it[interessado] = false
            it[observacoes] = null
            val now = LocalDateTime.now()
            it[createdAt] = now
            it[updatedAt] = now
        } get LeadsTable.id
        insertedId.value
    }

    fun listLeads(filters: LeadListFilters = LeadListFilters()): List<LeadRecord> = dbQuery {
        val parts = mutableListOf<Op<Boolean>>()
        filters.q?.trim()?.takeIf { it.isNotEmpty() }?.let { term ->
            val p = "%$term%"
            parts.add((LeadsTable.razaoSocial like p) or (LeadsTable.nomeFantasia like p))
        }
        filters.status?.trim()?.takeIf { it.isNotEmpty() }?.let {
            parts.add(LeadsTable.status eq it)
        }
        filters.prioridade?.trim()?.takeIf { it.isNotEmpty() }?.let {
            parts.add(LeadsTable.prioridade eq it)
        }
        val query = when (parts.size) {
            0 -> LeadsTable.selectAll()
            1 -> LeadsTable.selectAll().where { parts[0] }
            else -> LeadsTable.selectAll().where { parts.reduce { a, b -> a and b } }
        }
        query.orderBy(LeadsTable.id, SortOrder.DESC).map { it.toLeadRecord() }
    }

    fun findById(id: Long): LeadRecord? = dbQuery {
        LeadsTable.selectAll().where { LeadsTable.id eq id }.firstOrNull()?.toLeadRecord()
    }

    fun findByIdsOrdered(ids: List<Long>): List<LeadRecord> {
        if (ids.isEmpty()) return emptyList()
        return dbQuery {
            val rows = LeadsTable.selectAll()
                .where { LeadsTable.id inList ids }
                .associateBy { it[LeadsTable.id].value }
            ids.mapNotNull { rows[it]?.toLeadRecord() }
        }
    }

    fun findTopEligibleForSorteio(limit: Int): List<Long> = dbQuery {
        val priorityRank = Case()
            .When(LeadsTable.prioridade eq LeadPriority.ALTA.name, intLiteral(1))
            .When(LeadsTable.prioridade eq LeadPriority.MEDIA.name, intLiteral(2))
            .Else(intLiteral(3))
        val eligibleWhere: Op<Boolean> =
            (LeadsTable.status eq LeadStatus.NOVO.name) and
                (LeadsTable.jaFoiSorteado eq false) and
                (LeadsTable.telefoneNormalizado.isNotNull()) and
                (LeadsTable.telefoneNormalizado neq "")
        LeadsTable.select(LeadsTable.id)
            .where { eligibleWhere }
            .orderBy(
                priorityRank to SortOrder.ASC,
                LeadsTable.score to SortOrder.DESC,
                LeadsTable.id to SortOrder.ASC,
            )
            .limit(limit)
            .map { it[LeadsTable.id].value }
    }

    fun markSorteados(leadIds: List<Long>, agora: LocalDateTime) {
        if (leadIds.isEmpty()) return
        dbQuery {
            LeadsTable.update({ LeadsTable.id inList leadIds }) {
                it[status] = LeadStatus.SORTEADO.name
                it[jaFoiSorteado] = true
                it[primeiroSorteioEm] = agora
                it[updatedAt] = agora
            }
        }
    }

    fun updateLeadStatus(id: Long, newStatus: LeadStatus): Boolean = dbQuery {
        val row = LeadsTable.selectAll().where { LeadsTable.id eq id }.firstOrNull() ?: return@dbQuery false
        val currentTentativas = row[LeadsTable.quantidadeTentativas]
        val now = LocalDateTime.now()
        LeadsTable.update({ LeadsTable.id eq id }) {
            it[status] = newStatus.name
            it[updatedAt] = now
            when (newStatus) {
                LeadStatus.CONTATADO -> it[quantidadeTentativas] = currentTentativas + 1
                LeadStatus.RESPONDEU -> it[respondeu] = true
                LeadStatus.INTERESSADO -> it[interessado] = true
                LeadStatus.SEM_INTERESSE -> it[interessado] = false
                else -> Unit
            }
        }
        true
    }

    fun updateManualEdit(
        id: Long,
        razaoSocial: String,
        nomeFantasia: String?,
        telefoneOriginal: String?,
        telefoneNormalizado: String?,
        email: String?,
        endereco: String?,
        cidade: String?,
        estado: String?,
        dataAbertura: String?,
        cnae: String?,
        situacao: String?,
        porte: String?,
        socio: String?,
        capitalSocial: String?,
        tipo: String?,
        observacoes: String?,
        score: Int,
        prioridade: LeadPriority,
    ): Boolean = dbQuery {
        val now = LocalDateTime.now()
        val n = LeadsTable.update({ LeadsTable.id eq id }) {
            it[LeadsTable.razaoSocial] = razaoSocial
            it[LeadsTable.nomeFantasia] = nomeFantasia
            it[LeadsTable.telefoneOriginal] = telefoneOriginal
            it[LeadsTable.telefoneNormalizado] = telefoneNormalizado
            it[LeadsTable.email] = email
            it[LeadsTable.endereco] = endereco
            it[LeadsTable.cidade] = cidade
            it[LeadsTable.estado] = estado
            it[LeadsTable.dataAbertura] = dataAbertura
            it[LeadsTable.cnae] = cnae
            it[LeadsTable.situacao] = situacao
            it[LeadsTable.porte] = porte
            it[LeadsTable.socio] = socio
            it[LeadsTable.capitalSocial] = capitalSocial
            it[LeadsTable.tipo] = tipo
            it[LeadsTable.observacoes] = observacoes
            it[LeadsTable.score] = score
            it[LeadsTable.prioridade] = prioridade.name
            it[updatedAt] = now
        }
        n > 0
    }

    fun dashboardCounts(): LeadDashboardCounts = dbQuery {
        fun countWhere(condition: Op<Boolean>): Long =
            LeadsTable.select(LeadsTable.id).where { condition }.count()

        val elegiveis: Op<Boolean> =
            (LeadsTable.status eq LeadStatus.NOVO.name) and
                (LeadsTable.jaFoiSorteado eq false) and
                (LeadsTable.telefoneNormalizado.isNotNull()) and
                (LeadsTable.telefoneNormalizado neq "")

        LeadDashboardCounts(
            totalLeads = LeadsTable.select(LeadsTable.id).where { Op.TRUE }.count(),
            totalNovos = countWhere(LeadsTable.status eq LeadStatus.NOVO.name),
            totalSorteados = countWhere(LeadsTable.status eq LeadStatus.SORTEADO.name),
            totalContatados = countWhere(LeadsTable.status eq LeadStatus.CONTATADO.name),
            totalRespondeu = countWhere(LeadsTable.respondeu eq true),
            totalInteressados = countWhere(LeadsTable.interessado eq true),
            totalSemInteresse = countWhere(LeadsTable.status eq LeadStatus.SEM_INTERESSE.name),
            totalNumeroInvalido = countWhere(LeadsTable.status eq LeadStatus.NUMERO_INVALIDO.name),
            restantesParaSorteio = countWhere(elegiveis),
        )
    }

    private fun ResultRow.toLeadRecord(): LeadRecord = LeadRecord(
        id = this[LeadsTable.id].value,
        cnpj = this[LeadsTable.cnpj],
        razaoSocial = this[LeadsTable.razaoSocial],
        nomeFantasia = this[LeadsTable.nomeFantasia],
        telefoneOriginal = this[LeadsTable.telefoneOriginal],
        telefoneNormalizado = this[LeadsTable.telefoneNormalizado],
        email = this[LeadsTable.email],
        endereco = this[LeadsTable.endereco],
        cidade = this[LeadsTable.cidade],
        estado = this[LeadsTable.estado],
        dataAbertura = this[LeadsTable.dataAbertura],
        cnae = this[LeadsTable.cnae],
        situacao = this[LeadsTable.situacao],
        porte = this[LeadsTable.porte],
        socio = this[LeadsTable.socio],
        capitalSocial = this[LeadsTable.capitalSocial],
        tipo = this[LeadsTable.tipo],
        score = this[LeadsTable.score],
        prioridade = this[LeadsTable.prioridade],
        status = this[LeadsTable.status],
        jaFoiSorteado = this[LeadsTable.jaFoiSorteado],
        primeiroSorteioEm = this[LeadsTable.primeiroSorteioEm],
        quantidadeTentativas = this[LeadsTable.quantidadeTentativas],
        respondeu = this[LeadsTable.respondeu],
        interessado = this[LeadsTable.interessado],
        observacoes = this[LeadsTable.observacoes],
        createdAt = this[LeadsTable.createdAt],
        updatedAt = this[LeadsTable.updatedAt],
    )

    private companion object {
        private fun <T> dbQuery(block: () -> T): T {
            return if (TransactionManager.currentOrNull() != null) {
                block()
            } else {
                transaction { block() }
            }
        }
    }
}
