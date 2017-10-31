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
                 java.util.Set" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.HashMap" %>

<%@include file="/apps/brightcove/components/shared/global.jsp" %>

<%

    String defaultAccount = "";
    String cookieAccount = "";
    String selectedAccount = "";
    String selectedAccountAlias = "";
    String previewPlayerLoc = "";
    String previewPlayerListLoc = "";
    ConfigurationGrabber cg = ServiceUtil.getConfigurationGrabber();
    Set<String> services = cg.getAvailableServices(slingRequest);

    if (services.size() > 0) {
        defaultAccount = (String) services.toArray()[0];
        cookieAccount = ServiceUtil.getAccountFromCookie(slingRequest);

        selectedAccount = (cookieAccount.trim().isEmpty()) ? defaultAccount : cookieAccount;

        ConfigurationService cs = cg.getConfigurationService(selectedAccount) != null ? cg.getConfigurationService(selectedAccount) : cg.getConfigurationService(defaultAccount);
        if (cs != null) {
            previewPlayerLoc = String.format("https://players.brightcove.net/%s/%s_default/index.html?videoId=",cs.getAccountID(),cs.getDefVideoPlayerID());
            previewPlayerListLoc = String.format("https://players.brightcove.net/%s/%s_default/index.html?playlistId=",cs.getAccountID(),cs.getDefPlaylistPlayerID());
        }
    }


    //Update Page Context
    pageContext.setAttribute("previewPlayerLoc", previewPlayerLoc);
    pageContext.setAttribute("previewPlayerListLoc", previewPlayerListLoc);

    pageContext.setAttribute("services", services);
    pageContext.setAttribute("selectedAccount", selectedAccount);
    pageContext.setAttribute("selectedAccountAlias", selectedAccountAlias);

    pageContext.setAttribute("favIcon", "/etc/designs/cs/brightcove/favicon.ico");


%><!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN">
<html>

<head>
    <meta http-equiv="Content-Type" content="text/html; utf-8"/>

    <cq:includeClientLib categories="cq.wcm.edit"/>


    <cq:includeClientLib css="brc.brightcove-api"/>


    <title>Brightcove Admin</title>
    <script type="text/javascript">
        var brc_admin = brc_admin || {};

        brc_admin.apiProxy = "/bin/brightcove/api";
        //This should be the direct URL to a preview player,  corresponding to the account of the tokens
        brc_admin.previewPlayerLoc = "${previewPlayerLoc}";
        brc_admin.previewPlayerListLoc = "${previewPlayerListLoc}";

    </script>

    <cq:includeClientLib js="brc.brightcove-api"/>
</head>

<body>
<div id="CQ"></div>

