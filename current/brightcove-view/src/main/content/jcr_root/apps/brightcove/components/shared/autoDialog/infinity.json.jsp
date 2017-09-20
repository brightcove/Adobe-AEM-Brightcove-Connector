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

        //ENTIRE OBJECT
        JSONObject obj = new JSONObject("{'jcr:primaryType':'cq:Widget','collapsible':false,'title':'Brightcove Asset Metadata','xtype':'dialogfieldset','collapsed':false,'items':{'jcr:primaryType':'cq:WidgetCollection','title':{'jcr:primaryType':'cq:Widget','allowBlank':'true','name':'./dc:title','xtype':'textfield','fieldLabel':'Title'},'description':{'jcr:primaryType':'cq:Widget','allowBlank':'true','name':'./brc_description','xtype':'textfield','fieldLabel':'Short Description'},'long_description':{'jcr:primaryType':'cq:Widget','name':'./brc_long_description','xtype':'textarea','fieldLabel':'Long Description'},'economics':{'jcr:primaryType':'cq:Widget','name':'./brc_economics','allowBlank':'true','type':'select','xtype':'selection','fieldLabel':'Economics','defaultValue':'AD_SUPPORTED','value':'AD_SUPPORTED','options':{'jcr:primaryType':'cq:WidgetCollection','ad_enabled':{'jcr:primaryType':'nt:unstructured','text':'Ad Enabled','value':'AD_SUPPORTED'},'ad_disabled':{'jcr:primaryType':'nt:unstructured','text':'Free','value':'FREE'}}},'updated_at':{'jcr:primaryType':'cq:Widget','name':'./brc_updated_at','readOnly':'{Boolean}true','xtype':'textfield','fieldLabel':'Last Updated'},'created_at':{'jcr:primaryType':'cq:Widget','name':'./brc_created_at','readOnly':'{Boolean}true','xtype':'textfield','fieldLabel':'Date Published'},'duration':{'jcr:primaryType':'cq:Widget','readOnly':'{Boolean}true','name':'./brc_duration','xtype':'textfield','fieldLabel':'Duration'},'id':{'jcr:primaryType':'cq:Widget','readOnly':'{Boolean}true','name':'./brc_id','xtype':'textfield','fieldLabel':'Video ID'},'reference_id':{'jcr:primaryType':'cq:Widget','name':'./brc_reference_id','xtype':'textfield','fieldLabel':'Reference ID'}}}");

        //OBJECT MUST BE MODIFIED TO RETURN A NEW ORDER AND GRAY FIELDS FOR IMMUTABLE
        //Note: ,'cls':'gray-backround' - for adding CSS to this would imply OOTB mod - Pablo

%>



<%=obj.toString(1)%>



