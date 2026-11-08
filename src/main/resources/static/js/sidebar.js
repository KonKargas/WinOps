document.addEventListener("DOMContentLoaded", () => {
    const sidebar = document.getElementById("winopsSidebar");
    const button = document.getElementById("sidebarCollapseButton");

    if (!sidebar || !button) {
        return;
    }

    const storageKey = "winops.sidebar.collapsed";
    const root = document.documentElement;

    function setCollapsed(collapsed) {
        root.classList.toggle("winops-sidebar-collapsed", collapsed);
        sidebar.classList.toggle("sidebar-collapsed", collapsed);
        button.setAttribute("aria-expanded", String(!collapsed));
        button.setAttribute("aria-label", collapsed ? "Expand sidebar" : "Collapse sidebar");
        button.title = collapsed ? "Expand sidebar" : "Collapse sidebar";
    }

    // The inline bootstrap in sidebar.html restores the layout before this
    // deferred script runs. Synchronize button accessibility without resetting it.
    setCollapsed(root.classList.contains("winops-sidebar-collapsed"));

    button.addEventListener("click", () => {
        const collapsed = !root.classList.contains("winops-sidebar-collapsed");
        setCollapsed(collapsed);

        try {
            localStorage.setItem(storageKey, String(collapsed));
        } catch (error) {
            // The current page still works if browser storage is disabled.
        }
    });

    const currentPath = window.location.pathname.replace(/\/+$/, "") || "/";

    sidebar.querySelectorAll(".sidebar-nav a[data-page]").forEach(link => {
        const page = link.dataset.page;
        const active = currentPath === "/" + page
            || currentPath.startsWith("/" + page + "/")
            || (page === "policies" && currentPath === "/");

        link.classList.toggle("active", active);
        if (active) {
            link.setAttribute("aria-current", "page");
        } else {
            link.removeAttribute("aria-current");
        }
    });
});
