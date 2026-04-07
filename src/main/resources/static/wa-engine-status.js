(function () {
    var root = document.getElementById("wa-global-engine");
    if (!root) {
        return;
    }

    var badgeEl = document.getElementById("wa-global-engine-badge");
    var msgEl = document.getElementById("wa-global-engine-msg");
    var metaEl = document.getElementById("wa-global-engine-meta");
    var pauseBtn = document.getElementById("wa-global-pause-btn");
    var resumeBtn = document.getElementById("wa-global-resume-btn");

    var POLL_MS = 45000;

    function humanLabel(status) {
        var map = {
            PAUSED: "Pausado",
            IDLE: "Ocioso",
            WAITING_NEXT_SEND: "Aguardando próximo envio",
            READY_TO_SEND: "Pronto para enviar",
            PROCESSING: "Processando",
            DAILY_LIMIT_REACHED: "Limite diário",
            MISCONFIGURED: "Configuração",
            PENDING_WITHOUT_SCHEDULE: "Fila sem agendamento",
            ERROR: "Erro do motor",
        };
        return map[status] || status || "—";
    }

    function badgeClass(status) {
        var base = "wa-global-engine__badge";
        if (!status) {
            return base;
        }
        return base + " wa-global-engine__badge--" + String(status).toLowerCase();
    }

    function formatMeta(d) {
        var head = [];
        head.push(d.servicePaused ? "Serviço pausado" : "Serviço ativo");
        if (d.phoneNumberIdMasked) {
            head.push("Phone ID " + d.phoneNumberIdMasked);
        }
        if (d.defaultTemplateName) {
            head.push(
                "Tpl " +
                    d.defaultTemplateName +
                    (d.defaultTemplateLanguage ? " (" + d.defaultTemplateLanguage + ")" : ""),
            );
        }
        var parts = [];
        if (d.currentStatus === "PROCESSING") {
            if (d.currentProcessingCampaignId != null) {
                parts.push("Campanha #" + d.currentProcessingCampaignId);
                parts.push("Lead #" + (d.currentProcessingLeadId != null ? d.currentProcessingLeadId : "—"));
                if (d.currentProcessingBatchId != null) {
                    parts.push("Lote #" + d.currentProcessingBatchId);
                }
            }
        } else {
            if (d.nextCampaignId != null) {
                parts.push("Campanha #" + d.nextCampaignId);
            }
            if (d.nextLeadId != null) {
                parts.push("Lead #" + d.nextLeadId);
            }
            if (d.nextBatchId != null) {
                parts.push("Lote #" + d.nextBatchId);
            }
        }
        if (d.waitingDelayMinutesRemaining != null && d.waitingDelayMinutesRemaining > 0) {
            parts.push("≈ " + d.waitingDelayMinutesRemaining + " min");
        }
        if (d.remainingQuotaToday != null && d.dailyLimit != null) {
            parts.push("Quota " + d.remainingQuotaToday + "/" + d.dailyLimit);
        }
        if (d.sendDelayMinMinutes != null && d.sendDelayMaxMinutes != null) {
            parts.push("Delay " + d.sendDelayMinMinutes + "–" + d.sendDelayMaxMinutes + " min");
        }
        return head.concat(parts).join(" · ");
    }

    function updatePauseControls(d) {
        if (!pauseBtn || !resumeBtn) {
            return;
        }
        var paused = d.servicePaused === true;
        pauseBtn.hidden = paused;
        resumeBtn.hidden = !paused;
        pauseBtn.disabled = false;
        resumeBtn.disabled = false;
    }

    async function postAction(path) {
        var r = await fetch(path, { method: "POST", headers: { Accept: "application/json" } });
        if (!r.ok) {
            var t = await r.text();
            throw new Error(t || r.statusText);
        }
        await refresh();
    }

    if (pauseBtn) {
        pauseBtn.addEventListener("click", function () {
            pauseBtn.disabled = true;
            postAction("/whatsapp/settings/pause").catch(function (e) {
                if (msgEl) {
                    msgEl.textContent = "Não foi possível pausar: " + (e.message || e);
                }
                pauseBtn.disabled = false;
            });
        });
    }
    if (resumeBtn) {
        resumeBtn.addEventListener("click", function () {
            resumeBtn.disabled = true;
            postAction("/whatsapp/settings/resume").catch(function (e) {
                if (msgEl) {
                    msgEl.textContent = "Não foi possível retomar: " + (e.message || e);
                }
                resumeBtn.disabled = false;
            });
        });
    }

    async function refresh() {
        try {
            var r = await fetch("/whatsapp/engine-status", { headers: { Accept: "application/json" } });
            var txt = await r.text();
            var d = null;
            try {
                d = JSON.parse(txt);
            } catch (ignore) {}
            if (!r.ok || !d) {
                if (badgeEl) {
                    badgeEl.textContent = "Indisponível";
                    badgeEl.className = badgeClass("ERROR");
                }
                if (msgEl) {
                    msgEl.textContent = "Não foi possível carregar o status do motor.";
                }
                if (metaEl) {
                    metaEl.textContent = "";
                }
                return;
            }
            if (badgeEl) {
                badgeEl.textContent = humanLabel(d.currentStatus);
                badgeEl.className = badgeClass(d.currentStatus);
            }
            if (msgEl) {
                msgEl.textContent = d.statusMessage || "";
            }
            if (metaEl) {
                metaEl.textContent = formatMeta(d);
            }
            updatePauseControls(d);
        } catch (e) {
            if (badgeEl) {
                badgeEl.textContent = "Rede";
                badgeEl.className = badgeClass("ERROR");
            }
            if (msgEl) {
                msgEl.textContent = e.message || "Erro de rede.";
            }
        }
    }

    refresh();
    window.setInterval(refresh, POLL_MS);
})();
