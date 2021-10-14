(function($, $document) {
    "use strict";

    var ACCOUNTID = "./account", PLAYLISTS = "./videoPlayerPL", 
                    PLAYERS = "./playerPath", VIDEOS = "./videoPlayer";
    var API_URL = "/bin/brightcove/api";

    var existingValues = {};
    var account_id = "";

    const DIALOG_VIDEO_FIELD_SELECTOR = '.brightcove-dialog-video-autocomplete';
    const DIALOG_PLAYLIST_FIELD_SELECTOR = '.brightcove-dialog-playlist-dropdown';
    const DIALOG_PLAYER_FIELD_SELECTOR = '.brightcove-dialog-player-dropdown';
    const DIALOG_ACCOUNT_FIELD_SELECTOR = '.brightcove-dialog-account-dropdown';

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
        $(DIALOG_VIDEO_FIELD_SELECTOR + ' ul.coral-SelectList').attr('data-granite-autocomplete-src',
            updateQueryStringParameter(
                $(DIALOG_VIDEO_FIELD_SELECTOR + ' ul.coral-SelectList').attr('data-granite-autocomplete-src'),
                "account_id",
                account_id)
            );
        
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
                accountSelector.items.clear();
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
                updateAutocompleteWithAcountId();
            });
    
            playerSelector.addEventListener('coral-select:showitems', function(event) {
                playerSelector.items.clear();

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