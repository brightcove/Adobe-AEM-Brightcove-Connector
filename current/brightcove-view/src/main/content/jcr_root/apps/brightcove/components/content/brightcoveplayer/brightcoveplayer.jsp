<%@ page import="com.day.cq.wcm.api.components.DropTarget" %>
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

<%@include file="/apps/brightcove/components/shared/component-global.jsp" %>

<%

    //add DropTarget prefix to page context

    pageContext.setAttribute("dropTargetPrefix", DropTarget.CSS_CLASS_PREFIX);

%>

<cq:include script="inline-styles.jsp"/>

<%-- Allow for inline CSS to be added at the component level for tweaks --%>
<c:if test="${(not brc_ignoreComponentProperties) and (not empty properties['inlineCSS'])}">
    <style type="text/css">
        <c:out value="${properties['inlineCSS']}" escapeXml="true"/>
    </style>
</c:if>


<div id="${brc_containerID}" class="${brc_containerClass}">
    <div id="component-wrap-${brc_componentID}" class="brc-align-${brc_align}">
        <c:choose>
            <c:when test="${(not empty brc_account) or (not empty brc_playerID)}">

                <div class="${dropTargetPrefix}brightcove_player md-dropzone-video drop-target-player"
                     data-emptytext="Add Player Here">

                    <c:if test="${(not empty brc_videoID) or (not empty brc_playlistID)}">
                        <div class="player-embed-wrap">
                            <cq:include script="player-embed.jsp"/>
                        </div>

                    </c:if>
                    <c:if test="${isEditMode}">
                        <div class="${dropTargetPrefix}brightcove_video cq-video-placeholder cq-block-sm-placeholder md-dropzone-video drop-target-video"
                             data-emptytext="Add Media Here"></div>
                    </c:if>
                </div>
            </c:when>
            <c:otherwise>
                <c:if test="${isEditMode}">
                    <div class="${dropTargetPrefix}brightcove_player cq-video-placeholder cq-block-sm-placeholder md-dropzone-video drop-target-player-empty"
                         data-emptytext="Add Player Here"></div>
                </c:if>

            </c:otherwise>
        </c:choose>
    </div>
</div>
<%

    /*** Cleanup all Page Context attributes bound to Request in /apps/brightcove/components/shared/component-global.jsp ***/

    pageContext.removeAttribute("brc_componentID");

    pageContext.removeAttribute("brc_account");
    pageContext.removeAttribute("brc_videoID");
    pageContext.removeAttribute("brc_playlistID");

    pageContext.removeAttribute("brc_playerPath");
    pageContext.removeAttribute("brc_playerID");
    pageContext.removeAttribute("brc_playerKey");
    pageContext.removeAttribute("brc_playerDataEmbed");


    pageContext.removeAttribute("brc_ignoreComponentProperties");

    pageContext.removeAttribute("brc_align");
    pageContext.removeAttribute("brc_width");
    pageContext.removeAttribute("brc_height");
    pageContext.removeAttribute("brc_hasSize");

    pageContext.removeAttribute("brc_containerID");
    pageContext.removeAttribute("brc_containerClass");

%>
<c:if test="${isEditMode && isTouchUI}">
<div data-sly-test="${wcmModes.isTouchAuthoring}" title="Configure component here" class="cq-Overlay cq-Overlay--component cq-droptarget cq-Overlay--placeholder" style="color: rgba(0,0,0,.3);
    border-color: rgba(0,0,0,.3);
    background-color: rgba(255,255,255,.25);
    border-style: solid;
    border-width: 0.125rem;
    margin: -0.125rem;
    height: 50px;
    width: 100%;
    line-height: 2.875rem;
    font-size: 0.875rem;
    text-align: center;
">Configure <%= componentContext.getComponent().getProperties().get("jcr:title","")%></div>
</c:if>
