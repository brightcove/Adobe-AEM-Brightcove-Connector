package com.coresecure.brightcove.wrapper.webservices;

import com.coresecure.brightcove.wrapper.BrightcoveAPI;
import com.coresecure.brightcove.wrapper.api.CmsAPI;
import com.coresecure.brightcove.wrapper.sling.ConfigurationGrabber;
import com.coresecure.brightcove.wrapper.sling.ConfigurationService;
import com.coresecure.brightcove.wrapper.sling.ServiceUtil;
import com.coresecure.brightcove.wrapper.utils.TextUtil;
import com.coresecure.brightcove.wrapper.utils.Constants;
import com.day.cq.dam.api.Asset;
import com.day.cq.wcm.api.Page;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.models.annotations.injectorspecific.OSGiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonArrayBuilder;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.servlet.ServletException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

@Service
@Component
@Properties(value = {
        @Property(name = "sling.servlet.extensions", value = {"json"}),
        @Property(name = "sling.servlet.paths", value = {"/bin/brightcove/getLocalVideoList"})
})

public class GetLocalAssetList extends SlingAllMethodsServlet {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetLocalAssetList.class);
    private transient ServiceUtil serviceUtil = null;

    Set<String> services;

    @Override
    protected void doGet(final SlingHttpServletRequest request,
                         final SlingHttpServletResponse response) throws ServletException,
            IOException {
                String action = request.getParameter("source");

                if (action != null && action.length() > 0) {
                    if ( action.equals("videos") ) {
                        getLocalBrightcoveVideosForDropdown(request, response);
                    } else if ( action.equals("playlists") ) {
                        getPlaylistsForDropdown(request, response);
                    } else if ( action.equals("players") ) {
                        getLocalPlayersForDropdown(request, response);
                    }
                }
                

    }

    public String getBrightcoveId(SlingHttpServletRequest request) {

        try {

            ConfigurationGrabber cg = ServiceUtil.getConfigurationGrabber();
            String brcAccountId = request.getParameter("account_id");
            
            if ( brcAccountId == null || brcAccountId.length() == 0 ) {
                brcAccountId = ServiceUtil.getAccountFromCookie(request);
            }
    
            if ( brcAccountId == null || brcAccountId.length() == 0 ) {
                services = cg.getAvailableServices(request);
    
                if ( services.size() > 0 ) {
                    brcAccountId = (String) services.toArray()[0];
                }
            }
    
            return brcAccountId;

        } catch (Exception e) {

            return null;

        }
        
    }


    public void getLocalBrightcoveVideosForDropdown(final SlingHttpServletRequest request,
                    final SlingHttpServletResponse response) throws ServletException,
            IOException {
        PrintWriter outWriter = response.getWriter();
        response.setContentType("application/json");

        try {
            String brcAccountId = getBrightcoveId(request);

            LOGGER.debug("brcAccountId:", brcAccountId);
            
            ConfigurationGrabber cg = ServiceUtil.getConfigurationGrabber();
            ConfigurationService brcService = cg.getConfigurationService(brcAccountId);
            String assetsPath = brcService.getAssetIntegrationPath() + "/" + brcAccountId;

            // now get the assets located in this folder and add them to the JSON array
            ResourceResolver resolver = request.getResourceResolver();
            Resource folderResource = resolver.getResource(assetsPath);

            JsonObjectBuilder bo = Json.createObjectBuilder();

            if ( folderResource != null ) {

                Iterator<Resource> children = folderResource.listChildren();
                JsonArrayBuilder items = Json.createArrayBuilder();

                while (children.hasNext()) {

                    Resource assetResource = children.next();

                    if (assetResource.getResourceType().equals("dam:Asset")) {

                        Asset videoAsset = assetResource.adaptTo(Asset.class);

                        ValueMap metadataValues = assetResource.getChild("jcr:content/metadata").adaptTo(ValueMap.class);

                        String title = videoAsset.getMetadataValue("dc:title");

                        if (title == null || title.length() == 0) {
                            title = assetResource.getName();
                        }

                        items.add(
                            Json.createObjectBuilder()
                                .add("path", assetResource.getPath())
                                .add("title", title)
                                .add("id", metadataValues.get("brc_id").toString())
                                .build()
                        );
                    }
                    

                }

                bo.add("items", items);

            }

            outWriter.write(bo.build().toString());

        } catch (Exception e) {
            LOGGER.error("JSONException", e);
            outWriter.write("{\"accounts\":[],\"error\":\"" + e.getMessage() + "\"}");
        }

    }

    public void getPlaylistsForDropdown(final SlingHttpServletRequest request,
                    final SlingHttpServletResponse response) throws ServletException,
            IOException {
        PrintWriter outWriter = response.getWriter();
        response.setContentType("application/json");

        try {
            String brcAccountId = getBrightcoveId(request);
            LOGGER.debug("brcAccountId:", brcAccountId);
            
            serviceUtil = new ServiceUtil(brcAccountId);

            outWriter.write(serviceUtil.getPlaylists(request.getParameter(Constants.QUERY), 0, 100, false, false).toString());

        } catch (Exception e) {
            LOGGER.error("JSONException", e);
            outWriter.write("{\"accounts\":[],\"error\":\"" + e.getMessage() + "\"}");
        }

    }

    public void getLocalPlayersForDropdown(final SlingHttpServletRequest request,
                    final SlingHttpServletResponse response) throws ServletException,
            IOException {
        PrintWriter outWriter = response.getWriter();
        response.setContentType("application/json");

        try {
            String brcAccountId = getBrightcoveId(request);
            LOGGER.debug("brcAccountId:", brcAccountId);

            ConfigurationGrabber cg = ServiceUtil.getConfigurationGrabber();
            ConfigurationService brcService = cg.getConfigurationService(brcAccountId);
            
            //serviceUtil = new ServiceUtil(brcAccountId);
            //outWriter.write(serviceUtil.getPlayers().toString());
            
            String playersPath = brcService.getPlayersLoc();
            ResourceResolver resourceResolver = request.getResourceResolver();
            Resource res = resourceResolver.resolve(playersPath);
            Iterator<Resource> playersItr = res.listChildren();
            
            JsonObjectBuilder bo = Json.createObjectBuilder();

            if (TextUtil.notEmpty(brcAccountId)) {
                JsonArrayBuilder items = Json.createArrayBuilder();
                while (playersItr.hasNext()) {
                    Page playerRes = playersItr.next().adaptTo(Page.class);
                    if (playerRes != null && "brightcove/components/page/brightcoveplayer".equals(playerRes.getContentResource().getResourceType())) {
                        String path = playerRes.getPath();
                        String title = playerRes.getTitle();
                        String account = playerRes.getProperties().get("account", "");
                        if (TextUtil.notEmpty(account) && account.equals(brcAccountId)) {
                            items.add(
                                Json.createObjectBuilder()
                                    .add("id", path)
                                    .add("name", title)
                                    .add("title", title)
                                    .build()
                            );
                        }
                    }
                }

                bo.add("items", items);
                outWriter.write(bo.build().toString());
            }

        } catch (Exception e) {
            LOGGER.error("JSONException", e);
            outWriter.write("{\"accounts\":[],\"error\":\"" + e.getMessage() + "\"}");
        }

    }

}