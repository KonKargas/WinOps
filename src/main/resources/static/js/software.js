function filterApplications() {
    const input = document.getElementById("softwareSearch");
    const table = document.getElementById("installedApplicationsTable");
    const empty = document.getElementById("softwareSearchEmpty");
    if (!input || !table) return;

    const filter = input.value.toLowerCase().trim();
    const rows = table.querySelectorAll("tbody tr");
    let visibleRows = 0;

    rows.forEach(row => {
        const visible = row.textContent.toLowerCase().includes(filter);
        row.style.display = visible ? "" : "none";
        if (visible) visibleRows++;
    });

    if (empty) empty.style.display = visibleRows === 0 ? "block" : "none";
}

async function readJsonResponse(response, fallbackMessage) {
    if (!response.ok) {
        throw new Error(fallbackMessage);
    }

    const text = await response.text();

    if (!text || !text.trim()) {
        return null;
    }

    try {
        return JSON.parse(text);
    } catch (error) {
        throw new Error("The server returned an invalid response.");
    }
}

function findInstalledRow(packageId) {
    return Array.from(document.querySelectorAll(".software-installed-row"))
            .find(row => row.dataset.packageId === packageId);
}

function findCatalogRow(packageId) {
    return Array.from(document.querySelectorAll(".software-catalog-row"))
            .find(row => row.dataset.packageId === packageId);
}

async function restoreActiveSoftwareOperations() {
    try {
        const response = await fetch("/software/operations/active", {cache: "no-store"});
        const operations = await readJsonResponse(response, "Could not restore software operations.");
        if (!operations) return;

        (operations.installations || []).forEach(operation => {
            const row = findCatalogRow(operation.packageId);
            if (!row) return;

            const button = row.querySelector(".software-install-button");
            if (!button) return;

            button.disabled = true;
            button.style.display = "none";
            setProgressState(row, operation.status, operation.message);
            pollInstallation(row, operation.packageId, button);
        });

        (operations.uninstallations || []).forEach(operation => {
            const row = findInstalledRow(operation.packageId);
            if (!row) return;

            const button = row.querySelector(".software-uninstall-button");
            if (!button) return;

            button.disabled = true;
            button.style.display = "none";
            setUninstallProgressState(row, operation.status, operation.message);
            pollUninstallation(row, operation.packageId, button);
        });

        (operations.updates || []).forEach(operation => {
            if (operation.updateAll) {
                const button = document.querySelector(".software-update-all-button");
                const action = document.querySelector(".software-update-all-action");
                if (!button || !action) return;

                button.disabled = true;
                button.style.display = "none";
                const progress = action.querySelector(".software-update-all-progress");
                const message = action.querySelector(".software-update-all-message");
                if (progress) progress.classList.add("active");
                if (message) message.textContent = operation.message || "Updating applications...";
                pollSoftwareUpdateAll(action, button);
                return;
            }

            const row = findInstalledRow(operation.packageId);
            if (!row) return;

            const button = row.querySelector(".software-update-button");
            if (!button) return;

            button.disabled = true;
            button.style.display = "none";
            setUpdateProgressState(row, operation.status, operation.message);
            pollSoftwareUpdate(row, operation.packageId, button);
        });

    } catch (error) {
        console.warn("Could not restore active software operations:", error);
    }
}

document.addEventListener("DOMContentLoaded", restoreActiveSoftwareOperations);

async function startSoftwareInstallation(button) {
    const packageId = button.dataset.packageId;
    const packageName = button.dataset.packageName;

    if (!confirm("Install " + packageName + " through Windows Package Manager?")) return;

    const row = button.closest(".software-catalog-row");
    const progress = row.querySelector(".software-install-progress");
    const result = row.querySelector(".software-install-result");

    button.disabled = true;
    button.style.display = "none";
    result.className = "software-install-result";
    result.textContent = "";
    progress.classList.add("active");
    setProgressState(row, "PREPARING", "Preparing installation...");

    try {
        const body = new URLSearchParams();
        body.append("packageId", packageId);

        const response = await fetch("/software/install/start", {
            method: "POST",
            headers: {"Content-Type": "application/x-www-form-urlencoded"},
            body: body.toString()
        });

        const installation = await readJsonResponse(
                response,
                "Could not start installation."
        );
        if (!installation) throw new Error("Package was rejected.");

        pollInstallation(row, packageId, button);

    } catch (error) {
        showInstallationFailure(row, button, error.message);
    }
}