<div id="brightcove">
    <div class="navbar">
        <div class="navbar-inner">
            <div class="container">
                <ul class="nav">
                    <li class="active">
                        <a id="allVideos"
                           onclick='searchVal="";$(this).parent("li").parent("ul").children("li").attr("class","");$(this).parent("li").attr("class","active");Load(getAllVideosURL());'>
                            All Videos
                        </a>
                    </li>
                    <li>
                        <a id="allPlaylists"
                           onclick='searchVal="";$(this).parent("li").parent("ul").children("li").attr("class","");$(this).parent("li").attr("class","active");Load(getAllPlaylistsURL())'>
                            All Playlists
                        </a>
                    </li>
                </ul>
                <div class="pull-right">
                    <button type="button" id="syncdbutton" alt="Brightcove Dataload" onClick="syncDB()">Sync Database</button>
                    <img src="/etc/designs/cs/brightcove/shared/img/logo_brightcove.png" alt="Brightcove Console">
                </div>

            </div>
        </div>
    </div>


    <div id="divConsole">
        <table width="100%" class="tblConsole" cellspacing="0" callpadding="0" border="0">
            <tr>
                <!-- Start center column -->
                <td width="75%" valign="top">
                    <table id="listTable" width="100%" cellspacing="0" callpadding="0">
                        <tr>
                            <div id="accountDiv" style="float:right;padding:5px">
                                <select id='selAccount' name="selAccount" style="position: relative;top: 5px;">
                                    <%
                                        for (String service: services){
                                        ConfigurationService cs = cg.getConfigurationService(service);
                                            if (cs != null) {
                                                %>
                                                <option value="<%=service%>"
                                                        class="<%=service.equals(selectedAccount) ? "selected":""%>" <%=service.equals(selectedAccount) ? "selected=\"selected\"":""%>>
                                                    <%=cs.getAccountAlias()+" ("+service+")"%>
                                                </option>
                                                <%
                                            }
                                        }
                                    %>
                                </select>
                            </div>
                        </tr>
                        <tr class="trCenterHeader">
                            <td id="tdOne">
                                <div id="headTitle" style="font-weight:bold">All Videos</div>

                                <div id="divVideoCount" style="float:left"></div>

                                <div id="searchDiv" style="float:right;padding:5px">

                                    <input id="search" type="text" value="Search Video" onClick="this.value=''">
                                    <!--Store the search query in searchBut.value so we can use it as the title of the page once the results are returned.  See searchVideoCallBack -->
                                    <select id='selField' name="selField" style="position: relative;top: 5px;">
                                        <option value="every_field">In Every Field</option>
                                        <option value="name">In Name</option>
                                        <option value="reference_id">In Reference ID</option>
                                        <option value="tags">In Tags</option>
                                        <option value="text">In Text</option>
                                    </select>
                                    <button id="searchBut"
                                            onClick="searchVal=document.getElementById('search').value;searchField=document.getElementById('selField').value;Load(searchVideoURL())">
                                        Search
                                    </button>
                                </div>
                                <div id="searchDiv_pl" style="float:right;padding:5px;display:none;">

                                    <input id="search_pl" type="text" value="Search Playlists" onClick="this.value=''"
                                           style="position: relative;top: 5px;">
                                    <!--Store the search query in searchBut.value so we can use it as the title of the page once the results are returned.  See searchVideoCallBack -->
                                    <select id='selField_pl' name="selField_pl" style="position: relative;top: 5px;">
                                        <option value="find_playlist_by_id">In Playlist ID</option>
                                        <option value="find_playlist_by_reference_id">In Reference ID</option>
                                    </select>
                                    <button id="searchBut"
                                            onClick="searchVal=document.getElementById('search_pl').value;searchField=document.getElementById('selField_pl').value;Load(getFindPlaylistsURL())">
                                        Search
                                    </button>
                                </div>

                            </td>
                        </tr>

                        <tr>
                            <td id="tdTwo">
                                <div name="butDiv" class="butDiv">
                                        <span name="buttonRow"><!--The buttons in buttonRow are hidden in playlist view -->
                                            <%--<button id="delButton" class="delButton" onClick="openBox('delConfPop')">--%>
                                                <%--Delete Checked--%>
                                            <%--</button>--%>
                                            <%--<button id="uplButton" class="uplButton" onClick="extFormUpload()">Upload--%>
                                                <%--Video--%>
                                            <%--</button>--%>
                                            <button id="newplstButton" onClick="createPlaylistBox()">Create Playlist
                                            </button>
                                        </span>
                                    <button class="btn" name="delFromPlstButton" onClick="openBox('modPlstPop')">Remove
                                        From Playlist
                                    </button>
                                </div>
                                <div name="pageDiv" class="pageDiv">
                                    Page Number:
                                    <select name="selPageN" onchange="changePage(this.selectedIndex)"></select>
                                </div>
                            </td>
                        </tr>
                        <tr>
                            <td>

                                <!-- Start Main list -->
                                <!-- buildMainVideoList, buildPlaylistList and showPlaylist populate this section, -->
                                <table id="tblMainList" cellpadding="3" cellspacing="0">
                                    <thead>
                                    <tr id="trHeader">
                                        <th id="checkCol" class="tdMainTableHead"
                                            style="border-right:0px;color:#e5e5e5;font-size:1px">
                                            <input type="checkbox" onclick="toggleSelect(this)" id="checkToggle"/>?
                                        </th>
                                        <th id="nameCol" class="tdMainTableHead sortable ASC" style="border-left:0px"
                                            data-sortType="" data-sortBy="name" onclick="sort(this)">
                                            Video Name <span class='order'></span>
                                        </th>
                                        <th id="lastUpdated" class="tdMainTableHead sortable NONE" data-sortType="-"
                                            data-sortBy="updated_at" onclick="sort(this)">
                                            Last Updated <span class='order'></span>
                                        </th>
                                        <th class="tdMainTableHead sortable NONE" data-sortType=""
                                            data-sortBy="reference_id" onclick="sort(this)">
                                            Reference Id <span class='order'></span>
                                        </th>
                                        <th class="tdMainTableHead">
                                            ID
                                        </th>


                                    </tr>
                                    </thead>
                                    <tbody id="tbData" >
                                    </tbody>
                                </table>
                            </td>
                        </tr>
                        <tr>
                            <td>
                                <div name="butDiv" class="butDiv">
                                        <span name="buttonRow"><!--The buttons in buttonRow are hidden in playlist view -->
                                            <%--<button id="delButton" class="delButton" onClick="openBox('delConfPop')">--%>
                                                <%--Delete Checked--%>
                                            <%--</button>--%>
                                            <%--<button id="uplButton" class="uplButton" onClick="extFormUpload()">Upload--%>
                                                <%--Video--%>
                                            <%--</button>--%>
                                            <button id="newplstButton" onClick="createPlaylistBox()">Create Playlist
                                            </button>
                                        </span>
                                    <button class="btn" name="delFromPlstButton" onClick="openBox('modPlstPop')">Remove
                                        From Playlist
                                    </button>
                                </div>
                                <div name="pageDiv" class="pageDiv">
                                    Page Number:
                                    <select name="selPageN" onchange="changePage(this.selectedIndex)"></select>
                                </div>
                            </td>
                        </tr>
                    </table>
                </td>

                <!-- start right metadata column, fields filled in by showMetaData() -->
                <!--Element Id's have the meta. prefix followed by the actual name of the Video object field.  This allows the fields to be easily compared.  See  metaSubmit()  -->
                <td id="tdMeta" valign="top" class="tdMetadata" style="display:none;">
                    <br/>
                    <span type="button" title="hide metadata" style="float:right;cursor: pointer;" onclick="closeBox('tdMeta')">x</span>
                    <div id="divMeta.name" style="font-weight:bold"></div>
                    <br/>Last Updated:
                    <div id="divMeta.lastModifiedDate"></div>
                    <hr/>
                    <div id="divMeta.previewDiv">
                        <ul class="thumbnails">
                            <li class="span3">
                                <div class="thumbnail">
                                    <img id="divMeta.videoStillURL" alt=" No Image " style="width:260px"/>
                                    <a onClick="uploadPoster()" href="#"
                                       class="btn">Change Poster</a>
                                </div>
                            </li>
                            <li class="span2">
                                <div class="thumbnail">
                                    <img id="divMeta.thumbnailURL" alt=" No Thumbnail " style="width:160px"/>
                                    <a onClick="uploadThumbnail()" href="#"
                                       class="btn">Change Thumbnail</a>
                                </div>
                            </li>

                        </ul>
                        <%--Upload text track / Delete Track--%>
                        <center id="trackarea">
                            <%--<span>Click Below to Remove Text Track</span><br>--%>
                            <div id="divMeta.text_tracks">
                            </div>
                            <br/>
                            <button id="uploadtrackbutton" onClick="uploadtrack()" style="display:block">Upload New Text Track</button>
                            <br/>

                        </center>
                        <p><a href="#" onClick="doPreview(document.getElementById('divMeta.id').innerHTML)"
                              class="btn btn-primary">Video Preview</a>
                        </p>
                    </div>
                    <br/>Duration:
                    <div id="divMeta.length"></div>
                    <br/>Video ID:
                    <div id="divMeta.id"></div>
                    <br/>
                    <hr>
                    Short Description:
                    <div id="divMeta.shortDescription"></div>
                    <br/>Tags:
                    <div id="divMeta.tags"></div>
                    <br/>Related Link:
                    <br/>
                    <a id="divMeta.linkURL"></a>
                    <br/>
                    <br/>

                    <div style="display:none" id="divMeta.linkText"></div>
                    Economics:
                    <div id="divMeta.economics"></div>
                    <br/>Date Published:
                    <div id="divMeta.publishedDate"></div>
                    <br/>Reference ID:
                    <div id="divMeta.referenceId"></div>
                    <br/>
                </td>
            </tr>
        </table>
        <div id="loading" class="alert">
            <p class="loadingMsg"><strong>Loading!</strong> Please wait while your page loads.</p>
            <p class="syncingMsg" style="display:none"><strong>Synchronizing</strong> Please wait while your assets load.</p>
        </div>
        <!-- Dimmed "Screen" over content behind overlays -->
        <div id="screen" style="display:none"></div>


        <!--Upload Video Image-->
        <div id="uploadVideoImageDiv" style="display:none" class="overlay tbInput">
            <form id="uploadVideoImageForm" method="POST" enctype="multipart/form-data" target="postFrame">
                <br/>
                <center><span class="title">Upload A New Image:</span>
                    <br/>
                    <br/>
                    <span class="subTitle">Title and Reference ID are Required</span>
                </center>
                <div class="hLine"></div>
                <table>
                    <tr class="requiredFields">
                        <td>Title:</td>
                        <td style="width:100%">
                            <input type="text" name="name" id="name"/>
                        </td>
                    </tr>
                    <tr>
                        <td>Reference ID:</td>
                        <td>
                            <input type="text" name="referenceId"/>
                        </td>
                    </tr>
                    <tr class="requiredFields">
                        <td>File:</td>
                        <td>
                            <input type="file" name="filePath" id="filePath" style="width:100%"/>
                            <input type="hidden" name="image"/>
                            <input type="hidden" id="videoidthumb" name="videoidthumb"/>
                            <input type="hidden" name="command" id="command" value="add_video_image"/>
                        </td>
                    </tr>
                </table>
                <div class="hLine"></div>
                <br/>
                <center>
                    <div class="subTitle">
                        Delays up to 20 minutes may occur before changes are reflected
                    </div>
                    <br/>
                    <button type="button" id="startUploadButton" onClick="startVideoImageUpload()">Start Upload</button>
                    <button type="button" id="cancelUploadButton" onClick="closeBox('uploadVideoImageDiv',this.form)">
                        Cancel
                    </button>
                </center>
            </form>
        </div>
        <!--Create Playlist -->
        <div id="createPlaylistDiv" style="display:none" class="overlay tbInput">
            <form id="createPlaylistForm" method="POST" enctype="multipart/form-data" target="postFrame">
                <br/>
                <center><span class="title">Create Playlist:</span>
                    <br/>
                    <br/>
                    <span class="subTitle">Title is Required</span>
                </center>
                <div class="hLine"></div>
                <table>
                    <tr class="requiredFields">
                        <td>Title:</td>
                        <td style="width:100%">
                            <input type="text" name="plst.name" id="plst.name"/>
                        </td>
                    </tr>
                    <tr>
                        <td>Short Description:</td>
                        <td>
                            <input type="text" name="plst.shortDescription" id="plst.shortDescription"/>
                        </td>
                    </tr>
                    <tr>
                        <td>Reference ID:</td>
                        <td>
                            <input type="text" name="plst.referenceId" id="plst.referenceId"/>
                        </td>
                    </tr>
                </table>
                <fieldset>
                    <legend>Videos</legend>
                    <table id="createPlstVideoTable">
                        <tr style="background-color:#e5e5e5">
                            <td class="tdMainTableHead" style="width:100%">Video Name</td>
                            <td class="tdMainTableHead" style="width:40%">ID</td>
                        </tr>
                    </table>
                    <input type="hidden" name="a" value="create_playlist"/>
                    <input type="hidden" name="playlist" id="playlist"/>
                </fieldset>
                <div class="hLine"></div>
                <br>
                <center>
                    <button type="button" id="createPlstSubmit" onClick="createPlaylistSubmit()">Create Playlist
                    </button>
                    <button type="button" id="createPlstCancel"
                            onClick="$('#createPlstVideoTable').empty();closeBox('createPlaylistDiv',this.form)">Cancel
                    </button>
                </center>
            </form>
        </div>

        <!--Get Upload status-->
        <div id='getUplStatus' style="display:none" class="overlay">
            <center>Get Upload Status By VideoId
                <br>
                <br>VideoId:
                <input type="text" id="uplStatusId">
                <br>
                <br>
                <button onClick="Load(getUploadStatusById())">Submit</button>
                <button type="button" onClick="closeBox('getUplStatus')">Cancel</button>
            </center>
        </div>

        <!--Upload Status Bar-->
        <div id="uploadStatus" style="display:none" class="overlay">
            <center>Uploading...</center>
            <div style="background-color:black;overflow:hidden;width:100%;position:relative">
                <div id="progress"></div>
            </div>
        </div>

        <!-- Delete confirmation dialog -->
        <div id="delConfPop" style="display:none" class="delConfPop overlay">
            <center><strong style="font-size:14px;">DELETE</strong>
                <br/>Are you sure you want to proceed?
                <br><span class="subTitle">Delays up to 20 minutes may occur before changes are reflected</span>
                <br/>
                <br/>
                <button name="deleteConfBut" onClick="delConfYes()" class="btn">Yes</button>
                <button type="button" class="btn" name="deleteConfBut" onClick="closeBox('delConfPop')">No</button>
            </center>
        </div>

        <!-- Modify Playlist confirmation dialog -->
        <div id="modPlstPop" style="display:none" class="delConfPop overlay">
            <center>Remove Selected From Playlist?
                <br/>
                <span class="subTitle">Are you sure you want to proceed?</span>
                <br/>
                <br/>

                <form id="modPlstForm" method="POST" enctype="multipart/form-data" target="postFrame">
                    <button onClick="modPlstSubmit()">Yes</button>
                    <button type="button" class="btn" onClick="closeBox('modPlstPop')">No</button>

                    <input type="hidden" name="command" value="update_playlist"/>
                    <input type="hidden" name="playlist" id="playlist"/>
                </form>
            </center>
        </div>

        <!--Player Preview  -->
        <!-- This window is populated by doPreview -->
        <div id="playerDiv" style="display:none;height: auto;" class="overlay">
            <div id="playerdrag" class="hLine"></div>
            <div id="playerTitle" style="text-transform:uppercase; font-weight:bold; font-size:14px;"></div>
            <div class="hLine"></div>
        </div>

        <!--Share Video -->
        <!--this functionality has been disabled (commented out), and has not been fully maintained -->
        <div id="shareVideoDiv" style="display:none" class="overlay tbInput">
            <form id="shareVideoForm" method="POST" enctype="multipart/form-data" target="postFrame">
                <br/>
                <center><span class="title">Share Video</span>
                    <br/>
                    <br/>
                    <span class="subTitle">Sharee Account Id's Should be Comma Separated </span>
                </center>
                <div class="hLine"></div>
                <table>

                    <tr class="requiredFields">
                        <td>Sharee Account Id's:</td>
                        <td style="width:100%">
                            <input type="text" name="sharees" id="sharees" style="width:100%"/>
                        </td>
                    </tr>

                </table>
                <fieldset>
                    <legend>Video</legend>
                    <table id="shareVideoTable">
                        <tr style="background-color:#e5e5e5">
                            <td class="tdMainTableHead" style="width:100%">Video Name</td>
                            <td class="tdMainTableHead" style="width:40%">ID</td>
                        </tr>
                    </table>
                </fieldset>
                <div class="hLine"></div>
                <br>
                <center>
                    <button id="shareSubmit" onClick="shareVidSubmit()">Share Video</button>
                    <button type="button" id="shareCancel"
                            onClick="$('shareVideoTable').empty();closeBox('shareVideoDiv',this.form)">Cancel
                    </button>
                </center>
                <input type="hidden" name="command" value="share_video"/>
                <input type="hidden" name="data"/>
            </form>
        </div>
        <iframe id="postFrame" name="postFrame" style="width:100%;border:none;display:none;"></iframe>
    </div>
