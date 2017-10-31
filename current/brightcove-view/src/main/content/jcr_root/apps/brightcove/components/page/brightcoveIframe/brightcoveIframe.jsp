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
<%@include file="/libs/foundation/global.jsp" %>

<%


    //Sample request: /etc/designs/brightcove_iframe.html?pk=AQ~~,AAAA2uzqPsk~,x6biOaTywG-S8Eb2e9BIzJibdsmcwx28&vp=1644607213001

    String videoID = request.getParameter("videoid");
    String accountID = request.getParameter("account");
    String embed = request.getParameter("embed");
    String player = request.getParameter("player");
    String playerid = request.getParameter("playerid");

    request.setAttribute("brc_componentID",playerid);
    request.setAttribute("brc_playerID",player);
    request.setAttribute("brc_account",accountID);
    request.setAttribute("brc_playerDataEmbed",embed);
    request.setAttribute("brc_videoID",videoID);




%>
<html>
<head>
    <style type="text/css" class="bc-style-iframe">
        .bc-player-default_default {
        <c:choose>
            <c:when test="${brc_hasSize}">
                width:${brc_width}px !important;
                height:${brc_height}px !important;
            </c:when>
            <c:otherwise>
                width:480px !important;
                height:270px !important;
            </c:otherwise>
        </c:choose>
        }
    </style>
</head>
<body style="background:black;">
<div style="margin-bottom: 0;margin-left:auto;margin-right:auto;margin-top: 0;overflow-x: hidden;overflow-y: hidden;text-align: center;text-align:center">

    <div id="container-${brc_componentID}" class="brightcove-container" data-playerid="${brc_componentID}" data-account="${brc_account}"
         data-player="${brc_playerID}"
         data-embed="${brc_playerDataEmbed}"
         data-video-id="${brc_videoID}"
            <c:choose>
                <c:when test="${brc_hasSize}">
                    data-width="${brc_width}px"
                    data-height="${brc_height}px"
                </c:when>
                <c:otherwise>
                    data-width="480px"
                    data-height="270px"
                </c:otherwise>
            </c:choose>>
    </div>
    <cq:includeClientLib js="brc.html5-player"/>
</div>
</body></html>