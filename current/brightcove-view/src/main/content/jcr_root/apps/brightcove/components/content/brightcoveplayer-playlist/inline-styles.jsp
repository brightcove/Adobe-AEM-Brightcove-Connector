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
<%-- TODO: create flexible base styles for out of the box usage. --%>

<c:if test="${not brc_hasSize}">
    <style type="text/css">
        #component-wrap-${brc_componentID} .brightcove-container .video-js {
            display: block;
            position: relative;
            margin: 20px auto;

            width: 55%;
            height: 100%;
        }

        #component-wrap-${brc_componentID}.brc-align-center .brightcove-container .video-js {
            width: 80%;
        }

        #component-wrap-${brc_componentID} .brightcove-container .video-js:after {
            padding-top: 56.25%;
            display: block;
            content: '';
        }


    </style>
</c:if>

<style type="text/css">

    #component-wrap-${brc_componentID} {

    }

    #component-wrap-${brc_componentID} .brightcove-container,
    #component-wrap-${brc_componentID} .brightcove-container .player-embed-wrap {
        width: 100%;
    }

    #component-wrap-${brc_componentID} .brightcove-container .video-js {
        margin-bottom: 0;
        margin-left: auto;
        margin-right: auto;
        margin-top: 0;
        overflow-x: hidden;
        overflow-y: hidden;
        width: 55%;
        display: inline-block;

    <%--text-align: ${brc_align};--%>
    }

    #component-wrap-${brc_componentID} .brightcove-container .playlist-wrapper {
        display: inline-block;
        width: 42%;
        margin-left: auto;
        margin-right: auto;
    }

    #component-wrap-${brc_componentID} .brightcove-container .playlist-wrapper .vjs-playlist{
        margin:0;
    }

    /**
     * Alignment Rules
     ******************/

    /* Left */
    #component-wrap-${brc_componentID}.brc-align-left .brightcove-container .video-js {
        margin-left: 0;
        float: left;
    }

    #component-wrap-${brc_componentID}.brc-align-left .brightcove-container .playlist-wrapper {
        float: right;
    }

    /* Right */
    #component-wrap-${brc_componentID}.brc-align-right .brightcove-container .video-js {
        margin-right: 0;
        float: right;
    }

    #component-wrap-${brc_componentID}.brc-align-right .brightcove-container .playlist-wrapper {
        float: left;
    }

    /* Center */
    #component-wrap-${brc_componentID}.brc-align-center .brightcove-container .video-js {
        display: block;
        width: 100%;
    }

    #component-wrap-${brc_componentID}.brc-align-center .brightcove-container .playlist-wrapper {
        width: 100%;
        display: block;
        margin-top:10px;
    }

    /**
     * Edit mode Styles
     **/
    #component-wrap-${brc_componentID} .drop-target-video{
        width:100%;
    }

</style>


