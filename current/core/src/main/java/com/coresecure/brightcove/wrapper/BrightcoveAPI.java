/*

    Adobe AEM Brightcove Connector

    Copyright (C) 2017 Coresecure Inc.

    Authors:    Alessandro Bonfatti
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
package com.coresecure.brightcove.wrapper;

import com.coresecure.brightcove.wrapper.api.Cms;
import com.coresecure.brightcove.wrapper.objects.Account;
import com.coresecure.brightcove.wrapper.objects.Platform;
import com.coresecure.brightcove.wrapper.sling.ConfigurationGrabber;
import com.coresecure.brightcove.wrapper.sling.ConfigurationService;
import com.coresecure.brightcove.wrapper.sling.ServiceUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BrightcoveAPI {
    public Platform platform;
    public Account account;
    public Cms cms;
    private static final Logger LOGGER = LoggerFactory.getLogger(BrightcoveAPI.class);

    public BrightcoveAPI(String aClient_id, String aClient_secret, String aAccount_id) {
        LOGGER.debug("BrightcoveAPI Init aAccount_id " + aAccount_id);

        platform = new Platform();
        account = new Account(platform, aClient_id, aClient_secret, aAccount_id);
        cms = new Cms(account);
    }

    public BrightcoveAPI(String key) {
        LOGGER.debug("BrightcoveAPI Init key " + key);
        platform = new Platform();
        ConfigurationGrabber cg = ServiceUtil.getConfigurationGrabber();
        ConfigurationService brcService = cg.getConfigurationService(key);

        if (brcService.getProxy()!=null && brcService.getProxy().length()>0) {
            setProxy(brcService.getProxy());
        }
        account = new Account(platform, brcService.getClientID(), brcService.getClientSecret(), brcService.getAccountID());
        cms = new Cms(account);
    }

    public void setProxy(String proxy) {
        platform.setProxy(proxy);
    }
}
