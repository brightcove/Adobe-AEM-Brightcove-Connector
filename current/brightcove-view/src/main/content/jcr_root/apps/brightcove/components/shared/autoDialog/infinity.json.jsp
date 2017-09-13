<%@ page import="org.apache.sling.commons.json.JSONObject" %>
<%@ page import="com.coresecure.brightcove.wrapper.sling.ConfigurationGrabber" %>
<%@ page import="com.coresecure.brightcove.wrapper.sling.ConfigurationService" %>
<%@ page import="com.coresecure.brightcove.wrapper.sling.ServiceUtil" %>
<%@ page import="org.apache.sling.commons.json.JSONArray" %>
<%@ page import="com.coresecure.brightcove.wrapper.utils.AccountUtil" %>
<%@ page import="org.slf4j.LoggerFactory" %>
<%@ page import="java.util.Iterator" %>
<%@ page import="org.apache.sling.commons.json.JSONException" %>
<%@ page import="org.apache.sling.api.resource.ModifiableValueMap" %>
<%@ page import="java.util.Arrays" %>
<%@ page import="java.util.Properties" %>
<%@ page trimDirectiveWhitespaces="true" %>
<%@include file="/libs/foundation/global.jsp" %>
<%
        response.setContentType("application/json");

        ConfigurationGrabber cg = ServiceUtil.getConfigurationGrabber();
        String requestedAccount = AccountUtil.getSelectedAccount(slingRequest);
        ConfigurationService cs = cg.getConfigurationService(requestedAccount);
        ServiceUtil serviceUtil = new ServiceUtil(requestedAccount);
        JSONObject api_obj = serviceUtil.getCustomFields();

        //ENTIRE OBJECT
        JSONObject obj = new JSONObject("{\"jcr:primaryType\":\"cq:Widget\",\"collapsible\":false,\"title\":\"Brightcove\",\"xtype\":\"dialogfieldset\",\"collapsed\":false,\"items\":{\"jcr:primaryType\":\"cq:WidgetCollection\",\"title\":{\"jcr:primaryType\":\"cq:Widget\",\"allowBlank\":\"false\",\"name\":\"./brc_name\",\"xtype\":\"textfield\",\"fieldLabel\":\"Title\"},\"updated_at\":{\"jcr:primaryType\":\"cq:Widget\",\"name\":\"./brc_updated_at\",\"readOnly\":\"{Boolean}true\",\"xtype\":\"textfield\",\"fieldLabel\":\"Last Updated\"},\"created_at\":{\"jcr:primaryType\":\"cq:Widget\",\"name\":\"./brc_created_at\",\"readOnly\":\"{Boolean}true\",\"xtype\":\"textfield\",\"fieldLabel\":\"Date Published\"},\"duration\":{\"jcr:primaryType\":\"cq:Widget\",\"readOnly\":\"{Boolean}true\",\"name\":\"./brc_duration\",\"xtype\":\"textfield\",\"fieldLabel\":\"Duration\"},\"description\":{\"jcr:primaryType\":\"cq:Widget\",\"allowBlank\":\"true\",\"name\":\"./brc_description\",\"xtype\":\"textfield\",\"fieldLabel\":\"Short Description\"},\"long_description\":{\"jcr:primaryType\":\"cq:Widget\",\"name\":\"./brc_long_description\",\"xtype\":\"textfield\",\"fieldLabel\":\"Long Description\"},\"id\":{\"jcr:primaryType\":\"cq:Widget\",\"readOnly\":\"{Boolean}true\",\"name\":\"./brc_id\",\"xtype\":\"textfield\",\"fieldLabel\":\"Video ID\"},\"reference_id\":{\"jcr:primaryType\":\"cq:Widget\",\"name\":\"./brc_reference_id\",\"xtype\":\"textfield\",\"fieldLabel\":\"Reference ID\"},\"economics\":{\"jcr:primaryType\":\"cq:Widget\",\"name\":\"./brc_economics\",\"allowBlank\":\"false\",\"type\":\"select\",\"xtype\":\"selection\",\"fieldLabel\":\"Economics\",\"options\":{\"jcr:primaryType\":\"cq:WidgetCollection\",\"ad_enabled\":{\"jcr:primaryType\":\"nt:unstructured\",\"text\":\"Ad Enabled\",\"value\":\"AD_SUPPORTED\"},\"ad_disabled\":{\"jcr:primaryType\":\"nt:unstructured\",\"text\":\"Free\",\"value\":\"FREE\"}}}}}");
        //JSONObject options = new JSONObject("{\"jcr:primaryType\":\"cq:Widget\",\"name\":\"./brc_economics\",\"type\":\"select\",\"xtype\":\"selection\",\"fieldLabel\":\"Economics\",\"options\":{\"jcr:primaryType\":\"cq:WidgetCollection\",\"ad_enabled\":{\"jcr:primaryType\":\"nt:unstructured\",\"text\":\"Ad Enabled\",\"value\":\"AD_SUPPORTED\"},\"ad_disabled\":{\"jcr:primaryType\":\"nt:unstructured\",\"text\":\"Free\",\"value\":\"FREE\"}}}");

        





        //{\"jcr:primaryType\":\"cq:Widget\",\"collapsible\":false,\"title\":\"Brightcove\",\"xtype\":\"dialogfieldset\",\"collapsed\":false,\"items\":{\"jcr:primaryType\":\"cq:WidgetCollection\",\"title\":{\"jcr:primaryType\":\"cq:Widget\",\"allowBlank\":\"false\",\"name\":\"./brc_name\",\"xtype\":\"textfield\",\"fieldLabel\":\"Title\"},\"created_at\":{\"jcr:primaryType\":\"cq:Widget\",\"name\":\"./brc_created_at\",\"xtype\":\"textfield\",\"fieldLabel\":\"Date Published\"},\"duration\":{\"jcr:primaryType\":\"cq:Widget\",\"name\":\"./brc_duration\",\"xtype\":\"textfield\",\"fieldLabel\":\"Duration\"},\"id\":{\"jcr:primaryType\":\"cq:Widget\",\"name\":\"./brc_id\",\"xtype\":\"textfield\",\"fieldLabel\":\"videoID\"},\"description\":{\"jcr:primaryType\":\"cq:Widget\",\"name\":\"./brc_description\",\"xtype\":\"textfield\",\"fieldLabel\":\"Short Description\"},\"long_description\":{\"jcr:primaryType\":\"cq:Widget\",\"name\":\"./brc_long_description\",\"xtype\":\"textfield\",\"fieldLabel\":\"Long Description\"},\"reference_id\":{\"jcr:primaryType\":\"cq:Widget\",\"name\":\"./brc_reference_id\",\"xtype\":\"textfield\",\"fieldLabel\":\"Reference ID\"},\"economics\":{\"jcr:primaryType\":\"cq:Widget\",\"name\":\"./brc_economics\",\"type\":\"select\",\"xtype\":\"selection\",\"fieldLabel\":\"Economics\",\"options\":{\"jcr:primaryType\":\"cq:WidgetCollection\",\"ad_enabled\":{\"jcr:primaryType\":\"nt:unstructured\",\"text\":\"Ad Enabled\",\"value\":\"AD_SUPPORTED\"},\"ad_disabled\":{\"jcr:primaryType\":\"nt:unstructured\",\"text\":\"Free\",\"value\":\"FREE\"}}}}}");

                //"readOnly":"{Boolean}true",  readOnly="{Boolean}true"

