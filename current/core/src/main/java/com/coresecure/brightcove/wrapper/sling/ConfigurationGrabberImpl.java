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
package com.coresecure.brightcove.wrapper.sling;


import org.apache.felix.scr.annotations.*;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.api.SlingHttpServletRequest;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import java.util.*;

@Component(immediate = true)
@Service
@References({
        @Reference(cardinality = ReferenceCardinality.OPTIONAL_MULTIPLE,
                policy = ReferencePolicy.DYNAMIC,
                referenceInterface = ConfigurationService.class,
                name = "ConfigurationService")
})

public class ConfigurationGrabberImpl implements ConfigurationGrabber {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigurationGrabberImpl.class);

    private static String KEY = "key";

    private final Map<String, ConfigurationService> myConfigurationServices = new HashMap<String, ConfigurationService>();
    private ComponentContext componentContext;

    public ConfigurationService getConfigurationService(String key) {
        return myConfigurationServices.get(key);
    }

    public Set<String> getAvailableServices() {
        return myConfigurationServices.keySet();
    }

    public Set<String> getAvailableServices(SlingHttpServletRequest request) {
        Set<String> result = new HashSet<String>();
        boolean is_authorized = false;
        try {
            Session session = request.getResourceResolver().adaptTo(Session.class);
            UserManager userManager = request.getResourceResolver().adaptTo(UserManager.class);
                /* to get the current user */
            Authorizable auth = userManager.getAuthorizable(session.getUserID());
            if (auth != null) {
                List<String> memberOf = new ArrayList<String>();
                Iterator<Group> groups = auth.memberOf();
                while (groups.hasNext() && !is_authorized) {
                    Group group = groups.next();
                    memberOf.add(group.getID());
                }
                int i = 0;
                for (String account : getAvailableServices()) {
                    ConfigurationService cs = getConfigurationService(account);
                    List<String> allowedGroups = new ArrayList<String>(cs.getAllowedGroupsList());
                    allowedGroups.retainAll(memberOf);
                    if (allowedGroups.size() > 0) {
                        result.add(account);
                        i++;
                    }
                }
            }

        } catch (RepositoryException e) {
            LOGGER.error("RepositoryException",e);
        }
        return result;
    }

    protected void bindConfigurationService(ServiceReference ref) {
        synchronized (this.myConfigurationServices) {
            String customKey = (String) ref.getProperty(KEY);
            ConfigurationService operation = (ConfigurationService) this.componentContext.locateService("ConfigurationService", ref);
            //Or you can use
            //MyCustomServices operation = ref.getProperty("service.pid");
            if (operation != null) {
                myConfigurationServices.put(customKey, operation);
            }
        }
    }

    protected void unbindConfigurationService(ServiceReference ref) {
        synchronized (this.myConfigurationServices) {
            String customKey = (String) ref.getProperty(KEY);
            myConfigurationServices.remove(customKey);
        }
    }

    @Activate
    protected void activate(ComponentContext ctx) {
        this.componentContext = ctx;
    }
}

