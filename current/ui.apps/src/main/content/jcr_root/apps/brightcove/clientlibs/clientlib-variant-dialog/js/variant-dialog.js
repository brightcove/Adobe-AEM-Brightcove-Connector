(function (document, $) {
    "use strict";

    var currentMode;      // 'add' or 'edit'
    var currentVideoId;
    var currentAccountId;
    var currentLanguage;  // only used in edit mode

    // ── Data helpers ──────────────────────────────────────────────────────────

    function getVariantsData() {
        var el = document.getElementById("brc-variants-data");
        if (!el) return [];
        try { return JSON.parse(el.textContent); } catch (e) { return []; }
    }

    function setVariantsData(variants) {
        var el = document.getElementById("brc-variants-data");
        if (el) el.textContent = JSON.stringify(variants);
    }

    // ── Dialog helpers ────────────────────────────────────────────────────────

    function showError(dlg, msg) {
        var el = dlg.querySelector("#brc-variant-error");
        if (el) { el.textContent = msg; el.style.display = "block"; }
    }

    function clearError(dlg) {
        var el = dlg.querySelector("#brc-variant-error");
        if (el) { el.textContent = ""; el.style.display = "none"; }
    }

    function clearForm(dlg) {
        dlg.querySelector("#brc-variant-language").value = "";
        dlg.querySelector("#brc-variant-name").value = "";
        dlg.querySelector("#brc-variant-description").value = "";
        dlg.querySelector("#brc-variant-long-description").value = "";
        dlg.querySelectorAll(".brc-variant-cf").forEach(function (el) { el.value = ""; });
        clearError(dlg);
    }

    function showDialog(dlg) {
        // Wait for the specific coral-dialog element to be upgraded before calling show()
        Coral.commons.ready(dlg, function () {
            dlg.show();
        });
    }

    // ── Open add ──────────────────────────────────────────────────────────────

    function openAddDialog(videoId, accountId) {
        var dlg = document.getElementById("brc-variant-dialog");
        if (!dlg) return;

        currentMode      = "add";
        currentVideoId   = videoId;
        currentAccountId = accountId;

        dlg.querySelector("coral-dialog-header").textContent = "Add Variant";

        clearForm(dlg);
        dlg.querySelector("#brc-variant-language").removeAttribute("readonly");
        dlg.querySelector("#brc-variant-language").removeAttribute("disabled");

        showDialog(dlg);
    }

    // ── Open edit ─────────────────────────────────────────────────────────────

    function openEditDialog(videoId, accountId, language) {
        var dlg = document.getElementById("brc-variant-dialog");
        if (!dlg) return;

        currentMode      = "edit";
        currentVideoId   = videoId;
        currentAccountId = accountId;
        currentLanguage  = language;

        dlg.querySelector("coral-dialog-header").textContent = "Edit Variant";

        clearForm(dlg);

        var langField = dlg.querySelector("#brc-variant-language");
        langField.value = language;
        langField.setAttribute("readonly", "readonly");
        langField.setAttribute("disabled", "disabled");

        var variants = getVariantsData();
        var variant  = null;
        for (var i = 0; i < variants.length; i++) {
            if (variants[i].language === language) { variant = variants[i]; break; }
        }

        if (variant) {
            dlg.querySelector("#brc-variant-name").value         = variant.name          || "";
            dlg.querySelector("#brc-variant-description").value  = variant.description   || "";
            dlg.querySelector("#brc-variant-long-description").value = variant.long_description || "";
            var cf = variant.custom_fields || {};
            dlg.querySelectorAll(".brc-variant-cf").forEach(function (el) {
                var id = el.dataset.cfId;
                if (id && cf[id] != null) el.value = cf[id];
            });
        }

        showDialog(dlg);
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    function handleSave() {
        var dlg = document.getElementById("brc-variant-dialog");
        if (!dlg) return;

        clearError(dlg);

        var language = dlg.querySelector("#brc-variant-language").value.trim();
        if (!language) { showError(dlg, "Language code is required."); return; }

        var name        = dlg.querySelector("#brc-variant-name").value.trim();
        var description = dlg.querySelector("#brc-variant-description").value.trim();
        var longDesc    = dlg.querySelector("#brc-variant-long-description").value.trim();

        var customFields = {};
        dlg.querySelectorAll(".brc-variant-cf").forEach(function (el) {
            var id = el.dataset.cfId;
            if (id) customFields[id] = el.value || "";
        });

        Granite.$.ajax({
            url: "/bin/brightcove/api.json",
            type: "POST",
            data: {
                a:             currentMode === "add" ? "add_variant" : "update_variant",
                account_id:    currentAccountId,
                videoId:       currentVideoId,
                language:      language,
                name:          name,
                description:   description,
                long_description: longDesc,
                custom_fields: JSON.stringify(customFields)
            },
            success: function (data) {
                // executePost puts the HTTP status code in data.error on success (200/201).
                // Only treat it as a real error if it's a string (Brightcove error_code)
                // or a number >= 400.
                var errorVal = data && data.error;
                var isError  = errorVal != null && errorVal !== 0 &&
                               errorVal !== 200 && errorVal !== 201;
                if (isError) {
                    showError(dlg, "Save failed. Check the language code and try again.");
                    return;
                }
                dlg.hide();
                var variants = getVariantsData();
                if (currentMode === "add") {
                    addBadgeToDOM(language, currentVideoId, currentAccountId);
                    variants.push({ language: language, name: name,
                        description: description, long_description: longDesc,
                        custom_fields: customFields });
                } else {
                    for (var i = 0; i < variants.length; i++) {
                        if (variants[i].language === currentLanguage) {
                            variants[i].name = name;
                            variants[i].description = description;
                            variants[i].long_description = longDesc;
                            variants[i].custom_fields = customFields;
                            break;
                        }
                    }
                }
                setVariantsData(variants);
                persistVariantsToJcr(variants);
            },
            error: function () {
                showError(dlg, "An error occurred. Please try again.");
            }
        });
    }

    // ── JCR persistence ───────────────────────────────────────────────────────

    function persistVariantsToJcr(variants) {
        var section = document.querySelector(".brc-variants-section");
        if (!section || !section.dataset.assetPath) return;

        // Sling POST multi-value String[]: send each element as a separate
        // brc_variants param alongside the @TypeHint hint.
        // traditional:true prevents jQuery from appending [0],[1] indices.
        Granite.$.ajax({
            url: section.dataset.assetPath + "/jcr:content/metadata",
            type: "POST",
            traditional: true,
            data: {
                "brc_variants":          variants.map(function (v) { return JSON.stringify(v); }),
                "brc_variants@TypeHint": "String[]"
            },
            error: function () {
                // Non-fatal — the change already exists in Brightcove and
                // JCR will be updated on the next scheduled sync.
            }
        });
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    function handleDelete(videoId, accountId, language) {
        if (!window.confirm("Delete the \"" + language + "\" variant? This cannot be undone.")) return;

        Granite.$.ajax({
            url: "/bin/brightcove/api.json",
            type: "POST",
            data: {
                a:          "delete_variant",
                account_id: accountId,
                videoId:    videoId,
                language:   language
            },
            success: function (data) {
                var errorVal = data && data.error;
                var isError  = errorVal != null && errorVal !== 0 &&
                               errorVal !== 200 && errorVal !== 204;
                if (isError) {
                    window.alert("Delete failed. Please try again.");
                    return;
                }
                // Remove badge from DOM
                var list = document.querySelector(".brc-variants-list");
                if (list) {
                    var badges = list.querySelectorAll(".brc-variant-badge");
                    for (var i = 0; i < badges.length; i++) {
                        var langEl = badges[i].querySelector(".brc-variant-language");
                        if (langEl && langEl.textContent === language) {
                            badges[i].remove();
                            break;
                        }
                    }
                    if (list.querySelectorAll(".brc-variant-badge").length === 0) {
                        var empty = document.createElement("span");
                        empty.className   = "coral-Form-fielddescription";
                        empty.textContent = "No variants configured.";
                        list.appendChild(empty);
                    }
                }
                // Update in-memory store and persist to JCR
                var variants = getVariantsData().filter(function (v) { return v.language !== language; });
                setVariantsData(variants);
                persistVariantsToJcr(variants);
            },
            error: function () {
                window.alert("An error occurred. Please try again.");
            }
        });
    }

    // ── DOM update after add ──────────────────────────────────────────────────

    function addBadgeToDOM(language, videoId, accountId) {
        var list = document.querySelector(".brc-variants-list");
        if (!list) return;

        var placeholder = list.querySelector(".coral-Form-fielddescription");
        if (placeholder) placeholder.remove();

        var idx = list.querySelectorAll(".brc-variant-badge").length;

        var badge    = document.createElement("span");
        badge.className  = "brc-variant-badge";
        badge.style.cssText = "display:inline-flex;align-items:center;gap:6px;padding:4px 10px;background:#e8f0fe;border-radius:14px;font-size:12px;";

        var langSpan = document.createElement("span");
        langSpan.className   = "brc-variant-language";
        langSpan.textContent = language;
        badge.appendChild(langSpan);

        var editBtn = document.createElement("button");
        editBtn.setAttribute("is", "coral-button");
        editBtn.setAttribute("variant", "minimal");
        editBtn.setAttribute("size", "S");
        editBtn.className            = "brc-edit-variant-btn";
        editBtn.dataset.language     = language;
        editBtn.dataset.variantIndex = String(idx);
        editBtn.dataset.videoId      = videoId;
        editBtn.dataset.accountId    = accountId;
        editBtn.type                 = "button";
        editBtn.textContent          = "Edit";
        badge.appendChild(editBtn);

        var delBtn = document.createElement("button");
        delBtn.setAttribute("is", "coral-button");
        delBtn.setAttribute("variant", "minimal");
        delBtn.setAttribute("size", "S");
        delBtn.className         = "brc-delete-variant-btn";
        delBtn.dataset.language  = language;
        delBtn.dataset.videoId   = videoId;
        delBtn.dataset.accountId = accountId;
        delBtn.type              = "button";
        delBtn.textContent       = "Delete";
        badge.appendChild(delBtn);

        list.appendChild(badge);
    }

    // ── Event binding (delegation — works regardless of load timing) ──────────

    // Add variant
    $(document).on("click", ".brc-add-variant-btn", function () {
        openAddDialog(this.dataset.videoId, this.dataset.accountId);
    });

    // Edit variant
    $(document).on("click", ".brc-edit-variant-btn", function () {
        openEditDialog(this.dataset.videoId, this.dataset.accountId, this.dataset.language);
    });

    // Delete variant
    $(document).on("click", ".brc-delete-variant-btn", function () {
        handleDelete(this.dataset.videoId, this.dataset.accountId, this.dataset.language);
    });

    // Save (inside dialog)
    $(document).on("click", "#brc-variant-save", handleSave);

}(document, Granite.$));
