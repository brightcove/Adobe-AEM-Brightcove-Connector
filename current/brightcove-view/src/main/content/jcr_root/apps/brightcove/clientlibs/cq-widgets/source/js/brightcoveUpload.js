/*

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

 */
function doFileUpload() {
    $CQ.bcUploaded = false;
    $CQ("#postFrame").load(
        function () {
            $CQ($CQ("#bcUpload").parents(".x-panel-bwrap")[0]).find(".cq-siteadmin-refresh").click();
        }
    );
    form = $CQ("#create_video_sample");
    form.attr('target', 'postFrame');
    buildJSONRequest(form);
    form.attr('action', "http://api.brightcove.com/services/post");
    form.submit();
    $CQ("#bcUpload").hide();
    $CQ("#waiting").show();


}

/*function buildJSONRequest(form){
 if($CQ('#name').val() =="" || $CQ('#shortDescription').val() =="" || $CQ("#filePath").val() ==""){
 alert("Require Name, Short Description and File");
 return;
 }else{
 $CQ('#JSONRPC').val('{"method": "create_video", "params": {"video": {"name": "' + $CQ('#name').val() + '", "shortDescription": "' + $CQ('#shortDescription').val() + '"},"token": "c9hG9CFjGaY6mguNiD7BKaYBZ2YCrCdoMlgV1y8LRgKNKgl-38duog.."}}');
 }
 }*/

function showModalWindow() {
    //transition effect
    $CQ("#tagBCUpload").html('<p><a id="tagHref" href="#bcUpload" onclick="hideModalWindow(); return false;">Close Upload Window</a></p>');
    //$CQ("#bcUpload").parent().children(".cq-cft-search-item").remove();
    $CQ("#bcUpload").slideToggle(1000);

}

function hideModalWindow() {
    //$CQ('#bcUpload').hide();
    //$CQ("#tagBCUpload").html('<p><a id="tagHref" href="#bcUpload" onclick="showModalWindow(); return false;">Upload a New Video</a></p>');
    $CQ($CQ("#bcUpload").parents(".x-panel-bwrap")[0]).find(".cq-siteadmin-refresh").click();
}
