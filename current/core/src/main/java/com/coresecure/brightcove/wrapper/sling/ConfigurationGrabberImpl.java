/*

    Adobe AEM Brightcove Connector

    Copyright (C) 2018 Coresecure Inc.

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

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;

import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.ResourceResolver;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component(service = ConfigurationGrabber.class, immediate = true, reference = {
        @Reference(name = "configurationService", service = com.coresecure.brightcove.wrapper.sling.ConfigurationService.class, policy = ReferencePolicy.DYNAMIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.MULTIPLE, bind = "bindConfigurationService", unbind = "unbindConfigurationService")
})
public class ConfigurationGrabberImpl implements ConfigurationGrabber {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigurationGrabberImpl.class);

    private static String KEY = "key";

    private final Map<String, ConfigurationService> myConfigurationServices = new ConcurrentHashMap<String, ConfigurationService>();
    private ComponentContext componentContext;

    protected final void bindConfigurationService(final ConfigurationService config,
            final Map<Object, Object> props) {

        LOGGER.info("ConfigurationService bind() called");
        if (config != null) {
            LOGGER.info("I am here");
        } else {
            LOGGER.info("null pointer");
        }
        LOGGER.info("accountid: " + config.getAccountID());
        LOGGER.info("Config: " + config);
        myConfigurationServices.put(config.getAccountID(), config);
    }

    protected final void unbindConfigurationService(final ConfigurationService config,
            final Map<Object, Object> props) {
        myConfigurationServices.remove(config.getAccountID());
    }

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

            ResourceResolver resourceResolver = request.getResourceResolver();

            Session session = resourceResolver.adaptTo(Session.class);
            UserManager userManager = resourceResolver.adaptTo(UserManager.class);
            LOGGER.info("getAvailableServices");
            if (session == null || userManager == null) {
                LOGGER.info("Session or userManager null");
                return null;
            }
            LOGGER.info("session not null and user manager not null");
            Authorizable auth = userManager.getAuthorizable(session.getUserID());
            if (auth == null) {
                LOGGER.info("auth null");
                return null;
            }
            LOGGER.info("auth not null");
            List<String> memberOf = new ArrayList<String>();
            Iterator<Group> groups = auth.memberOf();
            while (groups.hasNext() && !is_authorized) {
                Group group = groups.next();
                memberOf.add(group.getID());
            }
            LOGGER.info("memberof: " + memberOf.toString());
            int i = 0;
            LOGGER.info("groups work");
            LOGGER.info("key set: " + myConfigurationServices.keySet().toString());
            for (String account : getAvailableServices()) {
                ConfigurationService cs = getConfigurationService(account);
                LOGGER.info("allowedgroupslist" + cs.getAllowedGroupsList());
                List<String> allowedGroups = new ArrayList<String>(cs.getAllowedGroupsList());
                LOGGER.info("allowedgroups: " + allowedGroups.toString());
                allowedGroups.retainAll(memberOf);
                if (allowedGroups.size() > 0) {
                    result.add(account);
                    i++;
                }
            }
            LOGGER.info("result size: " + result.size());
        } catch (Exception e) {
            LOGGER.error("RepositoryException", e);
        }

        LOGGER.info(result.toString());
        return result;
    }

    @Activate
    protected void activate(ComponentContext ctx) {
        this.componentContext = ctx;
        LOGGER.info("Brightcove ConfigurationGrabber is active. ");
    }
}
