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

<%@include file="/apps/brightcove/components/shared/global.jsp" %>
<%

    /*
        Placeholder script to allow overlaying custom styles if this component is set as the resourceSuperType.

        ${brc_componentID} is defined in /apps/brightcove/components/shared/component-global.jsp

     */

%>


<c:if test="${not brc_hasSize}">
    <style type="text/css">
        #component-wrap-${brc_componentID} .brightcove-container {
            width: 80%;
            display: block;
            position: relative;
            margin: 20px auto;
        }

        #component-wrap-${brc_componentID} .brightcove-container:after {
            padding-top: 56.25%;
            display: block;
            content: '';
        }

        #component-wrap-${brc_componentID} .brightcove-container object,
        #component-wrap-${brc_componentID} .brightcove-container .video-js {
            position: absolute;
            top: 0;
            bottom: 0;
            right: 0;
            left: 0;
            width: 100%;
            height: 100%;
        }
    </style>
</c:if>

<style type="text/css">

    #component-wrap-${brc_componentID} {

    }

    #component-wrap-${brc_componentID} .player-embed-wrap {
        margin-bottom: 0;
        margin-left: auto;
        margin-right: auto;
        margin-top: 0;
        overflow-x: hidden;
        overflow-y: hidden;
        width: 100%;
        text-align: ${brc_align};
    }

    #component-wrap-${brc_componentID}.brc-align-left .player-embed-wrap {
        margin-left: 0;
    }

    #component-wrap-${brc_componentID}.brc-align-right .player-embed-wrap {
        margin-right: 0;
    }

    <%-- ???: Do we really want to always be setting width to 100% ? --%>
    #component-wrap-${brc_componentID} .brightcove-container {
        width: 100%;
    }


</style>