async function pollInstallation(row, packageId, button) {
    try {
        const response = await fetch(
            "/software/install/status?packageId=" + encodeURIComponent(packageId),
            {cache: "no-store"}
        );

        const installation = await readJsonResponse(
                response,
                "Could not read installation status."
        );
        if (!installation) throw new Error("Installation status is unavailable.");

        setProgressState(row, installation.status, installation.message);

        if (installation.status === "COMPLETED") {
            showInstallationSuccess(row);
            return;
        }

        if (installation.status === "FAILED") {
            showInstallationFailure(row, button, installation.message);
            return;
        }

        window.setTimeout(() => pollInstallation(row, packageId, button), 700);

    } catch (error) {
        showInstallationFailure(row, button, error.message);
    }
}

function setProgressState(row, status, message) {
    const progress = row.querySelector(".software-install-progress");
    const messageElement = row.querySelector(".software-progress-message");

    progress.classList.add("active");
    progress.classList.remove("downloading", "installing");

    if (status === "DOWNLOADING") progress.classList.add("downloading");
    if (status === "INSTALLING") progress.classList.add("installing");

    messageElement.textContent = message || "Working...";
}

function showInstallationSuccess(row) {
    const progress = row.querySelector(".software-install-progress");
    const result = row.querySelector(".software-install-result");

    progress.classList.remove("active");
    result.className = "software-install-result success";
    result.textContent = "✓ Installed";

    window.setTimeout(() => window.location.reload(), 900);
}

function showInstallationFailure(row, button, message) {
    const progress = row.querySelector(".software-install-progress");
    const result = row.querySelector(".software-install-result");

    progress.classList.remove("active");
    result.className = "software-install-result error";
    result.textContent = "Installation failed" + (message ? ": " + message : ".");

    button.disabled = false;
    button.style.display = "";
}


async function startSoftwareUpdate(button) {

    const packageId = button.dataset.packageId;
    const packageName = button.dataset.packageName;

    if (!confirm(
            "Update "
            + packageName
            + " through Windows Package Manager?"
    )) {
        return;
    }

    const row =
            button.closest(
                    ".software-installed-row"
            );

    const progress =
            row.querySelector(
                    ".software-update-progress"
            );

    const result =
            row.querySelector(
                    ".software-update-result"
            );

    button.disabled = true;
    button.style.display = "none";

    result.className =
            "software-update-result";

    result.textContent = "";

    progress.classList.add(
            "active"
    );

    setUpdateProgressState(
            row,
            "PREPARING",
            "Preparing update..."
    );

    try {

        const body =
                new URLSearchParams();

        body.append(
                "packageId",
                packageId
        );

        const response =
                await fetch(
                        "/software/update/start",
                        {
                            method: "POST",
                            headers: {
                                "Content-Type":
                                    "application/x-www-form-urlencoded"
                            },
                            body: body.toString()
                        }
                );

        const update =
                await readJsonResponse(
                        response,
                        "Could not start update."
                );

        if (!update) {
            throw new Error(
                    "Application could not be validated for update."
            );
        }

        pollSoftwareUpdate(
                row,
                packageId,
                button
        );

    } catch (error) {

        showSoftwareUpdateFailure(
                row,
                button,
                error.message
        );
    }
}


async function pollSoftwareUpdate(
        row,
        packageId,
        button) {

    try {

        const response =
                await fetch(
                        "/software/update/status?packageId="
                        + encodeURIComponent(packageId),
                        {
                            cache: "no-store"
                        }
                );

        const update =
                await readJsonResponse(
                        response,
                        "Could not read update status."
                );

        if (!update) {
            throw new Error(
                    "Update status is unavailable."
            );
        }

        setUpdateProgressState(
                row,
                update.status,
                update.message
        );

        if (update.status === "COMPLETED") {

            showSoftwareUpdateSuccess(
                    row
            );

            return;
        }

        if (update.status === "FAILED") {

            showSoftwareUpdateFailure(
                    row,
                    button,
                    update.message
            );

            return;
        }

        window.setTimeout(
                () => pollSoftwareUpdate(
                        row,
                        packageId,
                        button
                ),
                700
        );

    } catch (error) {

        showSoftwareUpdateFailure(
                row,
                button,
                error.message
        );
    }
}


