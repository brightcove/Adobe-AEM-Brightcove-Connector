<%--
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
--%>
<%@ page import="com.coresecure.brightcove.wrapper.sling.ConfigurationGrabber,
                 com.coresecure.brightcove.wrapper.sling.ConfigurationService,
                 com.coresecure.brightcove.wrapper.sling.ServiceUtil,
                 com.coresecure.brightcove.wrapper.utils.TextUtil,
                 java.util.UUID" %>


<%@include file="/apps/brightcove/components/shared/global.jsp" %>
<%

    String componentID = UUID.randomUUID().toString().replaceAll("-", "");

    String videoID = properties.get("videoPlayer", "").trim();
    String playlistID = properties.get("videoPlayerPL", "").trim();

    String account = properties.get("account", "").trim();
    String playerPath = properties.get("playerPath", "").trim();
    String playerID ="";
    String playerKey = "";
    String playerDataEmbed = "";

    String containerID = properties.get("containerID", "");
    String containerClass = properties.get("containerClass", "");


    // Default Values

    String align = "center";
    String width = "";
    String height = "";
    boolean hasSize = false;

    boolean ignoreComponentProperties = false;


    //fallback to default
    if (TextUtil.notEmpty(account)) {
        ConfigurationGrabber cg = ServiceUtil.getConfigurationGrabber();
        ConfigurationService cs = cg.getConfigurationService(account);
        if (cs != null) {
            playerID = cs.getDefVideoPlayerID();
            playerDataEmbed = cs.getDefVideoPlayerDataEmbedded();
            playerKey= cs.getDefVideoPlayerKey();
        }
    }

    playerID = properties.get("playerID",playerID).trim();
    playerKey = properties.get("playerKey",playerKey).trim();;
    playerDataEmbed = playerDataEmbed.isEmpty() ? "default" : playerDataEmbed;


    // Load Player Configuration

    if (!playerPath.isEmpty()) {

        Resource playerPageResource = resourceResolver.resolve(playerPath);

        if (playerPageResource != null) {

            Page playerPage = playerPageResource.adaptTo(Page.class);

            if (playerPage != null) {

                ValueMap playerProperties = playerPage.getProperties();

                playerID = playerProperties.get("playerID", playerID);
                playerKey = playerProperties.get("playerKey", playerKey);
                playerDataEmbed = playerProperties.get("data_embedded", playerDataEmbed);


                align = playerProperties.get("align", align);
                width = playerProperties.get("width", width);
                height = playerProperties.get("height", height);

                //append the class to the container wrap
                containerClass += " " + playerProperties.get("containerClass", "");

                ignoreComponentProperties = playerProperties.get("ignoreComponentProperties", ignoreComponentProperties);
            }

        }

    }

    // Override with local component properties IF enabled

    if (!ignoreComponentProperties) {

        align = properties.get("align", align);

        //we must override BOTH width and height to prevent one being set on Player Page and other set in component.
        if (properties.containsKey("width") || properties.containsKey("height")) {
            width = properties.get("width", width);
            height = properties.get("height", height);
        }
    }

    // Adjust size accordingly
    if (TextUtil.notEmpty(width) || TextUtil.notEmpty(height)) {
        hasSize = true;
        if (TextUtil.isEmpty(width)) {
            width = String.valueOf((480 * Integer.parseInt(height, 10)) / 270);
        } else if (TextUtil.isEmpty(height)) {
            height = String.valueOf((270 * Integer.parseInt(width, 10)) / 480);
        }
    }





    // Update Page Context
    pageContext.setAttribute("brc_account", account, PageContext.REQUEST_SCOPE);
    pageContext.setAttribute("brc_videoID", videoID, PageContext.REQUEST_SCOPE);
    pageContext.setAttribute("brc_playlistID", playlistID, PageContext.REQUEST_SCOPE);

    pageContext.setAttribute("brc_playerPath", playerPath, PageContext.REQUEST_SCOPE);
    pageContext.setAttribute("brc_playerID", playerID, PageContext.REQUEST_SCOPE);
    pageContext.setAttribute("brc_playerKey", playerKey, PageContext.REQUEST_SCOPE);
    pageContext.setAttribute("brc_playerDataEmbed", playerDataEmbed, PageContext.REQUEST_SCOPE);

    pageContext.setAttribute("brc_ignoreComponentProperties",ignoreComponentProperties, PageContext.REQUEST_SCOPE);

    pageContext.setAttribute("brc_align", align, PageContext.REQUEST_SCOPE);
    pageContext.setAttribute("brc_width", width, PageContext.REQUEST_SCOPE);
    pageContext.setAttribute("brc_height", height, PageContext.REQUEST_SCOPE);
    pageContext.setAttribute("brc_hasSize", hasSize, PageContext.REQUEST_SCOPE);


    pageContext.setAttribute("brc_componentID", componentID, PageContext.REQUEST_SCOPE);

    //Component Container
    pageContext.setAttribute("brc_containerID", containerID.trim());
    pageContext.setAttribute("brc_containerClass", containerClass.trim());

%>