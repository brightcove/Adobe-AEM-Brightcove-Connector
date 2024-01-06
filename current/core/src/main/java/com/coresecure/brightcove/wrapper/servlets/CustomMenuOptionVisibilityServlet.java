package com.coresecure.brightcove.wrapper.servlets;


import static org.osgi.framework.Constants.SERVICE_DESCRIPTION;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.servlet.Servlet;
import javax.servlet.ServletException;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.entity.ContentType;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.servlets.ServletResolverConstants;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adobe.granite.asset.api.Asset;
import com.adobe.granite.asset.api.AssetManager;
import com.coresecure.brightcove.wrapper.sling.ConfigurationGrabber;
import com.coresecure.brightcove.wrapper.sling.ConfigurationService;
import com.coresecure.brightcove.wrapper.sling.ServiceUtil;
import com.day.cq.dam.api.DamConstants;
import com.day.crx.JcrConstants;
import com.drew.lang.annotations.NotNull;
import com.google.gson.JsonObject;

/**
 * This servlet will be used to check the visibility of the custom menu option added in the Assets section.
 * The servlet returns a boolean status of whether menu option should be visible or not.
 * If it returns true, then menu option will be visible and if it returns false, then menu option will be hidden.
 */
@Component(immediate = true, service = Servlet.class, name = "Brightcove - Custom Menu Option Visibility Servlet",
        property = {
                SERVICE_DESCRIPTION + CustomMenuOptionVisibilityServlet.SERVLET_SERVICE_DESCRIPTION,
                ServletResolverConstants.SLING_SERVLET_PATHS + "=/bin/brightcove/custom-menu-option-visibility"
        })
public class CustomMenuOptionVisibilityServlet extends SlingSafeMethodsServlet {

    @Reference
    private ResourceResolverFactory resourceResolverFactory;

    public static final String SERVLET_SERVICE_DESCRIPTION = "=Brightcove - Custom Menu Option Visibility Servlet";
    private Map<String, String> paths = null;
    private static final Logger LOG = LoggerFactory.getLogger(CustomMenuOptionVisibilityServlet.class);
    private static final String SERVICE_ACCOUNT_IDENTIFIER = "brightcoveWrite";
    private String brightcoveAssetId;

    /**
     * Go get method to  provide visibility option.
     *
     * @param request  object.
     * @param response object.
     * @throws IOException exception.
     */
    @Override
    protected void doGet(@NotNull SlingHttpServletRequest request, @NotNull SlingHttpServletResponse response) throws ServletException, IOException {

        LOG.debug("Inside doGet method of CustomMenuOptionVisibilityServlet class");
        final String SHOW_REQUEST_FOR_VIDEO_SYNC_MENU_OPTION = "showRequestForVideoSyncMenuOption";
        final String SHOW_REQUEST_FOR_ASSET_DELETION_MENU_OPTION = "showRequestForAssetDeletionMenuOption";
        final String SHOW_EDIT_MENU_OPTION = "showEditMenuOption";
        String paths = request.getParameter("paths");
        PrintWriter out = response.getWriter();
        response.setStatus(200);
        response.setContentType(ContentType.APPLICATION_JSON.getMimeType());
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty(SHOW_REQUEST_FOR_VIDEO_SYNC_MENU_OPTION, false);
        jsonObject.addProperty(SHOW_REQUEST_FOR_ASSET_DELETION_MENU_OPTION, false);

        if (StringUtils.isBlank(paths)) {
            LOG.error("Invalid Request");
            response.setStatus(400);
            out.print(jsonObject);
            return;
        }

        boolean isValidAsset = checkValidBrightcovePath(paths);

        jsonObject.addProperty(SHOW_REQUEST_FOR_VIDEO_SYNC_MENU_OPTION, isValidAsset);
        jsonObject.addProperty(SHOW_REQUEST_FOR_ASSET_DELETION_MENU_OPTION, isValidAsset);

        out.print(jsonObject);
    }

    private boolean checkValidBrightcovePath(String assetPaths) {

        String[] assetURLs = assetPaths.split(Pattern.quote("|"));
        ResourceResolver rr = null;
        boolean isValid = false;

        try {
            // grab a resource resolver to pass to all the activation methods
            final Map<String, Object> authInfo = Collections.singletonMap(
                    ResourceResolverFactory.SUBSERVICE,
                    (Object) SERVICE_ACCOUNT_IDENTIFIER);

            // Get the Service resource resolver
            rr = resourceResolverFactory.getServiceResourceResolver(authInfo);

            // grab all the configured services
            ConfigurationGrabber cg = ServiceUtil.getConfigurationGrabber();
            Set<String> services = cg.getAvailableServices();
            
            // set up a variable to store the paths
            paths = new LinkedHashMap<>();
            
            // for each service, check to see the integration path
            for (String service : services) {

                // get the service from that account ID
                ConfigurationService brcService = cg.getConfigurationService(service);

                // add the account ID to a HashMap with a key of the path
                paths.put(brcService.getAssetIntegrationPath(), brcService.getAccountID());

            }

            // get all the account IDs of the integration paths
            Set<String> keys = paths.keySet();

            // check against the paths for each ConfigurationService
            for (String key : keys) {

                for (String asset : assetURLs) {
                    // check if this asset lives underneath a Brightcove managed folder
                    if (asset.contains(key)) {
                        return true;
                    }
                }
            }
        } catch (LoginException e) {
            // there is some issue with the system user used
            LOG.error("There was an error using the Brightcove system user.");
        } catch (Exception e) {
            // a general error
            LOG.error("Error when handling the Brightcove buttons: {}", e.getMessage());
        } finally {
            if (rr != null && rr.isLive()) {
                rr.close();
            }
        }

        return isValid;
    }
}