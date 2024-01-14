(function($, $document) {
    "use strict";

    var ACCOUNTID = "./account", EXPERIENCES = "./experience";
    var existingValues = {};

    const DIALOG_ACCOUNT_FIELD_SELECTOR = '.brightcove-dialog-experiences-account-dropdown';

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

    $document.on("dialog-ready", function(e) {

        // only act on the dialogs that matter
        if ( isBrightcoveDialog() ) {

            var accountSelector =  $("[name='" + ACCOUNTID +"']").get(0);
            var contentSelector =  $("[name='" + EXPERIENCES +"']").parent().parent().find('[data-granite-autocomplete-src]');

            console.log($('.cq-Dialog form').attr("action"));

            $.getJSON($('.cq-Dialog form').attr("action") + ".json").done(function(data) {
                existingValues = data;
                accountSelector.trigger('coral-select:showitems');
            });

            accountSelector.addEventListener('coral-select:showitems', function(event) {
                if (accountSelector.items.length == 0) {
                    $.getJSON("/bin/brightcove/accounts.json").done(function(data) {
                        var accounts = data.accounts;
                        var selected;
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
                            if (item.selected) {
                                selected = item.value;
                            }
                            accountSelector.items.add(item);
                        });

                        // add the account ID to the suggestion API URL
                        contentSelector.attr('data-granite-autocomplete-src',
                            updateQueryStringParameter(
                                contentSelector.attr('data-granite-autocomplete-src'),
                                'account_id',
                                selected
                            )
                        );

                        //contentSelector.trigger('coral-select:showitems');

                        // now trigger the other fields
                        //contentSelector.trigger('coral-autocomplete:showsuggestions');
                    });
                }
            });

        }

    });

})($, $(document));