(function($, $document) {
    "use strict";

    // test to see if this is either the player or playlist component
    console.log('init dynamic_dropdown()');

    var ACCOUNTID = "./account", PLAYLISTS = "./videoPlayerPL", 
                    PLAYERS = "./playerPath", VIDEOS = "./videoPlayer";
    var API_URL = "/bin/brightcove/api";
    var existingValues = {};
    const RES_PLAYLIST_COMPONENT = 'brightcove/components/content/brightcoveplayer-playlist';
    const RES_PLAYER_COMPONENT = 'brightcove/components/content/brightcoveplayer';

    function adjustLayoutHeight(){
        $(".coral-FixedColumn-column").css("height", "20rem");
    }

    $document.on("dialog-ready", function(e) {

        var dialogRes = $('.cq-Dialog form input[name="./sling:resourceType"]');

        // only act on the dialogs that matter
        if ( dialogRes.val().indexOf('brightcoveplayer') >= 0 ) {
            adjustLayoutHeight();

            var accountSelector =  $("[name='" + ACCOUNTID +"']").get(0);
            var contentSelector =  (dialogRes.val() == RES_PLAYER_COMPONENT) ? $("[name='" + VIDEOS +"']").get(0) : $("[name='" + PLAYLISTS +"']").get(0)
            var playerSelector =  $("[name='" + PLAYERS +"']").get(0);
    
            $.getJSON($('.cq-Dialog form').attr("action") + ".json").done(function(data) {
                existingValues = data;
                accountSelector.trigger('coral-select:showitems');
            });
    
            accountSelector.addEventListener('coral-select:showitems', function(event) {
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
                        contentSelector.trigger('coral-select:showitems');            playerSelector.trigger('coral-select:showitems');
                    });
                }
            });
    
            contentSelector.addEventListener('coral-select:showitems', function(event) {
                if (contentSelector.items.length == 0) {
                    var ACTION = (dialogRes.val() == RES_PLAYLIST_COMPONENT) ? 'playlists' : 'videos';
                    var CONDITION = (dialogRes.val() == RES_PLAYLIST_COMPONENT) ? existingValues.videoPlayerPL : existingValues.videoPlayer;
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
    
            // /bin/brightcove/api?a=local_players&account_id=6066350955001&limit=30&start=0
    
            playerSelector.addEventListener('coral-select:showitems', function(event) {
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