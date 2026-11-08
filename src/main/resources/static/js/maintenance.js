/* =========================================================
   WINOPS MANAGER — MAINTENANCE
   ========================================================= */

(() => {
    "use strict";

    const HEALTH_STATUS_URL = "/maintenance/health/status";
    const HEALTH_START_URL = "/maintenance/health/start";

    async function requestJson(url, options = {}) {
        const response = await fetch(url, { cache: "no-store", ...options });
        const body = await response.text();
        if (!response.ok) throw new Error("Request failed (HTTP " + response.status + ").");
        if (!body.trim()) throw new Error("The server returned an empty response.");
        return JSON.parse(body);
    }

    function toast(message, isError = false) {
        const element = document.getElementById("maintenanceToast");
        element.textContent = message;
        element.classList.toggle("error", isError);
        element.hidden = false;
    }

    async function openTool(button, tool) {
        button.disabled = true;
        try {
            const data = await requestJson("/maintenance/tools/" + encodeURIComponent(tool), {
                method: "POST"
            });
            toast(data.message || (data.success ? "Tool launched." : "Could not open tool."), !data.success);
        } catch (error) {
            toast(error.message || "Could not open tool.", true);
        } finally {
            button.disabled = false;
        }
    }

    let pollHandle = null;
    const healthButton = () => document.getElementById("maintenanceHealthButton");
    const healthStatus = () => document.getElementById("maintenanceHealthStatus");

    function renderHealth(job) {
        const box = healthStatus();
        box.hidden = job.status === "IDLE";
        box.textContent = job.message || "Checking Windows component store health...";
        box.classList.toggle("failed", job.status === "FAILED");
        box.classList.toggle("completed", job.status === "COMPLETED");
        healthButton().disabled = !job.finished;
        healthButton().textContent = job.finished ? "Run" : "Running...";
    }

    async function pollHealth() {
        try {
            const job = await requestJson(HEALTH_STATUS_URL);
            renderHealth(job);
            if (!job.finished) {
                pollHandle = window.setTimeout(pollHealth, 1200);
            }
        } catch (error) {
            healthButton().disabled = false;
            healthButton().textContent = "Run";
            healthStatus().hidden = false;
            healthStatus().classList.add("failed");
            healthStatus().textContent = error.message || "Unable to check status.";
        }
    }

    async function startHealth() {
        healthButton().disabled = true;
        healthButton().textContent = "Running...";
        try {
            const job = await requestJson(HEALTH_START_URL, { method: "POST" });
            renderHealth(job);
            window.clearTimeout(pollHandle);
            if (!job.finished) pollHandle = window.setTimeout(pollHealth, 800);
        } catch (error) {
            renderHealth({ status: "FAILED", finished: true, message: error.message });
        }
    }

    document.addEventListener("DOMContentLoaded", () => {
        document.querySelectorAll(".maintenance-tool[data-tool]").forEach(row => {
            const button = row.querySelector(".maintenance-action");
            button.addEventListener("click", () => openTool(button, row.dataset.tool));
        });
        healthButton().addEventListener("click", startHealth);
        pollHealth(); // Restore a running job after navigation/reload.
    });
})();
