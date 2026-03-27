package com.gearsales.leadengine.web.routes

import com.gearsales.leadengine.database.repositories.LeadInteractionRepository
import com.gearsales.leadengine.database.repositories.LeadListFilters
import com.gearsales.leadengine.database.repositories.LeadRepository
import com.gearsales.leadengine.domain.model.LeadImportRow
import com.gearsales.leadengine.domain.model.LeadInteractionTypes
import com.gearsales.leadengine.domain.model.LeadPriority
import com.gearsales.leadengine.domain.model.LeadStatus
import com.gearsales.leadengine.domain.service.LeadScoringService
import com.gearsales.leadengine.domain.service.PhoneNormalizer
import com.gearsales.leadengine.web.forms.LeadUpdateForm
import com.gearsales.leadengine.web.viewmodels.LeadDetailViewModel
import com.gearsales.leadengine.web.viewmodels.LeadInteractionViewModel
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.thymeleaf.ThymeleafContent

private val leadRepository = LeadRepository()
private val leadInteractionRepository = LeadInteractionRepository()
private val leadScoringService = LeadScoringService()

private fun String?.trimOrNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

private fun strEq(a: String?, b: String?): Boolean {
    val x = a?.trim().orEmpty()
    val y = b?.trim().orEmpty()
    return x == y
}

/** POST /leads/{id}/status pode enviar returnTo=/batches/{id} para voltar ao lote após ação. */
private fun redirectAfterStatusChange(returnTo: String?, leadId: Long, error: Boolean): String {
    val p = returnTo?.trim().orEmpty()
    val target = if (p.matches(Regex("^/batches/\\d+$"))) p else "/lead/$leadId"
    return if (error) "$target?error=status" else target
}

private suspend fun ApplicationCall.respondLeadDetailGet() {
    val id = parameters["id"]?.toLongOrNull()
    if (id == null) {
        respondText("Identificador inválido.", status = HttpStatusCode.BadRequest)
        return
    }
    val record = leadRepository.findById(id)
    if (record == null) {
        respondText("Lead não encontrado.", status = HttpStatusCode.NotFound)
        return
    }
    val error = request.queryParameters["error"].orEmpty()
    val interactions = leadInteractionRepository.listByLeadId(id).map { LeadInteractionViewModel.from(it) }
    respond(
        ThymeleafContent(
            "lead-detail",
            mapOf(
                "title" to "Lead #$id",
                "lead" to LeadDetailViewModel.from(record),
                "error" to error,
                "interactions" to interactions,
                "statusOptions" to LeadStatus.entries.map { it.name },
            ),
        ),
    )
}

