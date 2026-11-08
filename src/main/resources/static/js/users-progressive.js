/* Progressive loading of local Windows accounts. */
(() => {
    let currentRequest = null;

    async function loadUsers() {
        const container = document.getElementById('users-dynamic');
        if (!container) return;
        if (currentRequest) currentRequest.abort();
        const controller = new AbortController();
        currentRequest = controller;
        container.setAttribute('aria-busy', 'true');
        try {
            const response = await fetch('/users/data', {
                signal: controller.signal,
                headers: { 'Accept': 'text/html' },
                cache: 'no-store'
            });
            if (!response.ok) throw new Error(`HTTP ${response.status}`);
            const html = await response.text();
            if (currentRequest !== controller) return;
            container.innerHTML = html;
            const refresh = container.querySelector('.refresh-button');
            if (refresh) {
                refresh.removeAttribute('onclick');
                refresh.addEventListener('click', () => {
                    refresh.disabled = true;
                    refresh.textContent = 'Refreshing…';
                    loadUsers();
                });
            }
        } catch (error) {
            if (error.name === 'AbortError') return;
            container.innerHTML = '<section class="users-panel"><div class="users-loading-error" role="alert">Could not load Windows accounts. <button type="button" class="refresh-button" id="users-retry">Retry</button></div></section>';
            document.getElementById('users-retry').addEventListener('click', loadUsers);
            console.error('Users loading failed:', error);
        } finally {
            if (currentRequest === controller) {
                currentRequest = null;
                container.setAttribute('aria-busy', 'false');
            }
        }
    }

    document.addEventListener('DOMContentLoaded', loadUsers);
})();
