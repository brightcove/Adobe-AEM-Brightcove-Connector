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
var Brightcove = Brightcove || {};

Brightcove.ComboBox = CQ.Ext.extend(CQ.Ext.form.ComboBox, {

    /**
     * @cfg {String} url
     * The URL where the search request is sent to (defaults to "/content.search.json").
     */

    /**
     * @cfg {Object} store
     * @deprecated Use {@link #storeConfig} instead
     */

    /**
     * @cfg {Object} storeConfig
     * <p>The configuration of the store the SearchField is bound to. To simply
     * overwrite the URL of the store's proxy use the {@link #url} config option.</p>
     * <p>The default store's reader consumes a JSON of the following format:</p>
     * <pre><code>
     {
         "hits":[
             {
             "name": "sample",
             "path": "/content/sample",
             "excerpt": "",
             "title": "Sample Page"
             }
          ],
          "results":1
     }
     </code></pre>
     */
    storeConfig: null,

    constructor: function (config) {

        var template = '<tpl for=".">' +
            '<div class="search-item" qtip="{id}">' +
            '<div class="search-thumb"' +
            ' style="background-image:url({[values.thumbnailURL ? values.thumbnailURL : "/etc/designs/cs/brightcove/shared/img/noThumbnailP.png"]});">' +
            '</div>' +
            '<div class="search-text-wrapper">' +
            '<div class="search-title">{name}</div>' +
            '<div class="search-excerpt">{id}</div>' +
            '</div>' +
            '<div class="search-separator"></div>' +
            '</div>' +
            '</tpl>';

        config = CQ.Util.applyDefaults(config, {
            "brcAccountFieldName": "/.acccount", //Dependent field
            "width": 300,
            "autoSelect": true,
            "mode": "remote",
            "pageSize": 20,
            "minChars": 0,
            "typeAhead": false,
            "typeAheadDelay": 100,
            "validationEvent": false,
            "validateOnBlur": false,
            "displayField": "name",
            "valueField": "id",
            "triggerAction": 'query',
            "emptyText": CQ.I18n.getMessage("Enter search query"),
            "loadingText": CQ.I18n.getMessage("Searching..."),
            "tpl": new CQ.Ext.XTemplate(template),
            "itemSelector": "div.search-item"
        });


        var storePath = config.storePath;
        //console.log(storePath);

        var storeConfig = CQ.Util.applyDefaults(config.storeConfig, {
            "proxy": new CQ.Ext.data.HttpProxy({
                "url": storePath,
                "method": "GET"
            }),
            "baseParams": {
                "_charset_": "utf-8"
            },
            "reader": new CQ.Ext.data.JsonReader({
                "id": "id",
                "root": "items",
                "totalProperty": "totals",
                "fields": ["id", "name", "thumbnailURL"]
            })
        });

        config.store = new CQ.Ext.data.Store(storeConfig);

        Brightcove.ComboBox.superclass.constructor.call(this, config);
    },
    initComponent: function () {
        Brightcove.ComboBox.superclass.initComponent.call(this);

        //this.on('loadcontent', ...);
    },
    asyncSetDisplayValue: function (v) {


        var combobox = this,
            dialogObject = combobox.findParentByType('dialog'),
            brcAccountFieldName = combobox.initialConfig['brcAccountFieldName'],
            accountField = dialogObject.getField(brcAccountFieldName),
            value = CQ.Ext.isEmpty(v) ? '' : v;

        combobox.store.baseParams[this.queryParam] = value;
        combobox.store.baseParams['isID'] = !CQ.Ext.isEmpty(value);
        combobox.store.baseParams['account_id'] = accountField.getValue();

        //console.log('asyncSetDisplayValue', combobox, v);

        var success = combobox.store.load({
            params: combobox.getParams(value),
            callback: function () {
                combobox.setDisplayValue(combobox.value, false);
                if (value != combobox.value) {
                    combobox.setValue(value);
                }
            }
        });
        if (!success) {
            // Load was cancelled, so we'll just have to make do with the valueField:
            combobox.setDisplayValue(combobox.value, false);

        }
        combobox.store.baseParams['isID'] = false;

    }


});

CQ.Ext.reg("brc_combobox", Brightcove.ComboBox);

