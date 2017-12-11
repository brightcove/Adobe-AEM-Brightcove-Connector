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
<%@ page import="org.apache.sling.commons.json.JSONObject" %>
<%@ page trimDirectiveWhitespaces="true" %>
<%@include file="/libs/foundation/global.jsp" %>
<%
        //CLASIC UI RENDER
        response.setContentType("application/json");

        //ENTIRE OBJECT
        JSONObject obj = new JSONObject("{'jcr:primaryType':'cq:Widget','collapsible':false,'title':'Brightcove Asset Metadata','xtype':'dialogfieldset','collapsed':false,'items':{'jcr:primaryType':'cq:WidgetCollection','title':{'jcr:primaryType':'cq:Widget','allowBlank':'true','name':'./dc:title','xtype':'textfield','fieldLabel':'Title'},'description':{'jcr:primaryType':'cq:Widget','allowBlank':'true','name':'./brc_description','xtype':'textfield','fieldLabel':'Short Description'},'long_description':{'jcr:primaryType':'cq:Widget','name':'./brc_long_description','xtype':'textarea','fieldLabel':'Long Description'},'link_url':{'jcr:primaryType':'cq:Widget','allowBlank':'true','name':'./brc_link_url','xtype':'textfield','fieldLabel':'Link to Related Item'},'link_text':{'jcr:primaryType':'cq:Widget','allowBlank':'true','name':'./brc_link_text','xtype':'textfield','fieldLabel':'Text for Related Item'},'projection':{'jcr:primaryType':'cq:Widget','name':'./brc_projection','allowBlank':'true','type':'select','xtype':'selection','fieldLabel':'Projection','defaultValue':'','value':'','options':{'jcr:primaryType':'cq:WidgetCollection','proj_std':{'jcr:primaryType':'nt:unstructured','text':'Standard','value':''},'project_360':{'jcr:primaryType':'nt:unstructured','text':'360 Degree','value':'equirectangular'}}},'economics':{'jcr:primaryType':'cq:Widget','name':'./brc_economics','allowBlank':'true','type':'select','xtype':'selection','fieldLabel':'Economics','defaultValue':'AD_SUPPORTED','value':'AD_SUPPORTED','options':{'jcr:primaryType':'cq:WidgetCollection','ad_enabled':{'jcr:primaryType':'nt:unstructured','text':'Ad Enabled','value':'AD_SUPPORTED'},'ad_disabled':{'jcr:primaryType':'nt:unstructured','text':'Free','value':'FREE'}}},'updated_at':{'jcr:primaryType':'cq:Widget','name':'./brc_updated_at','readOnly':'{Boolean}true','xtype':'textfield','fieldLabel':'Last Updated'},'created_at':{'jcr:primaryType':'cq:Widget','name':'./brc_created_at','readOnly':'{Boolean}true','xtype':'textfield','fieldLabel':'Date Published'},'duration':{'jcr:primaryType':'cq:Widget','readOnly':'{Boolean}true','name':'./brc_duration','xtype':'textfield','fieldLabel':'Duration'},'id':{'jcr:primaryType':'cq:Widget','readOnly':'{Boolean}true','name':'./brc_id','xtype':'textfield','fieldLabel':'Video ID'},'reference_id':{'jcr:primaryType':'cq:Widget','name':'./brc_reference_id','xtype':'textfield','fieldLabel':'Reference ID'}}}");

%>
<%=obj.toString(1)%>



