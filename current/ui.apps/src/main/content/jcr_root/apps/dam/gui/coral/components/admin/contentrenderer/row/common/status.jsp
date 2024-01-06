<%--
  ADOBE CONFIDENTIAL

  Copyright 2013 Adobe Systems Incorporated
  All Rights Reserved.

  NOTICE:  All information contained herein is, and remains
  the property of Adobe Systems Incorporated and its suppliers,
  if any.  The intellectual and technical concepts contained
  herein are proprietary to Adobe Systems Incorporated and its
  suppliers and may be covered by U.S. and Foreign Patents,
  patents in process, and are protected by trade secret or copyright law.
  Dissemination of this information or reproduction of this material
  is strictly forbidden unless prior written permission is obtained
  from Adobe Systems Incorporated.
--%>
 <%@page session="false"%><%
 %><%@page import="javax.jcr.Node,
        com.day.cq.i18n.I18n,
		org.apache.sling.api.resource.Resource,
		org.apache.sling.api.resource.ResourceResolver,
        org.apache.sling.api.resource.ResourceUtil"%><%
%><%@taglib prefix="sling" uri="http://sling.apache.org/taglibs/sling/1.0"%><%
%><%@taglib prefix="cq" uri="http://www.day.com/taglibs/cq/1.0"%><%
%><%@taglib prefix="ui" uri="http://www.adobe.com/taglibs/granite/ui/1.0"%><%
%><cq:defineObjects /><%
    I18n i18n = new I18n(slingRequest);
    String contentPath = resource.getPath() + "/jcr:content/metadata/";
    Resource res = resourceResolver.getResource(contentPath);
	String status = ResourceUtil.getValueMap(res).get("dam:status", "");
    if (status.equals("approved")) {%>
        <coral-icon icon="thumbUp" size="XS" style = "margin-left: 10px; color: #B3DA8A;" title="<%= xssAPI.encodeForHTMLAttr(i18n.get("Approved")) %>"></coral-icon>
    <%} else if(status.equals("rejected")) { %>
        <coral-icon icon="thumbDown" size="XS" style = "margin-left: 10px; color: #FA7D73;" title="<%= xssAPI.encodeForHTMLAttr(i18n.get("Rejected")) %>"></coral-icon>
	<%} else if(status.equals("inApproval")) { %>
        <coral-icon icon="viewOn" size="XS" style = "margin-left: 10px; color: #FA7D73;" title="<%= xssAPI.encodeForHTMLAttr(i18n.get("In Approval")) %>"></coral-icon>
    <%} else if(status.equals("changesRequested")) { %>
        <coral-icon icon="pending" size="XS" style = "margin-left: 10px;" title="<%= xssAPI.encodeForHTMLAttr(i18n.get("Changes Requested")) %>"></coral-icon>
<%}%>