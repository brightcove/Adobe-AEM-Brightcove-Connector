/*
 Adobe AEM Brightcove Connector

 Copyright (C) 2017 Coresecure Inc.

 Authors:
 Alessandro Bonfatti
 Yan Kisen
 Pablo Kropilnicki

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.

 - Additional permission under GNU GPL version 3 section 7
 If you modify this Program, or any covered work, by linking or combining
 it with httpclient 4.1.3, httpcore 4.1.4, httpmine 4.1.3, jsoup 1.7.2,
 squeakysand-commons and squeakysand-osgi (or a modified version of those
 libraries), containing parts covered by the terms of APACHE LICENSE 2.0
 or MIT License, the licensors of this Program grant you additional
 permission to convey the resulting work.
*/
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