function setUpdateProgressState(
        row,
        status,
        message) {

    const progress =
            row.querySelector(
                    ".software-update-progress"
            );

    const messageElement =
            row.querySelector(
                    ".software-update-message"
            );

    progress.classList.add(
            "active"
    );

    messageElement.textContent =
            message
            || "Updating application...";
}


function showSoftwareUpdateSuccess(row) {

    const progress =
            row.querySelector(
                    ".software-update-progress"
            );

    const result =
            row.querySelector(
                    ".software-update-result"
            );

    progress.classList.remove(
            "active"
    );

    result.className =
            "software-update-result success";

    result.textContent =
            "✓ Updated";

    window.setTimeout(
            () => window.location.reload(),
            900
    );
}


function showSoftwareUpdateFailure(
        row,
        button,
        message) {

    const progress =
            row.querySelector(
                    ".software-update-progress"
            );

    const result =
            row.querySelector(
                    ".software-update-result"
            );

    progress.classList.remove(
            "active"
    );

    result.className =
            "software-update-result error";

    result.textContent =
            "Update failed"
            + (message
                ? ": " + message
                : ".");

    button.disabled = false;
    button.style.display = "";
}


async function startSoftwareUpdateAll(button) {

    if (!confirm(
            "Install all available software updates through Windows Package Manager?"
    )) {
        return;
    }

    const action =
            button.closest(
                    ".software-update-all-action"
            );

    const progress =
            action.querySelector(
                    ".software-update-all-progress"
            );

    const result =
            action.querySelector(
                    ".software-update-all-result"
            );

    button.disabled = true;
    button.style.display = "none";

    result.className =
            "software-update-all-result";

    result.textContent = "";

    progress.classList.add(
            "active"
    );

    try {

        const response =
                await fetch(
                        "/software/update/all/start",
                        {
                            method: "POST"
                        }
                );

        const update =
                await readJsonResponse(
                        response,
                        "Could not start Update All."
                );

        if (!update) {
            throw new Error(
                    "No available updates could be started."
            );
        }

        pollSoftwareUpdateAll(
                action,
                button
        );

    } catch (error) {

        showSoftwareUpdateAllFailure(
                action,
                button,
                error.message
        );
    }
}


async function pollSoftwareUpdateAll(
        action,
        button) {

    try {

        const response =
                await fetch(
                        "/software/update/all/status",
                        {
                            cache: "no-store"
                        }
                );

        const update =
                await readJsonResponse(
                        response,
                        "Could not read Update All status."
                );

        if (!update) {
            throw new Error(
                    "Update All status is unavailable."
            );
        }

        const messageElement =
                action.querySelector(
                        ".software-update-all-message"
                );

        messageElement.textContent =
                update.message
                || "Updating applications...";

        if (update.status === "COMPLETED") {

            const progress =
                    action.querySelector(
                            ".software-update-all-progress"
                    );

            const result =
                    action.querySelector(
                            ".software-update-all-result"
                    );

            progress.classList.remove(
                    "active"
            );

            result.className =
                    "software-update-all-result success";

            result.textContent =
                    "✓ Updates completed";

            window.setTimeout(
                    () => window.location.reload(),
                    1000
            );

            return;
        }

        if (update.status === "FAILED") {

            showSoftwareUpdateAllFailure(
                    action,
                    button,
                    update.message
            );

            return;
        }

        window.setTimeout(
                () => pollSoftwareUpdateAll(
                        action,
                        button
                ),
                700
        );

    } catch (error) {

        showSoftwareUpdateAllFailure(
                action,
                button,
                error.message
        );
    }
}


