(function () {
    var flash = document.getElementById("wa-cfg-flash");
    var serviceLine = document.getElementById("wa-cfg-service-line");
    var form = document.getElementById("wa-cfg-form");
    var pauseBtn = document.getElementById("wa-cfg-pause");
    var resumeBtn = document.getElementById("wa-cfg-resume");

    function showFlash(kind, text) {
        if (!flash) return;
        flash.hidden = false;
        flash.textContent = text;
        flash.className =
            kind === "err" ? "flash-msg flash-msg--error" : "flash-msg flash-msg--success";
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
        return errs;
    }

    function applyToForm(d) {
        document.getElementById("wa-cfg-phone").value = d.phoneNumberId || "";
        document.getElementById("wa-cfg-tpl").value = d.defaultTemplateName || "";
        document.getElementById("wa-cfg-lang").value = d.defaultTemplateLanguage || "";
        document.getElementById("wa-cfg-limit").value = d.dailySendLimit;
        document.getElementById("wa-cfg-dmin").value = d.sendDelayMinMinutes;
        document.getElementById("wa-cfg-dmax").value = d.sendDelayMaxMinutes;
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
            };
            var ve = validateClient(body);
            if (ve.length) {
                showFlash("err", ve.join(" "));
                return;
            }
            try {
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

    load();
})();
