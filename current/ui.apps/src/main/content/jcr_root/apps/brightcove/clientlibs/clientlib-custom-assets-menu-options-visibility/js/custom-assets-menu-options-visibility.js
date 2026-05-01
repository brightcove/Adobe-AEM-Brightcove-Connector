(function (document, $) {
    "use strict";
    const SELECTOR_CREATE_ZIP_OPTION = ".brightcove-create-zip-option";

    function setVisibilityForCustomMenuOptions(assetsPathList) {

        let param = {
            paths: assetsPathList
        };
        Granite.$.ajax({
            url: "/bin/brightcove/custom-menu-option-visibility",
            type: "GET",
            data: param,
            async: false,
            success: function (data, status) {
                if (data && data.showRequestForVideoSyncMenuOption) {
                    $(".brightcove-request-for-video-sync-option").removeClass("foundation-collection-action-hidden");
                } else {
                    $(".brightcove-request-for-video-sync-option").addClass("foundation-collection-action-hidden");
                }
                if (data && data.showRequestForAssetDeletionMenuOption) {
                    $(".brightcove-request-for-asset-deletion-option").removeClass("foundation-collection-action-hidden");
                } else {
                    $(".brightcove-request-for-asset-deletion-option").addClass("foundation-collection-action-hidden");
                }

            }
        });
    }

    function getCurrentUserInfo() {

        Granite.$.ajax({
            url: "/bin/brightcove/author/users/current-user-info",
            type: "GET",
            async: false,
            success: function (data, status) {
                localStorage.setItem('isCurrentUserOnlybrightcoveDAMUser', data.isCurrentUserOnlybrightcoveDAMUser);
            }
        });
    }

    getCurrentUserInfo();

    // BCON-130: Swap the low-res brc_thumbnail.png for the full-size brc_poster.png
    // on the DAM asset details page. AEM's Video.js component hardcodes the thumbnail
    // rendition as the poster; we poll until Video.js has rendered .vjs-poster and
    // then update its background-image URL.
    if (location.pathname.indexOf('/assetdetails.html/') === 0) {
        var posterSwapInterval = setInterval(function () {
            var vjsPoster = document.querySelector('#dam_video .vjs-poster');
            if (vjsPoster) {
                clearInterval(posterSwapInterval);
                var bg = vjsPoster.style.backgroundImage || window.getComputedStyle(vjsPoster).backgroundImage;
                if (bg && bg.indexOf('brc_thumbnail.png') !== -1) {
                    vjsPoster.style.backgroundImage = bg.replace('brc_thumbnail.png', 'brc_poster.png');
                }
            }
        }, 200);
        // Stop polling after 15 seconds regardless
        setTimeout(function () { clearInterval(posterSwapInterval); }, 15000);
    }

    function processActionMenuItems(tile) {

        if (tile) {
            let cardActionBar = tile.next();
            let anchors = cardActionBar.find('.foundation-anchor');
            for (let i = 0; i < anchors.length; ++i) {
                let anchor = anchors[i];
                let iconAttribute = $(anchor).attr("icon");
                if (iconAttribute === "edit") {
                    anchor.remove();
                }
            }
        }
    }

    $(document).on("foundation-selections-change", function (e) {

        var selectedItems = $(".foundation-collection").find(".foundation-selections-item");
        var selectedItemsPathList = "";
        if (selectedItems.length > 0) {
            for (var i = 0; i < selectedItems.length; ++i) {
                selectedItemsPathList += selectedItems[i].dataset.foundationCollectionItemId + "|";
            }
            selectedItemsPathList = selectedItemsPathList.substring(0, selectedItemsPathList.length - 1);
            setVisibilityForCustomMenuOptions(selectedItemsPathList);
        }
    });

    $(document).on("foundation-contentloaded", function (e) {

        let currentPath = location.pathname;
        if (currentPath.indexOf('/assetdetails.html/') == 0) {
            let assetPath = currentPath.replace('/assetdetails.html/', '/');
            setVisibilityForCustomMenuOptions(assetPath);
        }

        jQuery('.foundation-layout-masonry-cardwrapper').each(function () {

            let card = $(this);
            let processedCard = card.attr('data-processed');
            let isCurrentUserOnlybrightcoveDAMUser = localStorage.getItem('isCurrentUserOnlybrightcoveDAMUser');
            if (isCurrentUserOnlybrightcoveDAMUser === "true") {
                if (!processedCard) {
                    let hrefValue = card.attr('href');
                    if (hrefValue && (hrefValue.indexOf('/assetdetails.html/') == 0)) {
                        card.attr('data-processed', true);
                        let tileIcons = card.find('._coral-Icon--sizeXS._coral-Card-property-icon._coral-Icon');
                        for (let i = 0; i < tileIcons.length; ++i) {
                            let icon = tileIcons[i];
                            let iconType = $(icon).attr("icon");
                            if ((iconType === "globe") || (iconType === "thumbUp")) {
                                processActionMenuItems(card);
                                break;
                            }
                        }
                    }
                }
            }
        });
    });
}(document, Granite.$));