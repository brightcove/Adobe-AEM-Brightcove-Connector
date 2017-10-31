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
<%@ page contentType="text/html"
         pageEncoding="utf-8"
         import="com.coresecure.brightcove.wrapper.sling.ConfigurationGrabber,
                 com.coresecure.brightcove.wrapper.sling.ConfigurationService,
                 com.coresecure.brightcove.wrapper.sling.ServiceUtil,
                 com.coresecure.brightcove.wrapper.utils.TextUtil" %>
<%@ page import="com.day.text.Text" %>

<%@include file="/apps/brightcove/components/shared/global.jsp" %>

<%

    /*
    TODO: separate "HTML5" and "Legacy" players into separate configuration pages so that only relevant players will appear as options when configuring a component.
     */


    String segmentPath = Text.getRelativeParent(resource.getPath(), 1);

    String title = properties.get("jcr:title", Text.getName(segmentPath));
    String description = properties.get("jcr:description", "");

    String dialogPath = "";
    if (editContext != null && editContext.getComponent() != null) {
        dialogPath = editContext.getComponent().getDialogPath();
    }


    // Player Settings

    String account = properties.get("account", "").trim();
    String playerID = properties.get("playerID", "").trim();
    String playerKey = properties.get("playerKey", "").trim();

    String playerDataEmbed = properties.get("data_embedded", "default");


    // Dimensions

    String width = properties.get("width", "480");
    String height = properties.get("width", "270");


    // Adjust size accordingly
    if (TextUtil.notEmpty(width) || TextUtil.notEmpty(height)) {
        if (TextUtil.isEmpty(width)) {
            width = String.valueOf((480 * Integer.parseInt(height, 10)) / 270);
        } else if (TextUtil.isEmpty(height)) {
            height = String.valueOf((270 * Integer.parseInt(width, 10)) / 480);
        }
    }


    //fallback to default
    if (TextUtil.isEmpty(playerID) && TextUtil.notEmpty(account)) {
        ConfigurationGrabber cg = ServiceUtil.getConfigurationGrabber();
        ConfigurationService cs = cg.getConfigurationService(account);
        if (cs != null) {
            playerID = cs.getDefVideoPlayerID();
            playerDataEmbed = cs.getDefVideoPlayerDataEmbedded();
        }
    }

    ValueMap playerProperties = currentPage.getProperties();

    if (playerProperties.containsKey("width") && playerProperties.containsKey("height")) {

        width = playerProperties.get("width", String.class);
        height = playerProperties.get("height", String.class);
    } else if (playerProperties.containsKey("width") && !playerProperties.containsKey("height")) {
        width = playerProperties.get("width", String.class);
        height = String.valueOf(270 * playerProperties.get("width", 1) / 480);

    } else if (!playerProperties.containsKey("width") && playerProperties.containsKey("height")) {
        height = playerProperties.get("height", String.class);
        width = String.valueOf(480 * playerProperties.get("height", 1) / 270);
    }


// Update Page Context

    pageContext.setAttribute("playerTitle", title);
    pageContext.setAttribute("playerDescription", description);

    pageContext.setAttribute("dialogPath", dialogPath);

    pageContext.setAttribute("account", account);
    pageContext.setAttribute("playerID", playerID);
    pageContext.setAttribute("playerKey", playerKey);
    pageContext.setAttribute("playerDataEmbed", playerDataEmbed);


    pageContext.setAttribute("width", width);
    pageContext.setAttribute("height", height);

%>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN">
<html>
<head>
    <title>${playerTitle} | Brightcove Player</title>

    <meta http-equiv="Content-Type" content="text/html; utf-8"/>

    <cq:includeClientLib categories="cq.wcm.edit"/>

    <script src="/libs/cq/ui/resources/cq-ui.js" type="text/javascript"></script>
    <script type="text/javascript">
        CQ.WCM.launchSidekick("${currentPage.path}", {
            propsDialog: "${dialogPath}",
            locked: ${currentPage.locked}
        });
    </script>

    <style type="text/css">
        .edit-box {
            width: 75%;
        }
    </style>

</head>

<body>

<h1>Brightcove Player Config | &quot;${playerTitle}&quot;</h1>

<div class="definition-container">
    <p>${playerDescription}</p>
</div>


<cq:text value="Player ID" tagName="h2" tagClass="no-icon"/>

<p>Use the Page Properties editor to edit the Player ID.</p>

<div class="edit-box">
    <cq:text property="playerID" placeholder="NONE" tagName="strong"/>
</div>

<cq:text value="Player Key" tagName="h2" tagClass="no-icon"/>

<p>Use the Page Properties editor to edit the Player Key.</p>

<div class="edit-box">
    <cq:text property="playerKey" placeholder="NONE" tagName="strong"/>
</div>

<cq:text value="Data Embed" tagName="h2" tagClass="no-icon"/>

<p>Use the Page Properties editor to edit the Data Embed.</p>

<div class="edit-box">
    <cq:text value="${playerDataEmbed}" tagName="strong"/>
</div>

<cq:text value="Player Preview" tagName="h2" tagClass="no-icon"/>
<p></p>

<div class="edit-box">
    <c:choose>
        <c:when test="${empty playerKey}">
            <video
                    data-account="${account}"
                    data-player="${playerID}"
                    data-embed="${playerDataEmbed}"
                    data-video-id=""
                    class="video-js"
                    width="${width}px"
                    height="${height}px"
                    class="video-js" controls>
            </video>
            <script src="//players.brightcove.net/${account}/${playerID}_${playerDataEmbed}/index.min.js"></script>
        </c:when>
        <c:otherwise>
            <c:if test="${(not empty width) and (not empty height)}">

                <!-- DO NOT USE!!!! FOR PREVIEW PURPOSES ONLY. -->

                <!-- Start of Brightcove Player -->

                <div style="display:none;"></div>
                <!--
                By use of this code snippet, I agree to the Brightcove Publisher T and C
                found at https://accounts.brightcove.com/en/terms-and-conditions/.
                -->

                <script language="JavaScript" type="text/javascript"
                        src="https://sadmin.brightcove.com/js/BrightcoveExperiences.js"></script>

                <object id="myExperience" class="BrightcoveExperience">
                    <param name="bgcolor" value="#FFFFFF"/>
                    <param name="width" value="${width}"/>
                    <param name="height" value="${height}"/>
                    <param name="playerID" value="${playerID}"/>
                    <param name="playerKey" value="${playerKey}"/>
                    <param name="isVid" value="true"/>
                    <param name="isUI" value="true"/>
                    <param name="dynamicStreaming" value="true"/>
                    <param name="cacheAMFURL" value="//share.brightcove.com/services/messagebroker/amf"/>
                    <param name="secureConnections" value="true"/>
                </object>

                <!--
                This script tag will cause the Brightcove Players defined above it to be created as soon
                as the line is read by the browser. If you wish to have the player instantiated only after
                the rest of the HTML is processed and the page load is complete, remove the line.
                -->
                <script type="text/javascript">brightcove.createExperiences();</script>
            </c:if>
        </c:otherwise>
    </c:choose>
</div>

</body>
</html>