(function($, $document) {
    "use strict";

    var ACCOUNTID = "./accountName", VIDEOLIST = "./videoList";

    function adjustLayoutHeight(){
        $(".coral-FixedColumn-column").css("height", "20rem");
    }

    var accountSelector =  $("[name='" + ACCOUNTID +"']").closest(".coral-Select");

   $document.on("dialog-ready", function(e) {
        adjustLayoutHeight();

   var selectorAcc = "[name='./account']";

        
    // Get the list from the source
        $.getJSON("/bin/brightcove/accounts.json").done(function(data){
            //console.log(data);
            var accountss = data.accounts;

			accountss.forEach(function(value, index) {
              // Add each item

             //	$("<option>").appendTo(accountSelector._select).val(value.value).html(value.text);
             //trying to see if this atleast appends option to select dom element
                accountSelector.append( $("<option>")).val("value").html("text to be displayed");


            });

           /* for (var i in accountss) {
            	console.log("data array -- ", accountss[i].text);
                selectorAcc.accountss.add({
                    value: accountss[i].value,
                    content:{ innerHTML :accountss[i].text}
                });
            }*/

//	});
    });
});

})($, $(document));