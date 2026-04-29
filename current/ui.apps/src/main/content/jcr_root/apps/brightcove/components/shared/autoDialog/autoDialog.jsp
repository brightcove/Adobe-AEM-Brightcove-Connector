<%--
 Adobe AEM Brightcove Connector

 Copyright (C) 2018 Coresecure Inc.

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

<%@ page import="com.coresecure.brightcove.wrapper.filter.CustomAddDialogTabFilter" %>
<%@ page import="org.apache.sling.commons.json.JSONObject" %>
<%@ page import="com.coresecure.brightcove.wrapper.sling.ServiceUtil" %>
<%@ page import="org.apache.sling.commons.json.JSONArray" %>
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page trimDirectiveWhitespaces="true" %>
<%@include file="/libs/foundation/global.jsp" %>
<%


    Resource asset_res = slingRequest.getParameter("item") != null ? resourceResolver.resolve(slingRequest.getParameter("item")) : resourceResolver.resolve(slingRequest.getRequestPathInfo().getSuffix());

	Resource metadataRes = asset_res.getChild("jcr:content/metadata");
	ValueMap map = metadataRes.adaptTo(ValueMap.class);
	String brcid = map.get("brc_id","");
   
    Node parentNode = asset_res.getParent().adaptTo(Node.class);

    if ( StringUtils.isNotBlank("brc_id")) {

        String requestedAccount;
        
        if (parentNode.hasProperty("brc_folder_id")) {
            // this is not the actual account folder, so let's go up one more
            requestedAccount = parentNode.getParent().getName();
        } else {
            requestedAccount = parentNode.getName();
        }


        JSONArray custom_fields_arr = new JSONArray();
        try {
            ServiceUtil serviceUtil = new ServiceUtil(requestedAccount);
            JSONObject custom_fields_obj = new JSONObject(serviceUtil.getCustomFields().toString());
            JSONArray fetched = custom_fields_obj.optJSONArray("custom_fields");
            if (fetched != null) custom_fields_arr = fetched;
        } catch (Exception e) {
            // account not configured in this environment — render without custom fields
        }

        Resource custom_fields = metadataRes.getChild("brc_custom_fields");
        ValueMap custom_map = custom_fields != null ? custom_fields.adaptTo(ValueMap.class) : null;

        String[] brcVariantsRaw = map.get("brc_variants", new String[0]);
%>
<script type="application/json" id="brc-variants-data">[<%
    for (int bvIdx = 0; bvIdx < brcVariantsRaw.length; bvIdx++) {
        if (bvIdx > 0) out.print(",");
        String bv = brcVariantsRaw[bvIdx];
        // Escape `</` so a variant value containing `</script>` cannot break
        // out of this script tag. `\/` is valid JSON; the parser reads `</`.
        out.print((bv != null && !bv.isEmpty()) ? bv.replace("</", "<\\/") : "{}");
    }
%>]</script>

    <div  class="aem-assets-metadata-form-column">
        <div class="foundation-field-editable">
            <div  class="coral-Form-fieldwrapper foundation-field-edit"><label class="coral-Form-fieldlabel">Title (Editable in Basic Tab)</label><input  class="coral-Form-field" data-metaType="text" type="text" value="<%=map.get("dc:title","")%>" disabled="" data-foundation-validation="" data-validation="" is="coral-textfield"></div>
        </div>
        <div class="foundation-field-editable">
            <div  class="coral-Form-fieldwrapper foundation-field-edit"><label class="coral-Form-fieldlabel">Short Description</label><input  class="coral-Form-field" data-metaType="text" type="text" name="./jcr:content/metadata/brc_description" maxlength="250" value="<%=map.get("brc_description","")%>" data-foundation-validation="" data-validation="" is="coral-textfield"></div>
        </div>
        <div class="foundation-field-editable">
            <div  class="coral-Form-fieldwrapper foundation-field-edit"><label class="coral-Form-fieldlabel">Long Description</label><textarea maxlength="5000" class="coral-Form-field" data-metaType="text" type="text" name="./jcr:content/metadata/brc_long_description" value="<%=map.get("brc_long_description","")%>" data-foundation-validation="" data-validation="" is="coral-textarea"><%=map.get("brc_long_description","")%></textarea></div>
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
            <div  class="coral-Form-fieldwrapper foundation-field-edit"><label class="coral-Form-fieldlabel">Link to Related Item</label><input maxlength="250" class="coral-Form-field" data-metaType="text" type="text" name="./jcr:content/metadata/brc_link_url" value="<%=map.get("brc_link_url","")%>" data-foundation-validation="" data-validation="" is="coral-textfield"></div>
        </div>
        <div class="foundation-field-editable">
            <div  class="coral-Form-fieldwrapper foundation-field-edit"><label class="coral-Form-fieldlabel">Text for Related Item</label><input maxlength="255" class="coral-Form-field" data-metaType="text" type="text" name="./jcr:content/metadata/brc_link_text" value="<%=map.get("brc_link_text","")%>" data-foundation-validation="" data-validation="" is="coral-textfield"></div>
        </div>

        <div  class="coral-Form-fieldwrapper foundation-field-edit">
            <label class="coral-Form-fieldlabel">Projection</label>
            <coral-select class="coral-Form-field" data-metaType="dropdown" name="./jcr:content/metadata/brc_projection" data-foundation-validation="" data-validation="" >
                <coral-select-item <%="".equals(map.get("brc_projection","")) ? "selected='selected'" : ""%> value="">Standard</coral-select-item>
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

<div class="aem-assets-metadata-form-column brc-variants-section" data-asset-path="<%=org.apache.commons.lang3.StringEscapeUtils.escapeHtml4(asset_res.getPath())%>" style="width:100%;margin-top:16px;">
    <div class="coral-Form-fieldwrapper">
        <label class="coral-Form-fieldlabel">Variants</label>
        <div class="brc-variants-list" style="display:flex;flex-wrap:wrap;gap:8px;margin-bottom:8px;">
            <%
                if (brcVariantsRaw.length == 0) {
            %>
            <span class="coral-Form-fielddescription">No variants configured.</span>
            <%
                } else {
                    for (int vIdx = 0; vIdx < brcVariantsRaw.length; vIdx++) {
                        String variantLang = "";
                        try {
                            JSONObject variantObj = new JSONObject(brcVariantsRaw[vIdx]);
                            variantLang = variantObj.optString("language", "");
                        } catch (Exception ex) { /* skip malformed entry */ }
                        if (variantLang.isEmpty()) continue;
            %>
            <span class="brc-variant-badge" style="display:inline-flex;align-items:center;gap:6px;padding:4px 10px;background:#e8f0fe;border-radius:14px;font-size:12px;">
                <span class="brc-variant-language"><%=org.apache.commons.lang3.StringEscapeUtils.escapeHtml4(variantLang)%></span>
                <button is="coral-button" variant="minimal" size="S"
                        class="brc-edit-variant-btn"
                        data-language="<%=org.apache.commons.lang3.StringEscapeUtils.escapeHtml4(variantLang)%>"
                        data-variant-index="<%=vIdx%>"
                        data-video-id="<%=brcid%>"
                        data-account-id="<%=org.apache.commons.lang3.StringEscapeUtils.escapeHtml4(map.get("brc_account_id",""))%>"
                        type="button">Edit</button>
                <button is="coral-button" variant="minimal" size="S"
                        class="brc-delete-variant-btn"
                        data-language="<%=org.apache.commons.lang3.StringEscapeUtils.escapeHtml4(variantLang)%>"
                        data-video-id="<%=brcid%>"
                        data-account-id="<%=org.apache.commons.lang3.StringEscapeUtils.escapeHtml4(map.get("brc_account_id",""))%>"
                        type="button">Delete</button>
            </span>
            <%
                    }
                }
            %>
        </div>
        <button is="coral-button" variant="secondary" size="S"
                class="brc-add-variant-btn"
                data-video-id="<%=brcid%>"
                data-account-id="<%=org.apache.commons.lang3.StringEscapeUtils.escapeHtml4(map.get("brc_account_id",""))%>"
                type="button">+ Add Variant</button>
    </div>
</div>

<coral-dialog id="brc-variant-dialog" closable="on" size="L">
    <coral-dialog-header>Variant</coral-dialog-header>
    <coral-dialog-content>
        <div class="coral-Form coral-Form--vertical" style="padding:0;">
            <div id="brc-variant-error" style="display:none;color:#d9534f;margin-bottom:8px;"></div>
            <div class="coral-Form-fieldwrapper">
                <label class="coral-Form-fieldlabel" for="brc-variant-language">Language Code <span style="color:#d9534f;">*</span></label>
                <input id="brc-variant-language" is="coral-textfield" class="coral-Form-field" type="text" placeholder="e.g. fr, de, es">
            </div>
            <div class="coral-Form-fieldwrapper">
                <label class="coral-Form-fieldlabel" for="brc-variant-name">Name</label>
                <input id="brc-variant-name" is="coral-textfield" class="coral-Form-field" type="text">
            </div>
            <div class="coral-Form-fieldwrapper">
                <label class="coral-Form-fieldlabel" for="brc-variant-description">Short Description</label>
                <input id="brc-variant-description" is="coral-textfield" class="coral-Form-field" type="text" maxlength="250">
            </div>
            <div class="coral-Form-fieldwrapper">
                <label class="coral-Form-fieldlabel" for="brc-variant-long-description">Long Description</label>
                <textarea id="brc-variant-long-description" is="coral-textarea" class="coral-Form-field" maxlength="5000"></textarea>
            </div>
            <%
                if (custom_fields_arr.length() > 0) {
                    for (int dlgZ = 0; dlgZ < custom_fields_arr.length(); dlgZ++) {
                        JSONObject dlgCf = custom_fields_arr.getJSONObject(dlgZ);
                        String dlgFieldTitle = dlgCf.getString("display_name");
                        String dlgFieldId    = dlgCf.getString("id");
                        String dlgFieldType  = dlgCf.getString("type");
                        boolean dlgRequired  = dlgCf.getBoolean("required");
                        if (dlgFieldType.equals("enum")) {
                            JSONArray dlgEnums = dlgCf.getJSONArray("enum_values");
            %>
            <div class="coral-Form-fieldwrapper">
                <label class="coral-Form-fieldlabel"><b><%=dlgFieldTitle%><%=dlgRequired ? " *":""%></b></label>
                <coral-select class="coral-Form-field brc-variant-cf" data-cf-id="<%=dlgFieldId%>">
                    <% if (!dlgRequired) { %>
                    <coral-select-item value=""></coral-select-item>
                    <% } %>
                    <% for (int dlgX = 0; dlgX < dlgEnums.length(); dlgX++) { %>
                    <coral-select-item value="<%=dlgEnums.getString(dlgX)%>"><%=dlgEnums.getString(dlgX)%></coral-select-item>
                    <% } %>
                </coral-select>
            </div>
            <%
                        } else {
            %>
            <div class="coral-Form-fieldwrapper">
                <label class="coral-Form-fieldlabel"><b><%=dlgFieldTitle%><%=dlgRequired ? " *":""%></b></label>
                <input is="coral-textfield" class="coral-Form-field brc-variant-cf" type="text" data-cf-id="<%=dlgFieldId%>">
            </div>
            <%
                        }
                    }
                }
            %>
        </div>
    </coral-dialog-content>
    <coral-dialog-footer>
        <button is="coral-button" variant="default" coral-close type="button">Cancel</button>
        <button id="brc-variant-save" is="coral-button" variant="primary" type="button">Save</button>
    </coral-dialog-footer>
</coral-dialog>
<%
    } else {
%>
<div  class="aem-assets-metadata-form-column" style="width: 100%;">
    <div class="foundation-field-editable">
        <div  class="coral-Form-fieldwrapper foundation-field-edit">
            <label data-metatype="section" class="coral-Form-fieldlabel">
                <h3>Notice</h3>
                <span>
                    This resource is not managed by Brightcove and does not have any associated Brightcove metadata.
                </span>
            </label>
        </div>
    </div>
</div>
<%
    }
%>





