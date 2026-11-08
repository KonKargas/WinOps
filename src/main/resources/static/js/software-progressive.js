/* Software page progressive loading. Existing WinGet operation handlers remain in software.js. */
(function () {
    let currentRequest = null;

    async function loadSoftwareData() {
        const container = document.getElementById("software-progressive-content");
        if (!container) return;

        if (currentRequest) currentRequest.abort();
        const controller = new AbortController();
        currentRequest = controller;
        container.setAttribute("aria-busy", "true");

        try {
            const response = await fetch("/software/data", {
                headers: { "Accept": "text/html" },
                cache: "no-store",
                signal: controller.signal
            });
            if (!response.ok) throw new Error("HTTP " + response.status);
            const html = await response.text();
            if (controller.signal.aborted) return;
            container.innerHTML = html;
            container.setAttribute("aria-busy", "false");
            if (typeof restoreActiveSoftwareOperations === "function") {
                await restoreActiveSoftwareOperations();
            }
        } catch (error) {
            if (controller.signal.aborted) return;
            container.setAttribute("aria-busy", "false");
            container.innerHTML = '<div class="software-progressive-loading software-progressive-error">' +
                '<div><strong>Unable to load software information.</strong>' +
                '<p>Check the WinGet service and try again.</p>' +
                '<button type="button" class="button-primary" id="software-progressive-retry">Retry</button></div></div>';
            document.getElementById("software-progressive-retry")
                .addEventListener("click", loadSoftwareData);
            console.error("Software loading failed:", error);
        } finally {
            if (currentRequest === controller) currentRequest = null;
        }
    }

    document.addEventListener("DOMContentLoaded", loadSoftwareData);
    window.refreshSoftwareData = loadSoftwareData;
})();
