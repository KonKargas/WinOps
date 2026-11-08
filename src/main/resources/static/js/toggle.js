document.addEventListener("DOMContentLoaded", () => {

    /*
     * =========================================================
     * POLICY SWITCHES
     * =========================================================
     */

    const switches =
        document.querySelectorAll(".switch");


    switches.forEach(policySwitch => {

        policySwitch.addEventListener(
            "click",
            function (event) {

                // Prevent immediate form submission
                event.preventDefault();


                const form =
                    this.closest("form");

                const enabledInput =
                    form.querySelector(
                        'input[name="enabled"]'
                    );

                const stateText =
                    this.closest(".policy-control")
                        .querySelector(".policy-state");


                const isCurrentlyActive =
                    this.classList.contains("active");


                // Toggle visual state
                this.classList.toggle("active");


                if (isCurrentlyActive) {

                    // ON -> OFF
                    enabledInput.value = "false";

                    stateText.textContent = "Disabled";

                    stateText.classList.remove(
                        "enabled"
                    );

                    stateText.classList.add(
                        "disabled"
                    );

                } else {

                    // OFF -> ON
                    enabledInput.value = "true";

                    stateText.textContent = "Enabled";

                    stateText.classList.remove(
                        "disabled"
                    );

                    stateText.classList.add(
                        "enabled"
                    );
                }


                // Give the animation time to play,
                // then send the request to Spring Boot.
                setTimeout(() => {

                    form.submit();

                }, 220);
            }
        );
    });


    /*
     * =========================================================
     * RESTORE POLICY CATEGORY STATES
     * =========================================================
     */

    restoreCategoryStates();

});


/*
 * =========================================================
 * TOGGLE POLICY CATEGORY
 * =========================================================
 */

function toggleCategory(header) {

    const category =
        header.closest(".policy-category");

    const policyList =
        category.querySelector(".policy-list");

    const arrow =
        header.querySelector(".category-arrow");


    const categoryKey =
        getCategoryStorageKey(category);


    const isCollapsed =
        policyList.classList.contains("collapsed");


    if (isCollapsed) {

        /*
         * EXPAND
         */

        policyList.classList.remove(
            "collapsed"
        );

        arrow.classList.remove(
            "collapsed"
        );


        localStorage.setItem(
            categoryKey,
            "expanded"
        );

    } else {

        /*
         * COLLAPSE
         */

        policyList.classList.add(
            "collapsed"
        );

        arrow.classList.add(
            "collapsed"
        );


        localStorage.setItem(
            categoryKey,
            "collapsed"
        );
    }
}


/*
 * =========================================================
 * RESTORE CATEGORY STATES
 * =========================================================
 */

function restoreCategoryStates() {

    const categories =
        document.querySelectorAll(
            ".policy-category"
        );


    categories.forEach(category => {

        const policyList =
            category.querySelector(
                ".policy-list"
            );

        const arrow =
            category.querySelector(
                ".category-arrow"
            );


        if (!policyList || !arrow) {
            return;
        }


        const categoryKey =
            getCategoryStorageKey(category);


        const savedState =
            localStorage.getItem(
                categoryKey
            );


        /*
         * No saved state:
         * keep the state defined by HTML.
         */
        if (savedState === null) {
            return;
        }


        if (savedState === "collapsed") {

            policyList.classList.add(
                "collapsed"
            );

            arrow.classList.add(
                "collapsed"
            );

        } else if (savedState === "expanded") {

            policyList.classList.remove(
                "collapsed"
            );

            arrow.classList.remove(
                "collapsed"
            );
        }
    });
}


/*
 * =========================================================
 * CATEGORY STORAGE KEY
 * =========================================================
 */

function getCategoryStorageKey(category) {

    /*
     * Prefer an explicit data-category attribute.
     */

    const categoryName =
        category.dataset.category;


    if (categoryName) {

        return "winops.policy.category."
            + categoryName;
    }


    /*
     * Fallback:
     * use the category title.
     */

    const title =
        category.querySelector(
            ".category-title"
        );


    const identifier =
        title
            ? title.textContent
                .trim()
                .toLowerCase()
                .replace(/\s+/g, "-")
            : "unknown";


    return "winops.policy.category."
        + identifier;
}

/* Preserve position after policy changes, including category state. */
(function () {
    const key = "winops.policies.return";
    function remember(row) {
        if (!row) return;
        sessionStorage.setItem(key, JSON.stringify({
            id: row.dataset.policyId,
            scroll: window.scrollY
        }));
    }
    document.addEventListener("DOMContentLoaded", () => {
        const saved = sessionStorage.getItem(key);
        if (saved) {
            sessionStorage.removeItem(key);
            try {
                const target = JSON.parse(saved);
                requestAnimationFrame(() => requestAnimationFrame(() => {
                    const row = [...document.querySelectorAll(".policy-row")]
                        .find(el => el.dataset.policyId === target.id);
                    if (row) row.scrollIntoView({block: "center", behavior: "instant"});
                    else window.scrollTo(0, target.scroll || 0);
                }));
            } catch (_) { /* ignore stale scroll state */ }
        }
        document.querySelectorAll(".policy-row").forEach(row => {
            const form = row.querySelector("form");
            if (!form) return;
            form.addEventListener("submit", () => remember(row));
            const toggle = form.querySelector(".switch");
            if (toggle) toggle.addEventListener("click", () => remember(row), true);
            const select = row.querySelector(".policy-value-select");
            const apply = row.querySelector(".policy-value-apply");
            if (select && apply) {
                const current = select.dataset.current;
                if (["0", "1", "2"].includes(current)) select.value = current;
                const hidden = form.querySelector('input[name="selectedValue"]');
                if (hidden) hidden.value = select.value;
                select.addEventListener("change", () => { if (hidden) hidden.value = select.value; });
                apply.addEventListener("click", () => {
                    if (hidden) hidden.value = select.value;
                    form.querySelector('input[name="enabled"]').value = "true";
                    remember(row);
                    form.submit();
                });
            }
        });
    });
})();
