<% %>
    <%@include file="/libs/granite/ui/global.jsp" %>
        <% %>
            <%@ page session="false" contentType="text/html" pageEncoding="utf-8" import="com.adobe.granite.ui.components.formbuilder.FormResourceManager,
         		 org.apache.sling.api.resource.Resource,
         		 org.apache.sling.api.resource.ValueMap,
				 com.adobe.granite.ui.components.Config,
         		 java.util.HashMap" %>
                <% ValueMap fieldProperties=resource.adaptTo(ValueMap.class); String key=resource.getName(); String
                    resourcePathBase="dam/gui/coral/components/admin/schemaforms/formbuilder/formfieldproperties/" ; %>
                    <div class="formbuilder-content-form" role="gridcell">
                        <label class="fieldtype">
                            <coral-icon alt="" icon="users" size="XS"></coral-icon>
                            <%= xssAPI.encodeForHTML(i18n.get("Authorizable")) %>
                        </label>
                        <sling:include resource="<%=resource%>"
                            resourceType="granite/ui/components/coral/foundation/authorizable/autocomplete" />
                    </div>
                    <div class="formbuilder-content-properties">

                        <input type="hidden" name="<%= xssAPI.encodeForHTMLAttr(" ./items/" + key) %>">
                        <input type="hidden" name="<%= xssAPI.encodeForHTMLAttr(" ./items/" + key + "/jcr:primaryType" )
                            %>" value="nt:unstructured">
                        <input type="hidden" name="<%= xssAPI.encodeForHTMLAttr(" ./items/" + key + "/resourceType" )
                            %>" value="granite/ui/components/coral/foundation/authorizable/autocomplete">
                        <input type="hidden" name="<%= xssAPI.encodeForHTMLAttr(" ./items/" + key
                            + "/sling:resourceType" ) %>" value="dam/gui/components/admin/schemafield">
                        <input type="hidden" name="<%= xssAPI.encodeForHTMLAttr(" ./items/" + key + "/multiple@TypeHint"
                            ) %>" value="Boolean">
                        <input type="hidden" name="<%= xssAPI.encodeForHTMLAttr(" ./items/" + key + "/valueType" ) %>"
                        value="principalname">
                        <input type="hidden" name="<%= xssAPI.encodeForHTMLAttr(" ./items/" + key + "/forceSelection" )
                            %>" value="true">
                        <input type="hidden" name="<%= xssAPI.encodeForHTMLAttr(" ./items/" + key
                            + "/forceSelection@TypeHint" ) %>" value="Boolean">
                        <input type="hidden" name="<%= xssAPI.encodeForHTMLAttr(" ./items/" + key
                            + "/granite:data/metaType" ) %>" value="authorizable">

                        <% String[] settingsList={"labelfields", "metadatamappertextfield" , "titlefields" }; for(String
                            settingComponent : settingsList){ %>
                            <sling:include resource="<%= resource %>"
                                resourceType="<%= resourcePathBase + settingComponent %>" />
                            <% } %>

                                <% FormResourceManager formResourceManager=sling.getService(FormResourceManager.class);
                                    HashMap<String, Object> values = new HashMap<String, Object>();
                                        values.put("granite:class", "checkbox-label");
                                        values.put("text", i18n.get("allow_multiple_selection"));
                                        values.put("value", "true");
                                        values.put("checked", fieldProperties.get("multiple", false));
                                        values.put("name", "./items/" + key + "/multiple");
                                        Resource multipleSelectionResource =
                                        formResourceManager.getDefaultPropertyFieldResource(resource, values);
                                        %>
                                        <sling:include resource="<%= multipleSelectionResource %>"
                                            resourceType="granite/ui/components/coral/foundation/form/checkbox" />

                                        <div class="coral-Form-fieldwrapper">
                                            <label class="coral-Form-fieldlabel" id="label-vertical-radiogroup-0">
                                                <%= i18n.get("authorizable_selection_options") %>
                                            </label>
                                            <% String selector=fieldProperties.get("selector", "" ); %>
                                                <div
                                                    class="coral-Form-field coral-RadioGroup coral-RadioGroup--vertical">
                                                    <coral-radio class="coral-Form-field"
                                                        name="<%= xssAPI.encodeForHTMLAttr(" ./items/" + key
                                                        + "/selector" ) %>" value="all"
                                                        labelledby="label-vertical-radiogroup-0" <%= "all"
                                                            .equals(selector) ? "checked" : "" %> > <%=
                                                                i18n.get("allow_both_users_and_groups") %>
                                                    </coral-radio>
                                                    <coral-radio class="coral-Form-field"
                                                        name="<%= xssAPI.encodeForHTMLAttr(" ./items/" + key
                                                        + "/selector" ) %>" value="user"
                                                        labelledby="label-vertical-radiogroup-0" <%= "user"
                                                            .equals(selector) ? "checked" : "" %> > <%=
                                                                i18n.get("allow_only_users") %> </coral-radio>
                                                    <coral-radio class="coral-Form-field"
                                                        name="<%= xssAPI.encodeForHTMLAttr(" ./items/" + key
                                                        + "/selector" ) %>" value="group"
                                                        labelledby="label-vertical-radiogroup-0" <%= "group"
                                                            .equals(selector) ? "checked" : "" %> > <%=
                                                                i18n.get("allow_only_groups") %> </coral-radio>
                                                </div>
                                        </div>

                                        <div class="coral-Form-fieldwrapper">
                                            <label class="coral-Form-fieldlabel" id="label-vertical-radiogroup-1">
                                                <%= i18n.get("authorizable_validation_options") %>
                                            </label>
                                            <% String validation=fieldProperties.get("validation", "" ); %>
                                                <div
                                                    class="coral-Form-field coral-RadioGroup coral-RadioGroup--vertical">
                                                    <coral-radio class="coral-Form-field"
                                                        name="<%= xssAPI.encodeForHTMLAttr(" ./items/" + key
                                                        + "/validation" ) %>" value=""
                                                        labelledby="label-vertical-radiogroup-1" <%= ""
                                                            .equals(validation) ? "checked" : "" %> > <%=
                                                                i18n.get("brightcove_authorizable_validation_options_none")
                                                                %> </coral-radio>
                                                </div>
                                        </div>

                                        <coral-icon class="delete-field" icon="delete" size="L" tabindex="0"
                                            role="button" alt="<%= xssAPI.encodeForHTMLAttr(i18n.get(" Delete")) %>"
                                            data-target-id="<%= xssAPI.encodeForHTMLAttr(key) %>" data-target="<%=
                                                    xssAPI.encodeForHTMLAttr("./items/" + key + "@Delete" ) %>
                                                    "></coral-icon>

                    </div>
                    <div class="formbuilder-content-properties-rules">
                        <label for="field">
                            <span class="rules-label">
                                <%= i18n.get("Field") %>
                            </span>
                            <% String[] fieldRulesList={"disableineditmodefields", "showemptyfieldinreadonly" };
                                for(String ruleComponent : fieldRulesList){ %>
                                <sling:include resource="<%= resource %>"
                                    resourceType="<%= resourcePathBase + ruleComponent %>" />
                                <% } %>
                        </label>
                        <label for="requirement">
                            <span class="rules-label">
                                <%= i18n.get("Requirement") %>
                            </span>
                            <div class="coral-Form-fieldwrapper">
                                <label class="coral-Form-fieldlabel" id="label-vertical-radiogroup-1"></label>
                                <% boolean required=fieldProperties.get("required", false); %>
                                    <div class="coral-Form-field coral-RadioGroup coral-RadioGroup--vertical">
                                        <coral-radio class="coral-Form-field" name="<%= xssAPI.encodeForHTMLAttr("
                                            ./items/" + key + "/required" ) %>" value="false"
                                            labelledby="label-vertical-radiogroup-1" <%= !required ? "checked" : "" %> >
                                                <%= i18n.get("Not Required") %> </coral-radio>
                                        <coral-radio class="coral-Form-field" name="<%= xssAPI.encodeForHTMLAttr("
                                            ./items/" + key + "/required" ) %>" value="true"
                                            labelledby="label-vertical-radiogroup-1" <%= required ? "checked" : "" %> >
                                                <%= i18n.get("Required") %> </coral-radio>
                                    </div>
                            </div>
                        </label>
                    </div>