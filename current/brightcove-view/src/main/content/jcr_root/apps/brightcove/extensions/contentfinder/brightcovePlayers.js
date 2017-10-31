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
{
    "tabTip": CQ.I18n.getMessage("Brightcove Players"),
    "id": "cfTab-Brightcove-Players",
    "iconCls": "cq-cft-tab-icon brightcoveplayers",
    "xtype": "contentfindertab",
    "ranking": 30,
    "allowedPaths": [
                     "/content/*",
                     "/etc/scaffolding/*",
                     "/etc/workflow/packages/*"
                 ],
    "items": [
       CQ.wcm.ContentFinderTab.getQueryBoxConfig({
            "id": "cfTab-Brightcove-Players-QueryBox",
            "items": [
                CQ.wcm.ContentFinderTab.getSuggestFieldConfig({"url":  CQ.shared.HTTP.getContextPath() +"/bin/brightcove/suggestions.json?type=videos"}),
                {
                        xtype: "spacer",
                        height: 5
                },
                {
                    xtype: "combo",
                    id: "cfTab-Brightcove-Players-account",
                    name:"account_id",
                    triggerAction:"all",
                    lazyInit:true,
                    store: {
                        "url": CQ.shared.HTTP.getContextPath() +'/bin/brightcove/accounts.json',
                        "reader": new CQ.Ext.data.JsonReader({
                            "root": "accounts",
                            "fields": [
                                "text", "value"
                            ]
                        })
                    },
                	valueField: 'value',
   					displayField: 'text',
                    editable: false,
                    emptyText: CQ.I18n.getMessage("Filter by account"),
                	style:"",
                	listeners: {
                    	select: function (combo, record, index ) {
                    		var store = CQ.Ext.getCmp("cfTab-Brightcove-Players").items.get(0);
                            document.cookie = "brc_act="+record.data.value+"; expires=0; path=/";
                            var contentfinder_element = CQ.Ext.getCmp("cfTab-Brightcove-Players");
                    		contentfinder_element.submitQueryBox(store);
                    	}
                    }
                }
            ]
        }),
        CQ.wcm.ContentFinderTab.getResultsBoxConfig({
            "id":"cfTab-Brightcove-resultBox",
            "itemsDDGroups": ["brightcove_player"],
            "itemsDDNewParagraph": {
                "path": "brightcove/components/content/brightcovevideo",
                "propertyName": "./playerPath"
            },
            "noRefreshButton": true,
            "items": {
                "tpl":
                    '<tpl for=".">' +
                '<div class="cq-cft-search-item" title="{thumbnailURL}" ondblclick="window.location= CQ.shared.HTTP.getContextPath() +\'/brightcove/admin\';">' +
                                    '<div class="cq-cft-search-thumb-top"' +
                                    ' style="background-image:url(\'/etc/designs/cs/brightcove/shared/img/noThumbnailP.png\');"></div>' +
                                         '<div class="cq-cft-search-text-wrapper">' +
                                            '<div class="cq-cft-search-title"><p class="cq-cft-search-title">{name}</p><p>{path}</p></div>' +
                                        '</div>' +
                                    '<div class="cq-cft-search-separator"></div>' +
                            '</div>' +
                    '</tpl>',
                "itemSelector": CQ.wcm.ContentFinderTab.DETAILS_ITEMSELECTOR
			},
            "tbar": [
                CQ.wcm.ContentFinderTab.REFRESH_BUTTON,"->",
                {
                    text: "Export CSV",
                    handler: function() {
                        var url= CQ.shared.HTTP.getContextPath() +'/bin/brightcove/api?a=3&query='+$("#cfTab-Brightcove-QueryBox input[name=query]").val();
                        window.open(url, 'Download');

                   }
                }
            ]
        },{
            "url": "/apps/brightcove/components/tools/players.html"
        },{
            "autoLoad":false,
            "reader": new CQ.Ext.data.JsonReader({
                "root": "items",
                "fields": [
                    {name:"name"},
                    {name:"path", mapping:"path"},
                    {name:"thumbnailURL", type:"string", mapping:"thumbnailURL"}
                ],
                "id": "path"

            })

        })
    ]

}