document.addEventListener('DOMContentLoaded', () => {
    const container = document.getElementById('system-data-container');
    if (!container) return;

    let controller;
    async function loadSystemData() {
        if (controller) controller.abort();
        controller = new AbortController();
        container.setAttribute('aria-busy', 'true');
        try {
            const response = await fetch('/system/data', {
                signal: controller.signal,
                headers: { 'X-Requested-With': 'XMLHttpRequest' },
                cache: 'no-store'
            });
            if (!response.ok) throw new Error('HTTP ' + response.status);
            const html = await response.text();
            container.innerHTML = html;
        } catch (error) {
            if (error.name === 'AbortError') return;
            console.error('System information loading failed:', error);
            container.innerHTML = '<section class="system-loading system-loading-error">' +
                '<div><strong>Could not load system information.</strong>' +
                '<p>Check the application log and try again.</p>' +
                '<button type="button" id="system-retry">Retry</button></div></section>';
            document.getElementById('system-retry').addEventListener('click', loadSystemData);
        } finally {
            container.setAttribute('aria-busy', 'false');
        }
    }
    loadSystemData();
});
