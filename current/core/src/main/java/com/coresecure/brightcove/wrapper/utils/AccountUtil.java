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
package com.coresecure.brightcove.wrapper.utils;

import com.coresecure.brightcove.wrapper.sling.ConfigurationGrabber;
import com.coresecure.brightcove.wrapper.sling.ConfigurationService;
import com.coresecure.brightcove.wrapper.sling.ServiceUtil;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.api.SlingHttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class AccountUtil
{
    private static final Logger LOGGER = LoggerFactory.getLogger(AccountUtil.class);

    public static String getSelectedAccount(SlingHttpServletRequest req)
    {
        String accountParam = req.getParameter("account_id");
        String selectedaccount = accountParam != null && !accountParam.isEmpty() ? accountParam : ServiceUtil.getAccountFromCookie(req);
        return selectedaccount;
    }

    public static Set<String> getSelectedServices(SlingHttpServletRequest req) {
        ConfigurationGrabber cg = ServiceUtil.getConfigurationGrabber();    //GETCONFIG SERVICE
        String selectedaccount = AccountUtil.getSelectedAccount(req);       //GET CURRENT ACCOUNT
        Set<String> services = new TreeSet<String>();
        if (selectedaccount != null && !selectedaccount.isEmpty())
        {
            ConfigurationService cs = cg.getConfigurationService(selectedaccount); //GET CONFIG FOR SELECTED ACCOUNT
            if (cs != null && isAuthorized(req,cs))
            {
                services.add(selectedaccount);          //INITIALIZE SERVICES FOR SPECIFIED ACCOUNT
            }
            else
            {
                services = cg.getAvailableServices(req); //ELSE GET AVAILABLE SERVICES
            }
        }
        else
        {
            services = cg.getAvailableServices(req);    //ELSE GET AVAILABLE SERVICES
        }
        return services;
    }

    public static boolean isAuthorized(SlingHttpServletRequest req, ConfigurationService service) {
        boolean is_authorized = false;
        Session session = req.getResourceResolver().adaptTo(Session.class); //GET CURRENT SESSION
        UserManager userManager = req.getResourceResolver().adaptTo(UserManager.class);
        List<String> allowedGroups = service.getAllowedGroupsList();
        try {
            Authorizable auth = userManager.getAuthorizable(session.getUserID());
            if (auth != null) {
                Iterator<Group> groups = auth.memberOf();
                while (groups.hasNext() && !is_authorized) {
                    Group group = groups.next();
                    if (allowedGroups.contains(group.getID())) is_authorized = true; //<-Authorization
                }
            }
        } catch (RepositoryException re) {
            LOGGER.error("executeRequest", re);
        }
        return is_authorized;
    }

}
