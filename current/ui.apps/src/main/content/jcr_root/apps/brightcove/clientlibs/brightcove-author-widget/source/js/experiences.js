(function($, $document) {
    "use strict";

    // test to see if this is either the player or playlist component

    var ACCOUNTID = "./account", EXPERIENCES = "./experience";
    var API_URL = "/bin/brightcove/api";
    var existingValues = {};
    const RES_EXPERIENCE_COMPONENT = 'brightcove/components/content/brightcoveexperiences';

    function adjustLayoutHeight(){
        $(".coral-FixedColumn-column").css("height", "20rem");
    }

    $document.on("dialog-ready", function(e) {

        var dialogRes = $('.cq-Dialog form input[name="./sling:resourceType"]');

        // only act on the dialogs that matter
        if ( dialogRes.val() == RES_EXPERIENCE_COMPONENT ) {
            //adjustLayoutHeight();

            $('.js-coral-Autocomplete-selectList').on('click', function(event) {
                console.log($(event.target));
            });

            var accountSelector =  $("[name='" + ACCOUNTID +"']").get(0);
            var contentSelector =  $("[name='" + EXPERIENCES +"']").get(0);
    
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
                        //contentSelector.trigger('coral-autocomplete:showsuggestions');
                    });
                }
            });
    
            // /bin/brightcove/api?a=search_playlists&account_id=6066350955001&limit=30&start=0
    
            // contentSelector.addEventListener('coral-select:showitems', function(event) {
            //     if (contentSelector.items.length == 0) {
            //         console.log(existingValues);
            //         var ACTION = 'search_videos';
            //         var CONDITION = existingValues.experience
            //         $.getJSON(API_URL + "?a=" + ACTION + "&limit=30&start=0&account_id=" + accountSelector.value).done(function(data) {
            //             var playlists = data.items;
            //             event.preventDefault();
            //             playlists.forEach(function(value, index) {
            //                 var item = {
            //                     value: value.id,
            //                     content: {
            //                         textContent: value.name
            //                     }
            //                 }
            //                 if ( (CONDITION != null) && (item.value == CONDITION) ) {
            //                     item.selected = true;
            //                 }
            //                 contentSelector.items.add(item);
            //             });
            //         });
            //     }
            // });
    
        }        

    });

})($, $(document));