function showSoftwareUpdateAllFailure(
        action,
        button,
        message) {

    const progress =
            action.querySelector(
                    ".software-update-all-progress"
            );

    const result =
            action.querySelector(
                    ".software-update-all-result"
            );

    progress.classList.remove(
            "active"
    );

    result.className =
            "software-update-all-result error";

    result.textContent =
            "Update All failed"
            + (message
                ? ": " + message
                : ".");

    button.disabled = false;
    button.style.display = "";
}


async function startSoftwareUninstallation(button) {

    const packageId =
            button.dataset.packageId;

    const packageName =
            button.dataset.packageName;

    if (!confirm(
            "Uninstall "
            + packageName
            + " from this computer?"
    )) {
        return;
    }

    const row =
            button.closest(
                    ".software-installed-row"
            );

    const progress =
            row.querySelector(
                    ".software-uninstall-progress"
            );

    const result =
            row.querySelector(
                    ".software-uninstall-result"
            );

    button.disabled = true;
    button.style.display = "none";

    result.className =
            "software-uninstall-result";

    result.textContent = "";

    progress.classList.add(
            "active"
    );

    setUninstallProgressState(
            row,
            "PREPARING",
            "Preparing removal..."
    );

    try {

        const body =
                new URLSearchParams();

        body.append(
                "packageId",
                packageId
        );

        const response =
                await fetch(
                        "/software/uninstall/start",
                        {
                            method: "POST",
                            headers: {
                                "Content-Type":
                                    "application/x-www-form-urlencoded"
                            },
                            body: body.toString()
                        }
                );

        const uninstallation =
                await readJsonResponse(
                        response,
                        "Could not start uninstallation."
                );

        if (!uninstallation) {
            throw new Error(
                    "Application could not be validated."
            );
        }

        pollUninstallation(
                row,
                packageId,
                button
        );

    } catch (error) {

        showUninstallationFailure(
                row,
                button,
                error.message
        );
    }
}


async function pollUninstallation(
        row,
        packageId,
        button) {

    try {

        const response =
                await fetch(
                        "/software/uninstall/status?packageId="
                        + encodeURIComponent(packageId),
                        {
                            cache: "no-store"
                        }
                );

        const uninstallation =
                await readJsonResponse(
                        response,
                        "Could not read uninstallation status."
                );

        if (!uninstallation) {
            throw new Error(
                    "Uninstallation status is unavailable."
            );
        }

        setUninstallProgressState(
                row,
                uninstallation.status,
                uninstallation.message
        );

        if (uninstallation.status === "COMPLETED") {

            showUninstallationSuccess(
                    row
            );

            return;
        }

        if (uninstallation.status === "FAILED") {

            showUninstallationFailure(
                    row,
                    button,
                    uninstallation.message
            );

            return;
        }

        window.setTimeout(
                () => pollUninstallation(
                        row,
                        packageId,
                        button
                ),
                700
        );

    } catch (error) {

        showUninstallationFailure(
                row,
                button,
                error.message
        );
    }
}


function setUninstallProgressState(
        row,
        status,
        message) {

    const progress =
            row.querySelector(
                    ".software-uninstall-progress"
            );

    const messageElement =
            row.querySelector(
                    ".software-uninstall-message"
            );

    progress.classList.add(
            "active"
    );

    messageElement.textContent =
            message
            || "Removing application...";
}


function showUninstallationSuccess(row) {

    const progress =
            row.querySelector(
                    ".software-uninstall-progress"
            );

    const result =
            row.querySelector(
                    ".software-uninstall-result"
            );

    progress.classList.remove(
            "active"
    );

    result.className =
            "software-uninstall-result success";

    result.textContent =
            "✓ Removed";

    row.classList.add(
            "software-row-removed"
    );

    window.setTimeout(
            () => window.location.reload(),
            900
    );
}


function showUninstallationFailure(
        row,
        button,
        message) {

    const progress =
            row.querySelector(
                    ".software-uninstall-progress"
            );

    const result =
            row.querySelector(
                    ".software-uninstall-result"
            );

    progress.classList.remove(
            "active"
    );

    result.className =
            "software-uninstall-result error";

    result.textContent =
            "Removal failed"
            + (message
                ? ": " + message
                : ".");

    button.disabled = false;
    button.style.display = "";
}