</div>


<%--LOADING MODAL ADDITION--%>
<div class="loading">
    <div class="loader">
        <svg id="brightcode" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 370.83 367.28">
            <path class="cls-1 shape-1" d="M53.36,161c-2.57-.94-3.73-3.19-5-5.36C37.65,136.84,27,118,16.15,99.33c-5-8.61-5-16.8.11-25.41C27.07,55.8,37.57,37.49,48.21,19.26c5.33-9.14,11.08-9.11,16.42.16,10.38,18,20.63,36.09,31.1,54,3.3,5.65,5.2,11.48,4.49,18l-3.9,9.17,0,0a34.07,34.07,0,0,0-2,2.79c-10.07,17.22-20.2,34.41-30.1,51.72C61.65,159.64,58.58,162.07,53.36,161Z" />
            <path class="cls-1 shape-2" d="M102,106.67a.78.78,0,0,0,.39-.82c4-3.17,7.91-3.1,11.88,0a.79.79,0,0,0,.4.84c3,5.46,5.81,11.08,9.55,16.11a32.9,32.9,0,0,0,1.39,3.14q11.57,20.29,23.2,40.55c4.47,7.75,4.55,15.37,0,23.13-11.25,19.19-22.36,38.47-33.66,57.63-3.95,6.69-10.34,6.54-14.34-.22-4.56-7.71-8.87-15.58-13.45-23.29C81,213,75.59,201.82,68.44,191.61v-.08a6.82,6.82,0,0,0-.5-1.89c-5.39-8.76-4.56-17.27.72-25.77,3.5-5.63,6.81-11.38,10.07-17.16,7.15-12.69,15.18-24.88,21.68-37.94v0A2.89,2.89,0,0,0,102,106.67Z" />
            <path class="cls-2 shape-3" d="M180.32,172.74l-7.73-.16c-6.84-2.21-12.49-5.9-16.06-12.4-4-7.26-8.09-14.46-12.26-21.61-5.24-9-10.6-17.95-15.9-26.92-1.08-5.09-5.85-8.52-6.21-13.93l0-1c.59-2.9,3.76-4.08,4.54-6.85,24.84,1.81,49.72.24,74.57.93,7.84.22,13,5.05,16.89,11.46q5,8.26,10,16.53a169.6,169.6,0,0,0,14,24.28.52.52,0,0,0,.15.65.94.94,0,0,0,.94,1.08l0,0,1.08,1.95,0,0a3.2,3.2,0,0,0,1.86,3.12h0a12.25,12.25,0,0,0,4.06,7h0a4.36,4.36,0,0,0,2.11,3.94v0a24.88,24.88,0,0,1,.05,7.94h0c-2.93,3.1-6.48,4-10.75,4C221.14,172.5,200.73,172.67,180.32,172.74Z" />
            <path class="cls-2 shape-4" d="M216.8,254.57l-.55.94c-4.78,4.24-9.9,8-16.6,8-23,.09-46,.14-68.94-.08-7.65-.08-10.47-5.64-6.65-12.37q16.22-28.6,32.57-57.14c4.8-8.41,12-12.36,21.76-12.27,20.65.19,41.3.09,61.94,0a31.79,31.79,0,0,1,9.83,1.2l0,0a2.51,2.51,0,0,0,2.08,2l0,0a15,15,0,0,1-2.09,11.84c-5,8.34-9.84,16.84-14.6,25.35-5.73,10.22-12.3,20-17.2,30.64h0Z" />

            <path class="cls-3 shape-5" d="M232.4,263.17l-1.11-.4-3-3-1-3.08a21.74,21.74,0,0,1,2.88-7.37q16.06-27.79,32-55.65c4.53-7.94,11.2-12,20.33-12,21.94,0,43.89.07,65.83-.07,4.42,0,7.73,1.31,9.91,5.2l.18,3.85-9.84,18a.84.84,0,0,0-.32.87,99.26,99.26,0,0,0-8.79,15.16l-.11.16q-.59.09-.46.64l-.71,1.67-.91,1.44a2.35,2.35,0,0,0-1.15,2c-3.37,5.67-6.95,11.22-10,17-3.34,6.26-7.43,11.64-13.92,14.86v-.05c-4.9,1.15-9.87,1.19-14.87,1.17-16.14-.06-32.29,0-48.43,0C243.36,263.57,237.86,264.09,232.4,263.17Z" />

            <path class="cls-3 shape-6" d="M356.19,341.24c-.89-1.37-1.68-2.8-2.45-4.25s-1.51-2.91-2.29-4.35l0-.08,0-.08q-5.4-8.76-10.54-17.66t-9.94-18l-.84-1.49-.84-1.49c-.88-1.48-2-2.83-3-4.26a11.74,11.74,0,0,1-2.08-4.75h0a49.38,49.38,0,0,0-7.06-7.38,22.66,22.66,0,0,0-9.1-4.62l-35.85-.1-35.85-.1a10.82,10.82,0,0,0-4.43,1.16,12.52,12.52,0,0,0-3.6,2.83l-.17.57-.17.57-.28.52-.28.52h0a12.79,12.79,0,0,0,.75,4.12,22.83,22.83,0,0,0,1.81,3.82q6.14,10.7,12.19,21.46l12.13,21.49h0l3.49,5.5,3.49,5.5,2.64,3.88,2.29,3.35a15.75,15.75,0,0,0,6.34,4.91,29.65,29.65,0,0,0,7.76,2h67a34.94,34.94,0,0,0,4.67-.37,10,10,0,0,0,4.34-1.77l0,0,0,0a9.33,9.33,0,0,0,1.87-5.71A11.2,11.2,0,0,0,356.19,341.24Z" />
        </svg>
    </div>
</div>


</body>

</html>