%>
<%--<%--%>
    <%--//EACH KEY IS A CUSTOM FIELD - EACH custom_field_props.get(key) = VALUE--%>



    <%--JSONArray api_custom_fields = api_obj!= null ? api_obj.getJSONArray("custom_fields") : null;--%>

    <%--if(api_custom_fields != null)--%>
    <%--{--%>



    <%--for(int cnt = 0; cnt < api_custom_fields.length(); cnt++)--%>
    <%--{--%>

        <%--JSONObject field = api_custom_fields.getJSONObject(cnt);--%>
        <%--JSONObject this_item = new JSONObject();--%>
        <%--//CHECK TYPE FROM API--%>
        <%--String type = field.getString("type");--%>

        <%--if(type.equals("enum"))--%>
        <%--{--%>
            <%--//SUBLEVEL CASES - FIXED FOR TOUCH UI--%>
            <%--////////////////////////////////////////////--%>
            <%--//CASE TWO - ENUM--%>
            <%--this_item.put("jcr:primaryType", "cq:Widget");--%>
            <%--this_item.put("name", "./brc_custom_fields/" + field.getString("id"));--%>
            <%--this_item.put("type", "select");--%>
            <%--this_item.put("xtype", "selection");--%>
            <%--this_item.put("fieldLabel", field.getString("display_name"));--%>

            <%--////////////--%>
            <%--JSONObject select_options = new JSONObject();--%>
            <%--select_options.put("jcr:primaryType",  "cq:WidgetCollection");--%>
            <%--JSONArray enumArray = field.getJSONArray("enum_values");--%>
            <%--for(int c = 0 ; c < enumArray.length() ; c++)--%>
            <%--{--%>
                <%--String enumObj = enumArray.getString(c);--%>
                <%--JSONObject opt = new JSONObject();--%>
                <%--opt.put("jcr:primaryType", "nt:unstructured");--%>
                <%--opt.put("text",enumObj);--%>
                <%--opt.put("value", enumObj);--%>
                <%--select_options.put(enumObj.replace(" ","_"),opt);--%>
            <%--}--%>
            <%--this_item.put("options",select_options);--%>
        <%--}--%>
        <%--else--%>
        <%--{--%>
            <%--////////////////////////////////////////////--%>
            <%--//CASE ONE SIMPLE TEXTFIELD--%>
            <%--this_item.put("jcr:primaryType", "cq:Widget");--%>
            <%--this_item.put("name", "./brc_custom_fields/" + field.getString("id"));--%>
            <%--this_item.put("xtype", "textfield");--%>
            <%--this_item.put("fieldLabel", field.getString("display_name"));--%>
            <%--//            this_item.put("required", false);--%>
            <%--////////////////////////////////////////////--%>
        <%--}--%>


        <%--obj.getJSONObject("items").put(field.getString("id"), this_item);--%>


        <%--%>--%>
<%--&lt;%&ndash;<li><%=key%>:<%=custom_fields_props.get(key)%></li>&lt;%&ndash;%>--%>


            <%--&lt;%&ndash;<%=api_obj.toString(1)%>&ndash;%&gt;--%>

<%--<%--%>

             <%--}--%>


    <%--}--%>
<%--%>--%>





<%--&lt;%&ndash;<h1>PropertiesEnd</h1>&ndash;%&gt;--%>


<%--&lt;%&ndash;<%=this_item.toString(1)%>&ndash;%&gt;--%>
<%--<%--%>



<%--%>--%>

<%--<%=obj.toString(0)%>--%>


<%=obj.toString(1)%>