async function refreshWingetSources(button) {

    const action =
            button.closest(".software-tool-action");

    const progress =
            action.querySelector(
                    ".software-tool-progress"
            );

    const result =
            action.querySelector(
                    ".software-tool-result"
            );


    button.disabled = true;
    button.style.display = "none";

    result.className =
            "software-tool-result";

    result.textContent = "";

    progress.classList.add("active");


    try {

        const response =
                await fetch(
                        "/software/tools/refresh-sources",
                        {
                            method: "POST"
                        }
                );


        const data =
                await readJsonResponse(
                        response,
                        "Could not refresh WinGet sources."
                );


        progress.classList.remove("active");


        if (data && data.success) {

            result.className =
                    "software-tool-result success";

            result.textContent =
                    "✓ Sources refreshed";

            window.setTimeout(
                    () => window.location.reload(),
                    900
            );

            return;
        }


        throw new Error(
                data && data.message
                        ? data.message
                        : "Source refresh failed."
        );


    } catch (error) {

        progress.classList.remove("active");

        result.className =
                "software-tool-result error";

        result.textContent =
                error.message
                || "Source refresh failed.";

        button.disabled = false;
        button.style.display = "";
    }
}

async function openInstalledApps(button) {

    const action =
            button.closest(".software-tool-action");

    const result =
            action.querySelector(
                    ".software-tool-result"
            );


    button.disabled = true;

    result.className =
            "software-tool-result";

    result.textContent = "";


    try {

        const response =
                await fetch(
                        "/software/tools/open-installed-apps",
                        {
                            method: "POST"
                        }
                );


        const data =
                await readJsonResponse(
                        response,
                        "Could not open Windows Installed Apps."
                );


        if (!data || !data.success) {

            throw new Error(
                    data && data.message
                            ? data.message
                            : "Could not open Windows Installed Apps."
            );
        }


        result.className =
                "software-tool-result success";

        result.textContent =
                "✓ Opened";


        window.setTimeout(
                () => {
                    result.textContent = "";
                    result.className =
                            "software-tool-result";
                },
                2500
        );


    } catch (error) {

        result.className =
                "software-tool-result error";

        result.textContent =
                error.message
                || "Could not open Installed Apps.";

    } finally {

        button.disabled = false;
    }
}

async function runWingetDiagnostics(button) {

    const action =
            button.closest(".software-tool-action");

    const progress =
            action.querySelector(
                    ".software-tool-progress"
            );

    const result =
            action.querySelector(
                    ".software-tool-result"
            );


    button.disabled = true;
    button.style.display = "none";

    result.className =
            "software-tool-result";

    result.textContent = "";

    progress.classList.add("active");


    try {

        const response =
                await fetch(
                        "/software/tools/diagnostics",
                        {
                            method: "GET",
                            cache: "no-store"
                        }
                );


        const data =
                await readJsonResponse(
                        response,
                        "Could not run WinGet diagnostics."
                );


        progress.classList.remove("active");


        if (!data) {

            throw new Error(
                    "Diagnostic information is unavailable."
            );
        }


        if (data.healthy) {

            result.className =
                    "software-tool-result success";

            result.innerHTML =
                    "✓ WinGet healthy"
                    + "<small>"
                    + escapeSoftwareText(data.version)
                    + " · "
                    + data.sourceCount
                    + (data.sourceCount === 1
                        ? " source"
                        : " sources")
                    + "</small>";

        } else {

            result.className =
                    "software-tool-result error";

            result.innerHTML =
                    "Diagnostic warning"
                    + "<small>"
                    + escapeSoftwareText(
                            data.message
                            || "WinGet requires attention."
                    )
                    + "</small>";
        }


    } catch (error) {

        progress.classList.remove("active");

        result.className =
                "software-tool-result error";

        result.textContent =
                error.message
                || "Diagnostics failed.";

    } finally {

        button.disabled = false;
        button.style.display = "";
    }
}


function escapeSoftwareText(value) {

    const element =
            document.createElement("div");

    element.textContent =
            value == null
                    ? ""
                    : String(value);

    return element.innerHTML;
}
