(function (document, $) {
    "use strict";

    var currentMode; // 'add' or 'edit'
    var currentVideoId;
    var currentLanguage; // only set in edit mode

    function getVariantsData() {
        var scriptEl = document.getElementById("brc-variants-data");
        if (!scriptEl) return [];
        try {
            return JSON.parse(scriptEl.textContent);
        } catch (e) {
            return [];
        }
    }

    function setVariantsData(variants) {
        var scriptEl = document.getElementById("brc-variants-data");
        if (scriptEl) scriptEl.textContent = JSON.stringify(variants);
    }

    function showError(dlg, message) {
        var errorEl = dlg.querySelector("#brc-variant-error");
        if (errorEl) {
            errorEl.textContent = message;
            errorEl.style.display = "block";
        }
    }

    function clearError(dlg) {
        var errorEl = dlg.querySelector("#brc-variant-error");
        if (errorEl) {
            errorEl.textContent = "";
            errorEl.style.display = "none";
        }
    }

    function clearForm(dlg) {
        dlg.querySelector("#brc-variant-language").value = "";
        dlg.querySelector("#brc-variant-name").value = "";
        dlg.querySelector("#brc-variant-description").value = "";
        dlg.querySelector("#brc-variant-long-description").value = "";
        dlg.querySelectorAll(".brc-variant-cf").forEach(function (el) {
            el.value = "";
        });
        clearError(dlg);
    }

    function openAddDialog(videoId) {
        var dlg = document.getElementById("brc-variant-dialog");
        if (!dlg) return;

        currentMode = "add";
        currentVideoId = videoId;

        dlg.querySelector("coral-dialog-header").textContent = "Add Variant";

        var langField = dlg.querySelector("#brc-variant-language");
        langField.removeAttribute("readonly");
        clearForm(dlg);
        dlg.show();
    }

    function openEditDialog(videoId, language) {
        var dlg = document.getElementById("brc-variant-dialog");
        if (!dlg) return;

        currentMode = "edit";
        currentVideoId = videoId;
        currentLanguage = language;

        dlg.querySelector("coral-dialog-header").textContent = "Edit Variant";

        clearForm(dlg);

        // Language is read-only in edit mode
        var langField = dlg.querySelector("#brc-variant-language");
        langField.value = language;
        langField.setAttribute("readonly", "readonly");

        // Populate from stored variant data
        var variants = getVariantsData();
        var variant = null;
        for (var i = 0; i < variants.length; i++) {
            if (variants[i].language === language) {
                variant = variants[i];
                break;
            }
        }

        if (variant) {
            dlg.querySelector("#brc-variant-name").value = variant.name || "";
            dlg.querySelector("#brc-variant-description").value = variant.description || "";
            dlg.querySelector("#brc-variant-long-description").value = variant.long_description || "";
            var customFields = variant.custom_fields || {};
            dlg.querySelectorAll(".brc-variant-cf").forEach(function (el) {
                var cfId = el.dataset.cfId;
                if (cfId && customFields[cfId] != null) {
                    el.value = customFields[cfId];
                }
            });
        }

        dlg.show();
    }

    function buildCustomFieldsObject(dlg) {
        var obj = {};
        dlg.querySelectorAll(".brc-variant-cf").forEach(function (el) {
            var cfId = el.dataset.cfId;
            if (cfId) obj[cfId] = el.value || "";
        });
        return obj;
    }

    function addBadgeToDOM(language, videoId) {
        var list = document.querySelector(".brc-variants-list");
        if (!list) return;

        // Remove the "no variants" placeholder if present
        var placeholder = list.querySelector(".coral-Form-fielddescription");
        if (placeholder) placeholder.remove();

        var idx = list.querySelectorAll(".brc-variant-badge").length;

        var badge = document.createElement("span");
        badge.className = "brc-variant-badge";
        badge.style.cssText = "display:inline-flex;align-items:center;gap:6px;padding:4px 10px;background:#e8f0fe;border-radius:14px;font-size:12px;";

        var langSpan = document.createElement("span");
        langSpan.className = "brc-variant-language";
        langSpan.textContent = language;
        badge.appendChild(langSpan);

        var editBtn = document.createElement("button");
        editBtn.setAttribute("is", "coral-button");
        editBtn.setAttribute("variant", "minimal");
        editBtn.setAttribute("size", "S");
        editBtn.className = "brc-edit-variant-btn";
        editBtn.dataset.language = language;
        editBtn.dataset.variantIndex = String(idx);
        editBtn.dataset.videoId = videoId;
        editBtn.type = "button";
        editBtn.textContent = "Edit";
        badge.appendChild(editBtn);

        list.appendChild(badge);

        editBtn.addEventListener("click", function () {
            openEditDialog(this.dataset.videoId, this.dataset.language);
        });
    }

    function handleSave() {
        var dlg = document.getElementById("brc-variant-dialog");
        if (!dlg) return;

        clearError(dlg);

        var language = dlg.querySelector("#brc-variant-language").value.trim();
        if (!language) {
            showError(dlg, "Language code is required.");
            return;
        }

        var name          = dlg.querySelector("#brc-variant-name").value.trim();
        var description   = dlg.querySelector("#brc-variant-description").value.trim();
        var longDesc      = dlg.querySelector("#brc-variant-long-description").value.trim();
        var customFields  = buildCustomFieldsObject(dlg);
        var action        = currentMode === "add" ? "add_variant" : "update_variant";

        Granite.$.ajax({
            url: "/bin/brightcove/api.json",
            type: "POST",
            data: {
                a: action,
                videoId: currentVideoId,
                language: language,
                name: name,
                description: description,
                long_description: longDesc,
                custom_fields: JSON.stringify(customFields)
            },
            success: function (data) {
                if (data && data.error && data.error !== null && data.error !== 0) {
                    showError(dlg, "Save failed. Please check the language code and try again.");
                    return;
                }
                dlg.hide();
                if (currentMode === "add") {
                    addBadgeToDOM(language, currentVideoId);
                    var variants = getVariantsData();
                    variants.push({
                        language: language,
                        name: name,
                        description: description,
                        long_description: longDesc,
                        custom_fields: customFields
                    });
                    setVariantsData(variants);
                } else {
                    // Update the in-memory data so subsequent edits see fresh values
                    var variants = getVariantsData();
                    for (var i = 0; i < variants.length; i++) {
                        if (variants[i].language === currentLanguage) {
                            variants[i].name = name;
                            variants[i].description = description;
                            variants[i].long_description = longDesc;
                            variants[i].custom_fields = customFields;
                            break;
                        }
                    }
                    setVariantsData(variants);
                }
            },
            error: function () {
                showError(dlg, "An error occurred. Please try again.");
            }
        });
    }

    function bindButtons() {
        var saveBtn = document.getElementById("brc-variant-save");
        if (saveBtn && !saveBtn.dataset.brcBound) {
            saveBtn.dataset.brcBound = "1";
            saveBtn.addEventListener("click", handleSave);
        }

        document.querySelectorAll(".brc-add-variant-btn").forEach(function (btn) {
            if (!btn.dataset.brcBound) {
                btn.dataset.brcBound = "1";
                btn.addEventListener("click", function () {
                    openAddDialog(this.dataset.videoId);
                });
            }
        });

        document.querySelectorAll(".brc-edit-variant-btn").forEach(function (btn) {
            if (!btn.dataset.brcBound) {
                btn.dataset.brcBound = "1";
                btn.addEventListener("click", function () {
                    openEditDialog(this.dataset.videoId, this.dataset.language);
                });
            }
        });
    }

    $(document).on("foundation-contentloaded", function () {
        Coral.commons.ready(function () {
            bindButtons();
        });
    });

}(document, Granite.$));
