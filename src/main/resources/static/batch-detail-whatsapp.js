(function () {
    var batchId = window.__GEAR_BATCH_ID__;
    if (batchId == null || batchId === "") {
        return;
    }

    function $(id) {
        return document.getElementById(id);
    }

    var toolbar = document.querySelector(".wa-toolbar");
    if (!toolbar) {
        return;
    }

    function setToolbarBusy(busy) {
        toolbar.querySelectorAll("button").forEach(function (btn) {
            btn.disabled = busy;
            btn.setAttribute("aria-busy", busy ? "true" : "false");
        });
    }

    function showMsg(text, isError) {
        var el = $("wa-action-message");
        if (!el) {
            return;
        }
        el.textContent = text || "";
        el.className = "wa-action-message" + (isError ? " wa-action-message--error" : "");
    }

    function reloadSoon() {
        window.setTimeout(function () {
            window.location.reload();
        }, 650);
    }

    async function postJson(path) {
        var r = await fetch(path, {
            method: "POST",
            headers: { Accept: "application/json", "Content-Type": "application/json" },
            body: "{}",
        });
        var txt = await r.text();
        var data = null;
        try {
            data = JSON.parse(txt);
        } catch (ignore) {}
        if (!r.ok) {
            var err = data && data.error ? data.error : "Erro HTTP " + r.status;
            throw new Error(err);
        }
        return data;
    }

    $("wa-prepare-send") &&
        $("wa-prepare-send").addEventListener("click", async function () {
            showMsg("");
            setToolbarBusy(true);
            try {
                var d = await postJson("/batches/" + batchId + "/prepare-send");
                var msg =
                    "Preparar envio concluído: " +
                    (d.campaignsCreated != null ? d.campaignsCreated : 0) +
                    " campanha(s) criada(s), " +
                    (d.skipped != null ? d.skipped : 0) +
                    " ignorado(s). Atualizando…";
                showMsg(msg, false);
                reloadSoon();
            } catch (e) {
                showMsg(e.message || String(e), true);
                setToolbarBusy(false);
            }
        });

    $("wa-send-batch") &&
        $("wa-send-batch").addEventListener("click", async function () {
            showMsg("");
            setToolbarBusy(true);
            try {
                var d = await postJson("/batches/" + batchId + "/send");
                var msg =
                    d.summaryMessage ||
                    "Fila atualizada: " +
                        (d.newlyScheduledCount != null ? d.newlyScheduledCount : 0) +
                        " nova(s) na fila. Quota hoje: " +
                        (d.remainingQuotaToday != null ? d.remainingQuotaToday : "?") +
                        ".";
                showMsg(msg, false);
                reloadSoon();
            } catch (e) {
                showMsg(e.message || String(e), true);
                setToolbarBusy(false);
            }
        });

    $("wa-refresh") &&
        $("wa-refresh").addEventListener("click", function () {
            showMsg("Atualizando…", false);
            setToolbarBusy(true);
            window.location.reload();
        });
})();
