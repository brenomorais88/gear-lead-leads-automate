(function () {
    var flash = document.getElementById("wa-cfg-flash");
    var serviceLine = document.getElementById("wa-cfg-service-line");
    var form = document.getElementById("wa-cfg-form");
    var saveBtn = document.getElementById("wa-cfg-save");
    var pauseBtn = document.getElementById("wa-cfg-pause");
    var resumeBtn = document.getElementById("wa-cfg-resume");
    var resetModal = document.getElementById("wa-cfg-reset-modal");
    var resetOpenBtn = document.getElementById("wa-cfg-open-reset");
    var resetCancelBtn = document.getElementById("wa-cfg-reset-cancel");
    var resetConfirmBtn = document.getElementById("wa-cfg-reset-confirm");
    var resetPhrase = document.getElementById("wa-cfg-reset-phrase");
    var resetAck = document.getElementById("wa-cfg-reset-ack");

    var RESET_PHRASE = "APAGAR TODOS OS DADOS";

    function showFlash(kind, text) {
        if (!flash) return;
        flash.hidden = false;
        flash.textContent = text;
        flash.className =
            kind === "err" ? "flash-msg flash-msg--error" : "flash-msg flash-msg--success";
        flash.scrollIntoView({ behavior: "smooth", block: "nearest" });
    }

    function hhMmOk(s) {
        return /^([01]\d|2[0-3]):[0-5]\d$/.test(String(s || "").trim());
    }

    function validateClient(body) {
        var errs = [];
        if (!body.phoneNumberId || !String(body.phoneNumberId).trim()) errs.push("Phone Number ID obrigatório.");
        if (!body.defaultTemplateName || !String(body.defaultTemplateName).trim()) errs.push("Template obrigatório.");
        if (!body.defaultTemplateLanguage || !String(body.defaultTemplateLanguage).trim()) errs.push("Idioma obrigatório.");
        if (!(body.dailySendLimit > 0)) errs.push("Limite diário deve ser maior que zero.");
        if (body.sendDelayMinMinutes < 0) errs.push("Delay mínimo não pode ser negativo.");
        if (body.sendDelayMaxMinutes < body.sendDelayMinMinutes) {
            errs.push("Delay máximo deve ser ≥ delay mínimo.");
        }
        if (!(body.batchSize >= 1)) errs.push("Tamanho do lote deve ser pelo menos 1.");
        if (!hhMmOk(body.executionStartTime)) errs.push("Horário de início inválido (use HH:mm).");
        if (!hhMmOk(body.executionEndTime)) errs.push("Horário de fim inválido (use HH:mm).");
        return errs;
    }

    function applyToForm(d) {
        document.getElementById("wa-cfg-phone").value = d.phoneNumberId || "";
        document.getElementById("wa-cfg-tpl").value = d.defaultTemplateName || "";
        document.getElementById("wa-cfg-lang").value = d.defaultTemplateLanguage || "";
        document.getElementById("wa-cfg-limit").value = d.dailySendLimit;
        document.getElementById("wa-cfg-dmin").value = d.sendDelayMinMinutes;
        document.getElementById("wa-cfg-dmax").value = d.sendDelayMaxMinutes;
        document.getElementById("wa-cfg-batch").value = d.batchSize != null ? d.batchSize : 20;
        document.getElementById("wa-cfg-exec-start").value = d.executionStartTime || "00:00";
        document.getElementById("wa-cfg-exec-end").value = d.executionEndTime || "23:59";
        document.getElementById("wa-cfg-inbound-recipients").value = d.inboundNotifyRecipients || "";
        document.getElementById("wa-cfg-inbound-template").value = d.inboundNotifyTemplateName || "";
        document.getElementById("wa-cfg-inbound-language").value = d.inboundNotifyTemplateLanguage || "pt_BR";
        document.getElementById("wa-cfg-inbound-body-template").value = d.inboundNotifyBodyTemplate || "";
        if (serviceLine) {
            serviceLine.textContent = d.servicePaused
                ? "Serviço pausado manualmente."
                : "Serviço ativo.";
        }
        if (pauseBtn && resumeBtn) {
            pauseBtn.hidden = !!d.servicePaused;
            resumeBtn.hidden = !d.servicePaused;
        }
    }

    function closeResetModal() {
        if (resetModal) resetModal.hidden = true;
        if (resetPhrase) resetPhrase.value = "";
        if (resetAck) resetAck.checked = false;
    }

    async function load() {
        try {
            var r = await fetch("/whatsapp/settings", { headers: { Accept: "application/json" } });
            var d = await r.json();
            if (!r.ok) throw new Error(d && d.errors ? d.errors.join("; ") : r.statusText);
            applyToForm(d);
        } catch (e) {
            showFlash("err", "Erro ao carregar configurações: " + (e.message || e));
        }
    }

    if (form) {
        form.addEventListener("submit", async function (ev) {
            ev.preventDefault();
            var body = {
                phoneNumberId: document.getElementById("wa-cfg-phone").value.trim(),
                defaultTemplateName: document.getElementById("wa-cfg-tpl").value.trim(),
                defaultTemplateLanguage: document.getElementById("wa-cfg-lang").value.trim(),
                dailySendLimit: parseInt(document.getElementById("wa-cfg-limit").value, 10),
                sendDelayMinMinutes: parseInt(document.getElementById("wa-cfg-dmin").value, 10),
                sendDelayMaxMinutes: parseInt(document.getElementById("wa-cfg-dmax").value, 10),
                batchSize: parseInt(document.getElementById("wa-cfg-batch").value, 10),
                executionStartTime: document.getElementById("wa-cfg-exec-start").value.trim(),
                executionEndTime: document.getElementById("wa-cfg-exec-end").value.trim(),
                inboundNotifyRecipients: document.getElementById("wa-cfg-inbound-recipients").value.trim(),
                inboundNotifyTemplateName: document.getElementById("wa-cfg-inbound-template").value.trim(),
                inboundNotifyTemplateLanguage: document.getElementById("wa-cfg-inbound-language").value.trim(),
                inboundNotifyBodyTemplate: document.getElementById("wa-cfg-inbound-body-template").value.trim(),
            };
            var ve = validateClient(body);
            if (ve.length) {
                showFlash("err", ve.join(" "));
                return;
            }
            try {
                if (saveBtn) {
                    saveBtn.disabled = true;
                    saveBtn.textContent = "Salvando...";
                }
                var r = await fetch("/whatsapp/settings", {
                    method: "PUT",
                    headers: { "Content-Type": "application/json", Accept: "application/json" },
                    body: JSON.stringify(body),
                });
                var txt = await r.text();
                var d = JSON.parse(txt);
                if (!r.ok) {
                    var msg = (d.errors && d.errors.join) ? d.errors.join(" ") : txt;
                    throw new Error(msg);
                }
                applyToForm(d);
                showFlash("ok", "Configuração salva com sucesso.");
            } catch (e) {
                showFlash("err", "Não foi possível salvar: " + (e.message || e));
            } finally {
                if (saveBtn) {
                    saveBtn.disabled = false;
                    saveBtn.textContent = "Salvar";
                }
            }
        });
    }

    async function postPauseResume(path) {
        try {
            var r = await fetch(path, { method: "POST", headers: { Accept: "application/json" } });
            var d = await r.json();
            if (!r.ok) throw new Error(d && d.errors ? d.errors.join("; ") : r.statusText);
            applyToForm(d);
            showFlash("ok", d.servicePaused ? "Serviço pausado." : "Serviço retomado.");
        } catch (e) {
            showFlash("err", e.message || String(e));
        }
    }

    if (pauseBtn) pauseBtn.addEventListener("click", function () { postPauseResume("/whatsapp/settings/pause"); });
    if (resumeBtn) resumeBtn.addEventListener("click", function () { postPauseResume("/whatsapp/settings/resume"); });

    if (resetOpenBtn && resetModal) {
        resetOpenBtn.addEventListener("click", function () {
            resetModal.hidden = false;
            if (resetPhrase) resetPhrase.focus();
        });
    }
    if (resetCancelBtn) resetCancelBtn.addEventListener("click", closeResetModal);
    if (resetModal) {
        resetModal.addEventListener("click", function (ev) {
            if (ev.target === resetModal) closeResetModal();
        });
    }

    if (resetConfirmBtn) {
        resetConfirmBtn.addEventListener("click", async function () {
            if (!resetAck || !resetAck.checked) {
                showFlash("err", "Marque a confirmação de que entende que a exclusão é irreversível.");
                return;
            }
            var phrase = resetPhrase ? resetPhrase.value.trim() : "";
            if (phrase !== RESET_PHRASE) {
                showFlash("err", "Texto de confirmação incorreto. Use exatamente: " + RESET_PHRASE);
                return;
            }
            if (!window.confirm("Confirma apagar TODOS os dados operacionais agora? Esta ação é irreversível.")) {
                return;
            }
            try {
                var r = await fetch("/whatsapp/settings/reset-operational-data", {
                    method: "POST",
                    headers: { "Content-Type": "application/json", Accept: "application/json" },
                    body: JSON.stringify({ confirmPhrase: phrase }),
                });
                var txt = await r.text();
                var d = JSON.parse(txt);
                if (!r.ok) {
                    var msg = (d.errors && d.errors.join) ? d.errors.join(" ") : txt;
                    throw new Error(msg);
                }
                closeResetModal();
                applyToForm(d);
                showFlash("ok", "Dados operacionais apagados. Configurações mantidas.");
            } catch (e) {
                showFlash("err", "Não foi possível apagar: " + (e.message || e));
            }
        });
    }

    load();
})();
