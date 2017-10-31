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

/**
 *  Library for working with Brightcove Accounts in Widgets
 *
 */


var Brightcove = Brightcove || {};

Brightcove.Account = {};

/*
 Store original value as a custom property so that we only reset the values if the selection ACTUALLY changed. (Workaround to odd listener behaviour)

 - Called by 'loadcontent' listener
 */
Brightcove.Account.initValue = function (accountField) {

    var initialValue = accountField.getValue();

    console.log('initialValue', initialValue);
    if (!initialValue) {
        console.log('initialValue undefined... Reset all dependent fields');
        Brightcove.Account.resetDependentFields(accountField);
    }
    accountField.oldValue = initialValue;

};


/*
 Verify that the newValue != oldValue.

 - Called by 'selectionchanged' listener
 */
Brightcove.Account.updateValue = function (accountField, newValue) {


    var oldValue = accountField.oldValue;


    if (oldValue !== newValue) {


        Brightcove.Account.resetDependentFields(accountField);

        accountField.oldValue = newValue;

    } else {
        console.log('nothing new... ignore');
    }

};

/*
 Reset all fields which have been configured to depend on the value of the Account.
 */
Brightcove.Account.resetDependentFields = function (accountField) {
    var dialogObject = accountField.findParentByType('dialog'),
        dependentFields = dialogObject.find('brcAccountFieldName', './account');

    for (var i = 0; i < dependentFields.length; i++) {
        var field = dependentFields[i];
        try {
            console.log('Brightcove.Account.resetDependentFields', field);
            field.setValue('');
        } catch (e) {
            console.warn(e);
        }
    }
};

