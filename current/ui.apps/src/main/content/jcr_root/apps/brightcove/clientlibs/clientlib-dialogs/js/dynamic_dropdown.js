(function($, $document) {
    "use strict";

    var ACCOUNTID = "./account", PLAYLISTS = "./videoPlayerPL", 
                    PLAYERS = "./playerPath", VIDEOS = "./videoPlayer";
    var API_URL = "/bin/brightcove/api";

    var existingValues = {};

    const DIALOG_VIDEO_FIELD_SELECTOR = '.brightcove-dialog-video-dropdown';
    const DIALOG_PLAYLIST_FIELD_SELECTOR = '.brightcove-dialog-playlist-dropdown';
    const DIALOG_PLAYER_FIELD_SELECTOR = '.brightcove-dialog-player-dropdown';
    const DIALOG_ACCOUNT_FIELD_SELECTOR = '.brightcove-dialog-account-dropdown';

    function isBrightcoveDialog() {
        return ( $(DIALOG_ACCOUNT_FIELD_SELECTOR).length > 0 );
    }

    $document.on("dialog-ready", function(e) {

        if ( isBrightcoveDialog() ) {

            console.log("brightcove dialog ready");

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
    
                        // now trigger the other fields
                        contentSelector.trigger('coral-select:showitems');
                        playerSelector.trigger('coral-select:showitems');
                    });
                }
            });
    
            contentSelector.addEventListener('coral-select:showitems', function(event) {
                contentSelector.items.clear();
                if (contentSelector.items.length == 0) {
                    var ACTION = ( $(DIALOG_PLAYLIST_FIELD_SELECTOR).length > 0 ) ? 'playlists' : 'videos';
                    var CONDITION = ( $(DIALOG_PLAYLIST_FIELD_SELECTOR).length > 0 ) ? existingValues.videoPlayerPL : existingValues.videoPlayer;
                    $.getJSON("/bin/brightcove/getLocalVideoList.json", {
                        source: ACTION
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
                }
            });
    
            playerSelector.addEventListener('coral-select:showitems', function(event) {
                playerSelector.items.clear();
                if (playerSelector.items.length == 0) {
                    $.getJSON("/bin/brightcove/getLocalVideoList.json", {
                        source: 'players'
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