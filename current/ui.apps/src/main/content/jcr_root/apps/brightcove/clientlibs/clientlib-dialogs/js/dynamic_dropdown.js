(function($, $document) {
    "use strict";

    var existingValues = {};
    var account_id = "";

    const DIALOG_VIDEO_FIELD_SELECTOR = '.brightcove-dialog-video-autocomplete';
    const DIALOG_PLAYLIST_FIELD_SELECTOR = '.brightcove-dialog-playlist-dropdown';
    const DIALOG_PLAYER_FIELD_SELECTOR = '.brightcove-dialog-player-dropdown';
    const DIALOG_ACCOUNT_FIELD_SELECTOR = '.brightcove-dialog-account-dropdown';

    function setFoundationValue($el, value) {
        var field = $el.adaptTo("foundation-field");
        if (field) {
            field.setValue(value);
            return true;
        }
        return false;
    }

    function clearVideoAndPlayerSelections(contentSelector, playerSelector) {
        // Clear stored selections so that later "selected=true" logic doesn't re-select old values
        existingValues.videoPlayer = null;
        existingValues.videoPlayerPL = null;
        existingValues.playerPath = null;

        // Clear UI values
        // Player (coral select)
        try {
            playerSelector.value = "";
        } catch (e) {}
        setFoundationValue($(DIALOG_PLAYER_FIELD_SELECTOR), "");

        // Video: autocomplete OR playlist select (depending on component)
        if ($(DIALOG_PLAYLIST_FIELD_SELECTOR).length > 0) {
            // playlist component: contentSelector is a coral select
            try {
                contentSelector.value = "";
            } catch (e) {}
            setFoundationValue($(DIALOG_PLAYLIST_FIELD_SELECTOR), "");
        } else {
            // player component: granite autocomplete
            setFoundationValue($(DIALOG_VIDEO_FIELD_SELECTOR), "");

            // clear the visible text input if foundation-field doesn’t
            $(DIALOG_VIDEO_FIELD_SELECTOR)
                .find("input[type='text'], input.coral-InputGroup-input")
                .val("")
                .trigger("change");
        }

        // Force player list to reload for the new account:
        try {
            playerSelector.items.clear();
        } catch (e) {}

        // For playlist select, updateAutocompleteWithAcountId() already does items.clear() so no need to repeat here
    }

    function isBrightcoveDialog() {
        return ( $(DIALOG_ACCOUNT_FIELD_SELECTOR).length > 0 );
    }

    function updateQueryStringParameter(uri, key, value) {
        var re = new RegExp("([?&])" + key + "=.*?(&|$)", "i");
        var separator = uri.indexOf('?') !== -1 ? "&" : "?";
        if (uri.match(re)) {
            return uri.replace(re, '$1' + key + "=" + value + '$2');
        }
        else {
            return uri + separator + key + "=" + value;
        }
    }

    function updateAutocompleteWithAcountId() {
        var accountSelector =  $(DIALOG_ACCOUNT_FIELD_SELECTOR).get(0);
        account_id = (accountSelector.selectedItem != null)
                            ? accountSelector.selectedItem.value : "";

        if ( $(DIALOG_PLAYLIST_FIELD_SELECTOR).length > 0 ) {
            // this is a playlist component
            var contentSelector = $(DIALOG_PLAYLIST_FIELD_SELECTOR).get(0);
            contentSelector.items.clear();

            var ACTION = 'playlists';
            var CONDITION = ( $(DIALOG_PLAYLIST_FIELD_SELECTOR).length > 0 ) ? existingValues.videoPlayerPL : existingValues.videoPlayer;

            $.getJSON("/bin/brightcove/getLocalVideoList.json", {
                source: ACTION,
                account_id: account_id
            }).done(function(data) {
                var videos = data.items;
                event.preventDefault();
                videos.forEach(function(value, index) {
                    var item = {
                        value: value.id,
                        content: {
                            textContent: (ACTION == 'playlists') ? value.name : value.title
                        }
                    }
                    if ( (CONDITION != null) && (item.value == CONDITION) ) {
                        item.selected = true;
                    }
                    contentSelector.items.add(item);
                });
            });
        } else {
            // this is a player component
            $(DIALOG_VIDEO_FIELD_SELECTOR + ' ul.coral-SelectList').attr('data-granite-autocomplete-src',
                updateQueryStringParameter(
                    $(DIALOG_VIDEO_FIELD_SELECTOR + ' ul.coral-SelectList').attr('data-granite-autocomplete-src'),
                    "account_id",
                    account_id)
                );
        }
    }


    $document.on("dialog-ready", function(e) {

        if ( isBrightcoveDialog() ) {

            var accountSelector =  $(DIALOG_ACCOUNT_FIELD_SELECTOR).get(0);
            var contentSelector =  ( $(DIALOG_VIDEO_FIELD_SELECTOR).length > 0 )
                ? $(DIALOG_VIDEO_FIELD_SELECTOR).get(0)
                : $(DIALOG_PLAYLIST_FIELD_SELECTOR).get(0);

            var playerSelector =  $(DIALOG_PLAYER_FIELD_SELECTOR).get(0);

            $.getJSON($('.cq-Dialog form').attr("action") + ".json").done(function(data) {
                existingValues = data;
                accountSelector.trigger('coral-select:showitems');
            });

            accountSelector.addEventListener('coral-select:showitems', function(event) {
                //accountSelector.items.clear();
                if (accountSelector.items.length == 0) {
                    $.getJSON("/bin/brightcove/accounts.json").done(function(data) {
                        var accounts = data.accounts;
                        event.preventDefault();
                        accounts.forEach(function(value, index) {
                            var item = {
                                value: value.value,
                                content: {
                                    textContent: value.text
                                }
                            }
                            if (existingValues.account == null && index == 0) {
                                item.selected = true;
                            } else if (item.value == existingValues.account) {
                                item.selected = true;
                            }
                            accountSelector.items.add(item);
                        });

                        updateAutocompleteWithAcountId();

                        // now trigger the other fields
                        //contentSelector.trigger('coral-select:showitems');
                        playerSelector.trigger('coral-select:showitems');
                    });
                }
            });

            accountSelector.addEventListener("change", function(event) {
                // 1) Clear the dependent fields immediately so UI doesn’t show stale selection
                clearVideoAndPlayerSelections(contentSelector, playerSelector);

                // 2) Update video datasource URL / playlist items for the new account
                updateAutocompleteWithAcountId();
            });

            playerSelector.addEventListener('coral-select:showitems', function(event) {
                var accountSelector =  $(DIALOG_ACCOUNT_FIELD_SELECTOR).get(0);
                var account_id = (accountSelector.selectedItem != null)
                            ? accountSelector.selectedItem.value : "";

                if (playerSelector.items.length == 0) {
                    $.getJSON("/bin/brightcove/getLocalVideoList.json", {
                        source: 'players',
                        account_id: account_id
                    }).done(function(data) {
                        var players = data.items;
                        event.preventDefault();
                        players.forEach(function(value, index) {
                            var item = {
                                value: value.id,
                                content: {
                                    textContent: value.name
                                }
                            }
                            if (existingValues.playerPath != null && item.value == existingValues.playerPath) {
                                item.selected = true;
                            }
                            playerSelector.items.add(item);
                        });
                    });
                }
            });
        }
    });

})($, $(document));