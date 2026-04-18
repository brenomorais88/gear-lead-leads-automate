(function () {
    var root = document.getElementById("dashboard-whatsapp-root");
    if (!root) {
        return;
    }

    var errEl = document.getElementById("wa-dash-error");
    var statusEl = document.getElementById("wa-dash-loading");

    var refreshBtn = document.getElementById("wa-dash-refresh");

    function setLoading(on) {
        if (statusEl) {
            statusEl.hidden = !on;
            statusEl.textContent = on ? "Carregando resumo…" : "";
        }
        if (errEl && on) {
            errEl.hidden = true;
            errEl.textContent = "";
        }
        if (refreshBtn) {
            refreshBtn.disabled = on;
        }
    }

    function showError(msg) {
        if (errEl) {
            errEl.hidden = false;
            errEl.textContent = msg;
        }
        if (statusEl) {
            statusEl.hidden = true;
        }
        if (refreshBtn) {
            refreshBtn.disabled = false;
        }
    }

    function setText(id, value) {
        var el = document.getElementById(id);
        if (el) {
            el.textContent = value != null ? String(value) : "—";
        }
    }

    async function loadSummary() {
        setLoading(true);
        try {
            var r = await fetch("/dashboard/whatsapp-summary", {
                headers: { Accept: "application/json" },
            });
            var txt = await r.text();
            var data = null;
            try {
                data = JSON.parse(txt);
            } catch (ignore) {}
            if (!r.ok) {
                var msg = data && data.error ? data.error : "Não foi possível carregar o resumo (" + r.status + ").";
                showError(msg);
                return;
            }
            if (!data) {
                showError("Resposta inválida do servidor.");
                return;
            }
            setText("wa-dailyLimit", data.dailyLimit);
            setText("wa-sentToday", data.sentToday);
            setText("wa-remainingQuotaToday", data.remainingQuotaToday);
            setText("wa-pendingCampaigns", data.pendingCampaigns);
            setText("wa-failedCampaigns", data.failedCampaigns);
            setText("wa-respondedCampaigns", data.respondedCampaigns);
            var tpl = data.activeTemplateName || "—";
            if (data.activeTemplateLanguage) {
                tpl += " (" + data.activeTemplateLanguage + ")";
            }
            setText("wa-activeTemplate", tpl);
            var engEl = document.getElementById("wa-dash-engine-inline");
            if (engEl && data.engineStatus) {
                var es = data.engineStatus;
                engEl.textContent =
                    "Estado do motor: " +
                    (es.currentStatus || "—") +
                    " — " +
                    (es.statusMessage || "");
                engEl.hidden = false;
            }
            setLoading(false);
        } catch (e) {
            showError(e.message || "Erro de rede ao carregar o resumo.");
        }
    }

    document.getElementById("wa-dash-refresh")?.addEventListener("click", function () {
        loadSummary();
    });

    loadSummary();
})();
