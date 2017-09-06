package com.coresecure.brightcove.wrapper.sling;


import org.apache.felix.scr.annotations.*;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.api.SlingHttpServletRequest;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.ComponentContext;

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

