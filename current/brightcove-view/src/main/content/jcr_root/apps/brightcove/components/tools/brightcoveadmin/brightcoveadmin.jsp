<%--

    Adobe CQ5 Brightcove Connector

    Copyright (C) 2015 Coresecure Inc.

        Authors:    Alessandro Bonfatti
                    Yan Kisen

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

        ConfigurationService cs = cg.getConfigurationService(selectedAccount);
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
                                        <option value="display_name">In Name</option>
                                        <option value="reference_id">In Reference ID</option>
                                        <option value="tag">In Tags</option>
                                        <option value="shortDescription">In Short Description</option>
                                        <option value="longDescription">In Long Description</option>
                                        <option value="captioning">In Captioning</option>
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
                                    <tbody id="tbData">
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
                <td id="tdMeta" valign="top" class="tdMetadata">
                    <br/>

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
                    <center>
                        <%--<button id="bmetaEdit" onClick="extMetaEdit()" style="display:block">Edit</button>--%>
                        <button id="uploadtrackbutton" onClick="uploadtrack()" style="display:block">Upload Audio Track</button>
                    </center>
                    <br>
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

</body>

</html>