private suspend fun ApplicationCall.respondLeadDetailPost() {
    val id = parameters["id"]?.toLongOrNull()
    if (id == null) {
        respondText("Identificador inválido.", status = HttpStatusCode.BadRequest)
        return
    }
    val existing = leadRepository.findById(id)
    if (existing == null) {
        respondText("Lead não encontrado.", status = HttpStatusCode.NotFound)
        return
    }
    val p = receiveParameters()
    val form = LeadUpdateForm(
        razaoSocial = p["razaoSocial"],
        nomeFantasia = p["nomeFantasia"],
        telefoneOriginal = p["telefoneOriginal"],
        email = p["email"],
        endereco = p["endereco"],
        cidade = p["cidade"],
        estado = p["estado"],
        dataAbertura = p["dataAbertura"],
        cnae = p["cnae"],
        situacao = p["situacao"],
        porte = p["porte"],
        socio = p["socio"],
        capitalSocial = p["capitalSocial"],
        tipo = p["tipo"],
        observacoes = p["observacoes"],
    )
    val razao = form.razaoSocial?.trim().orEmpty()
    if (razao.isEmpty()) {
        respondRedirect("/lead/$id?error=razao", permanent = false)
        return
    }
    val telefoneOrig = form.telefoneOriginal.trimOrNull()
    val telefoneNz = PhoneNormalizer.normalizeForWhatsApp(telefoneOrig)
    val row = LeadImportRow(
        cnpj = existing.cnpj,
        razaoSocial = razao,
        nomeFantasia = form.nomeFantasia.trimOrNull(),
        telefone = telefoneOrig,
        email = form.email.trimOrNull(),
        endereco = form.endereco.trimOrNull(),
        cidade = form.cidade.trimOrNull(),
        estado = form.estado.trimOrNull(),
        dataAbertura = form.dataAbertura.trimOrNull(),
        cnae = form.cnae.trimOrNull(),
        situacao = form.situacao.trimOrNull(),
        porte = form.porte.trimOrNull(),
        socio = form.socio.trimOrNull(),
        capitalSocial = form.capitalSocial.trimOrNull(),
        tipo = form.tipo.trimOrNull(),
    )
    val scoreResult = leadScoringService.evaluate(row, telefoneNz)
    val newObs = form.observacoes.trimOrNull()

    val dataChanged =
        !strEq(existing.razaoSocial, razao) ||
            !strEq(existing.nomeFantasia, row.nomeFantasia) ||
            !strEq(existing.telefoneOriginal, telefoneOrig) ||
            !strEq(existing.email, row.email) ||
            !strEq(existing.endereco, row.endereco) ||
            !strEq(existing.cidade, row.cidade) ||
            !strEq(existing.estado, row.estado) ||
            !strEq(existing.dataAbertura, row.dataAbertura) ||
            !strEq(existing.cnae, row.cnae) ||
            !strEq(existing.situacao, row.situacao) ||
            !strEq(existing.porte, row.porte) ||
            !strEq(existing.socio, row.socio) ||
            !strEq(existing.capitalSocial, row.capitalSocial) ||
            !strEq(existing.tipo, row.tipo)
    val obsChanged = !strEq(existing.observacoes, newObs)

    val ok = leadRepository.updateManualEdit(
        id = id,
        razaoSocial = razao,
        nomeFantasia = row.nomeFantasia,
        telefoneOriginal = telefoneOrig,
        telefoneNormalizado = telefoneNz,
        email = row.email,
        endereco = row.endereco,
        cidade = row.cidade,
        estado = row.estado,
        dataAbertura = row.dataAbertura,
        cnae = row.cnae,
        situacao = row.situacao,
        porte = row.porte,
        socio = row.socio,
        capitalSocial = row.capitalSocial,
        tipo = row.tipo,
        observacoes = newObs,
        score = scoreResult.score,
        prioridade = scoreResult.prioridade,
    )
    if (ok) {
        if (dataChanged) {
            leadInteractionRepository.insert(
                leadId = id,
                interactionType = LeadInteractionTypes.EDIT_LEAD,
                result = "Dados atualizados",
                note = null,
            )
        }
        if (obsChanged) {
            leadInteractionRepository.insert(
                leadId = id,
                interactionType = LeadInteractionTypes.OBSERVATION,
                result = "Observações alteradas",
                note = newObs,
            )
        }
    }
    respondRedirect("/lead/$id", permanent = false)
}

private suspend fun ApplicationCall.respondLeadStatusPost() {
    val id = parameters["id"]?.toLongOrNull()
    if (id == null) {
        respondText("Identificador inválido.", status = HttpStatusCode.BadRequest)
        return
    }
    if (leadRepository.findById(id) == null) {
        respondText("Lead não encontrado.", status = HttpStatusCode.NotFound)
        return
    }
    val p = receiveParameters()
    val rawStatus = p["status"]?.trim().orEmpty()
    val note = p["note"]?.trimOrNull()
    val returnTo = p["returnTo"]?.trim()
    val newStatus = LeadStatus.entries.find { it.name == rawStatus }
    if (newStatus == null) {
        respondRedirect(redirectAfterStatusChange(returnTo, id, error = true), permanent = false)
        return
    }
    leadRepository.updateLeadStatus(id, newStatus)
    leadInteractionRepository.insert(
        leadId = id,
        interactionType = LeadInteractionTypes.STATUS_CHANGE,
        result = newStatus.name,
        note = note,
    )
    respondRedirect(redirectAfterStatusChange(returnTo, id, error = false), permanent = false)
}

fun Route.leadRoutes() {
    get("/leads") {
        val q = call.request.queryParameters["q"]
        val status = call.request.queryParameters["status"]
        val prioridade = call.request.queryParameters["prioridade"]
        val leads = leadRepository.listLeads(
            LeadListFilters(
                q = q,
                status = status,
                prioridade = prioridade,
            ),
        )
        call.respond(
            ThymeleafContent(
                "leads",
                mapOf(
                    "title" to "Leads",
                    "leads" to leads,
                    "q" to (q ?: ""),
                    "statusFilter" to (status ?: ""),
                    "prioridadeFilter" to (prioridade ?: ""),
                    "statuses" to LeadStatus.entries.map { it.name },
                    "prioridades" to LeadPriority.entries.map { it.name },
                ),
            ),
        )
    }
    post("/leads/{id}/edit") {
        call.respondLeadDetailPost()
    }
    post("/lead/{id}/edit") {
        call.respondLeadDetailPost()
    }
    post("/leads/{id}/status") {
        call.respondLeadStatusPost()
    }
    post("/lead/{id}/status") {
        call.respondLeadStatusPost()
    }
    get("/leads/{id}") {
        call.respondLeadDetailGet()
    }
    get("/lead/{id}") {
        call.respondLeadDetailGet()
    }
}
