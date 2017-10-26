(function (document, $) {
    "use strict";

    var ACCOUNT = "./account", PLAYERID = "./playerID";


    $(document).on("dialog-ready", function() {
        init();
    });
    $(window).load(function () {
        init();
    });
    function init() {
        var account = $("[name='" + ACCOUNT +"']").closest(".coral-Select")
        var playerID = new CUI.Select({
            element: $("[name='" + PLAYERID +"']").closest(".coral-Select")
        });
        if(_.isEmpty(playerID) || _.isEmpty(account)){
            return;
        }
        function fillPlayers(selectedAccount, selectedPlayer){
            playerID = new CUI.Select({
                element: $("[name='" + PLAYERID +"']").closest(".coral-Select")
            });
            $("[role='option']",playerID._selectList).remove();

            var x = $("[name='./playerID']").closest(".coral-Select").find('option').remove().end();
            $.getJSON("/bin/brightcove/api?a=players&account_id="+selectedAccount).done(function(data){
                _.each(data.items, function(value, id) {
                    var test2 = $("[name='./playerID']")[0];
                    $("<option "+(selectedPlayer === value.id ? "selected" : "")+" >").appendTo(test2).val(value.id).html(value.name);
                    $("<li class='coral-SelectList-item coral-SelectList-item--option' data-value='"+value.id+"' aria-selected='"+(selectedPlayer === value.id)+"' role='option'>"+value.name+"</li>").appendTo(playerID._selectList);
                });
                if(!_.isEmpty(selectedPlayer)){

                    playerID.setValue(selectedPlayer);

                }
            });



        }
        account.on('selected.select', function(event){
            console.log(event);
            fillPlayers(event.selected);
        });
        var $form = playerID.$element.closest("form");
        $.getJSON($form.attr("action") + ".json").done(function(data){
            if(_.isEmpty(data)){
                return;
            }
            fillPlayers($("[name='" + ACCOUNT +"']").val(),data.playerID);
        });
    }
})(document, Granite.$);