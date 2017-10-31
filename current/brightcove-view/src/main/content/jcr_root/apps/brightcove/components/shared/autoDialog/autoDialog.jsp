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
<%@ page import="org.slf4j.Logger" %>
<%@ page import="org.slf4j.LoggerFactory" %>
<%@ page import="com.coresecure.brightcove.wrapper.filter.CustomAddDialogTabFilter" %>
<%@ page import="org.apache.sling.commons.json.JSONObject" %>
<%@ page import="com.coresecure.brightcove.wrapper.sling.ServiceUtil" %>
<%@ page import="org.apache.sling.commons.json.JSONArray" %>
<%@ page trimDirectiveWhitespaces="true" %>
<%@include file="/libs/foundation/global.jsp" %>
<%
    log.error("request: "+slingRequest.getRequestURI());
    log.error("request: "+slingRequest.getRequestPathInfo().getExtension());
    log.error("request: "+slingRequest.getRequestPathInfo().getResourcePath());
    log.error("request: "+slingRequest.getRequestPathInfo().getSelectorString());
    log.error("request: "+slingRequest.getRequestPathInfo().getSuffix());
%>

<%


    Resource asset_res = slingRequest.getParameter("item") != null ? resourceResolver.resolve(slingRequest.getParameter("item")) : resourceResolver.resolve(slingRequest.getRequestPathInfo().getSuffix());
    String requestedAccount = asset_res.getParent().getName();

    Resource metadataRes = asset_res.getChild("jcr:content/metadata");
    ValueMap map = metadataRes.adaptTo(ValueMap.class);
    Logger LOGGER = LoggerFactory.getLogger(CustomAddDialogTabFilter.class); //export log


    ServiceUtil serviceUtil = new ServiceUtil(requestedAccount);


    JSONObject custom_fields_obj = serviceUtil.getCustomFields();
    JSONArray custom_fields_arr = custom_fields_obj.getJSONArray("custom_fields");

    Resource custom_fields = metadataRes.getChild("brc_custom_fields");
    ValueMap custom_map = custom_fields != null ? custom_fields.adaptTo(ValueMap.class) : null;



//    JSONObject getCustomFields(String ID) {

//TODO HANDLE TAB ID?
%>






<%--HEADER--%>
<%--HEADER--%>

<%--<div  class="aem-assets-metadata-form-tab" data-tabid="e445e6bf-6bf8-436b-a069-ac0dc7cbda1b">--%>


    <div  class="aem-assets-metadata-form-column">
        <div class="foundation-field-editable">
            <div  class="coral-Form-fieldwrapper foundation-field-edit"><label class="coral-Form-fieldlabel">Title (Editable in Basic Tab)</label><input  class="coral-Form-field" data-metaType="text" type="text" value="<%=map.get("dc:title","")%>" disabled="" data-foundation-validation="" data-validation="" is="coral-textfield"></div>
        </div>
        <div class="foundation-field-editable">
            <div  class="coral-Form-fieldwrapper foundation-field-edit"><label class="coral-Form-fieldlabel">Short Description</label><input  class="coral-Form-field" data-metaType="text" type="text" name="./jcr:content/metadata/brc_description" value="<%=map.get("brc_description","")%>" data-foundation-validation="" data-validation="" is="coral-textfield"></div>
        </div>
        <div class="foundation-field-editable">
            <div  class="coral-Form-fieldwrapper foundation-field-edit"><label class="coral-Form-fieldlabel">Long Description</label><textarea  class="coral-Form-field" data-metaType="text" type="text" name="./jcr:content/metadata/brc_long_description" value="<%=map.get("brc_long_description","")%>" data-foundation-validation="" data-validation="" is="coral-textarea"><%=map.get("brc_long_description","")%></textarea></div>
        </div>
        <div class="foundation-field-editable">
            <div  class="coral-Form-fieldwrapper foundation-field-edit">
                <label class="coral-Form-fieldlabel">Economics</label>
                <coral-select class="coral-Form-field" data-metaType="dropdown" name="./jcr:content/metadata/brc_economics" data-foundation-validation="" data-validation="" >
                    <coral-select-item <%="AD_SUPPORTED".equals(map.get("brc_economics","AD_SUPPORTED")) ? "selected='selected'" : ""%> value="AD_SUPPORTED">Ad Enabled</coral-select-item>
                    <coral-select-item <%="FREE".equals(map.get("brc_economics","AD_SUPPORTED")) ? "selected='selected'" : ""%> value="FREE">Free</coral-select-item>
                </coral-select>
            </div>
        </div>
        <div class="foundation-field-editable">
            <div  class="coral-Form-fieldwrapper foundation-field-edit"><label class="coral-Form-fieldlabel">Link to Related Item</label><input  class="coral-Form-field" data-metaType="text" type="text" name="./jcr:content/metadata/brc_link_url" value="<%=map.get("brc_link_url","")%>" data-foundation-validation="" data-validation="" is="coral-textfield"></div>
        </div>
        <div class="foundation-field-editable">
            <div  class="coral-Form-fieldwrapper foundation-field-edit"><label class="coral-Form-fieldlabel">Text for Related Item</label><input  class="coral-Form-field" data-metaType="text" type="text" name="./jcr:content/metadata/brc_link_text" value="<%=map.get("brc_link_text","")%>" data-foundation-validation="" data-validation="" is="coral-textfield"></div>
        </div>

        <div  class="coral-Form-fieldwrapper foundation-field-edit">
            <label class="coral-Form-fieldlabel">Projection</label>
            <coral-select class="coral-Form-field" data-metaType="dropdown" name="./jcr:content/metadata/brc_projection" data-foundation-validation="" data-validation="" >
                <coral-select-item <%="".equals(map.get("brc_projection","")) ? "selected='selected'" : ""%> value=""></coral-select-item>
                <coral-select-item <%="equirectangular".equals(map.get("brc_projection","EMPTY")) ? "selected='selected'" : ""%> value="equirectangular">360 Degree</coral-select-item>
            </coral-select>
        </div>

    </div>


