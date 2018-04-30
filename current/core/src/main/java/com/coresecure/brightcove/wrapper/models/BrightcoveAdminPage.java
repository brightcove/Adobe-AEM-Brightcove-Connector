package com.coresecure.brightcove.wrapper.models;


import com.coresecure.brightcove.wrapper.sling.ConfigurationGrabber;
import com.coresecure.brightcove.wrapper.sling.ConfigurationService;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.Source;
import org.apache.sling.models.annotations.injectorspecific.OSGiService;
import org.apache.sling.models.annotations.injectorspecific.Self;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import com.coresecure.brightcove.wrapper.sling.ServiceUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Model(adaptables=SlingHttpServletRequest.class)
public class BrightcoveAdminPage {

    protected final static Logger LOGGER = LoggerFactory.getLogger(BrightcoveAdminPage.class);
    public static final String FAVICONPATH = "/etc/designs/cs/brightcove/favicon.ico";

    @Self
    private SlingHttpServletRequest slingHttpServletRequest;

    @Inject
    @Source("sling-object")
    private ResourceResolver resourceResolver;

    @OSGiService
    ConfigurationGrabber cg;

    String defaultAccount = "";
    String cookieAccount = "";
    String selectedAccount = "";
    String previewPlayerLoc = "";
    String previewPlayerListLoc = "";
    String selectedAccountAlias = "";
    Set<String> services;
    List<ConfigurationService> configurationServices = new ArrayList<ConfigurationService>();

    public String getDefaultAccount()
    {
        return defaultAccount;
    }
    public String getCookieAccount()
    {
        return cookieAccount;
    }

    public String getSelectedAccountAlias()
    {
        return selectedAccountAlias;
    }
    public String getPreviewPlayerLoc()
    {
        return previewPlayerLoc;
    }
    public String getPreviewPlayerListLoc()
    {
        return previewPlayerListLoc;
    }

    public String getSelectedAccount() {
        return selectedAccount;
    }

    public List<ConfigurationService> getConfigurationServices() { return configurationServices;}


    @PostConstruct
    protected void init()
    {
        services = cg.getAvailableServices(slingHttpServletRequest);
        ConfigurationService cs;
        LOGGER.debug("services {}", services);

        if (services.size() > 0) {
            defaultAccount = (String) services.toArray()[0];                                        //Set first account as the default
            cookieAccount = ServiceUtil.getAccountFromCookie(slingHttpServletRequest);              //If old session holds account in cookie, set that as default
            selectedAccount = (cookieAccount.trim().isEmpty()) ? defaultAccount : cookieAccount;    //Only if cookie acct is not empty - else default

            cs = cg.getConfigurationService(selectedAccount) != null ? cg.getConfigurationService(selectedAccount) : cg.getConfigurationService(defaultAccount);
            try {
                LOGGER.debug("config service: " + cs.getAccountAlias());
                //Preview location
                previewPlayerLoc = String.format("https://players.brightcove.net/%s/%s_default/index.html?videoId=", cs.getAccountID(), cs.getDefVideoPlayerID());
                previewPlayerListLoc = String.format("https://players.brightcove.net/%s/%s_default/index.html?playlistId=", cs.getAccountID(), cs.getDefPlaylistPlayerID());
                selectedAccountAlias = cs.getAccountAlias();
                //DEBUGGING
                LOGGER.debug(previewPlayerLoc);
                LOGGER.debug(previewPlayerListLoc);
                LOGGER.debug(cs.getAccountID());
                LOGGER.debug(selectedAccountAlias);
            } catch (Exception e) {
                //OPTIONS RENDER FUNCTION
                LOGGER.error(e.getClass().getName(),e);
            }
            for (String service : services) {
                configurationServices.add(cg.getConfigurationService(service));
            }
        }
    }

}