<div  class="aem-assets-metadata-form-column">

    <%
        String current_field_title;
        String current_field_id;
        String current_type;
        for(int z = 0 ; z < custom_fields_arr.length() ; z ++ )
        {
            JSONObject current  = custom_fields_arr.getJSONObject(z);
            current_field_title = current.getString("display_name");
            current_field_id    = current.getString("id");;
            current_type = current.getString("type");
            Boolean required = current.getBoolean("required");


            if(current_type.equals("enum"))
            {
    %>

    <div class="foundation-field-editable">
        <div  class="coral-Form-fieldwrapper foundation-field-edit">
            <label class="coral-Form-fieldlabel"><b><%=current_field_title%><%=required ? " *":""%></b></label>
            <coral-select class="coral-Form-field" data-metaType="dropdown" name="./jcr:content/metadata/brc_custom_fields/<%=current_field_id%>" data-foundation-validation="" data-validation="">
                <% if (!required) { %>
                <coral-select-item value=""></coral-select-item>
                <% } %>
                <%


                    JSONArray enums = current.getJSONArray("enum_values");
                    for(int x = 0 ; x < enums.length(); x++)
                    {

                        String value = custom_map!=null ?  custom_map.get(current_field_id,""):"";
                        String enumValue = enums.getString(x);
                %>
                <coral-select-item <%=enumValue.equals(value) ? "selected='selected'" : ""%> value="<%=enumValue%>"><%=enumValue%></coral-select-item>
                <%
                    }

                %>
            </coral-select>
        </div>
    </div>
    <%}
    else
    {
    %>
    <div class="foundation-field-editable">
        <div  class="coral-Form-fieldwrapper foundation-field-edit">
            <label class="coral-Form-fieldlabel"><b><%=current_field_title%><%=required ? " *":""%></b></label>
            <input <%=required ? "aria-required='true' required='true'":""%> class="coral-Form-field" data-metaType="text" type="text" name="./jcr:content/metadata/brc_custom_fields/<%=current_field_id%>" value="<%=custom_map!=null ? custom_map.get(current_field_id,""):""%>" data-foundation-validation="" data-validation="" is="coral-textfield">
        </div>
    </div>
    <%
            }
        }
    %>

</div>


<div  class="aem-assets-metadata-form-column">
    <div class="foundation-field-editable">
        <div  class="coral-Form-fieldwrapper foundation-field-edit"><label class="coral-Form-fieldlabel">Video ID</label><input  class="coral-Form-field" disabled="" data-metaType="text" type="text" name="./jcr:content/metadata/brc_id" value="<%=map.get("brc_id","")%>" data-foundation-validation="" data-validation="" is="coral-textfield"></div>
    </div>

    <div class="foundation-field-editable">
        <div  class="coral-Form-fieldwrapper foundation-field-edit" ><label class="coral-Form-fieldlabel">Reference ID</label><input  class="coral-Form-field" disabled="" data-metaType="text" type="text" name="./jcr:content/metadata/brc_reference_id" value="<%=map.get("brc_reference_id","")%>" data-foundation-validation="" data-validation="" is="coral-textfield"></div>
    </div>

    <div class="foundation-field-editable">
        <div  class="coral-Form-fieldwrapper foundation-field-edit"><label class="coral-Form-fieldlabel">Last Updated</label><input  class="coral-Form-field" disabled="" data-metaType="text" type="text" name="./jcr:content/metadata/brc_updated_at" value="<%=map.get("brc_updated_at","")%>" data-foundation-validation="" data-validation="" is="coral-textfield"></div>
    </div>
    <div class="foundation-field-editable">
        <div  class="coral-Form-fieldwrapper foundation-field-edit"><label class="coral-Form-fieldlabel">Date Published</label><input  class="coral-Form-field" disabled="" data-metaType="text" type="text" name="./jcr:content/metadata/brc_created_at" value="<%=map.get("brc_created_at","")%>" data-foundation-validation="" data-validation="" is="coral-textfield"></div>
    </div>
    <div class="foundation-field-editable">
        <div  class="coral-Form-fieldwrapper foundation-field-edit"><label class="coral-Form-fieldlabel">Duration</label><input  class="coral-Form-field" disabled="" data-metaType="text" type="text" name="./jcr:content/metadata/brc_duration" value="<%=map.get("brc_duration","")%>" data-foundation-validation="" data-validation="" is="coral-textfield"></div>
    </div>

</div>





