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
                 com.coresecure.brightcove.wrapper.utils.TextUtil,
                 org.apache.sling.commons.json.JSONArray,
                 org.apache.sling.commons.json.JSONObject" %>
<%@ page import="java.util.Iterator" %>

<%@include file="/libs/foundation/global.jsp" %>

<%
    JSONObject root = new JSONObject();
    JSONArray items = new JSONArray();

    ConfigurationGrabber cg = ServiceUtil.getConfigurationGrabber();
    String defaultAccount = (String) cg.getAvailableServices().toArray()[0];
    ConfigurationService cs = cg.getConfigurationService(defaultAccount);

    String playersPath = cs.getPlayersLoc();
    Resource res = resourceResolver.resolve(playersPath);
    Iterator<Resource> playersItr = res.listChildren();
    String selectedAccount = request.getParameter("account_id");
    if (TextUtil.notEmpty(selectedAccount)) {
        while (playersItr.hasNext()) {
            Page playerRes = playersItr.next().adaptTo(Page.class);
            if (playerRes != null && "brightcove/components/page/brightcoveplayer".equals(playerRes.getContentResource().getResourceType())) {
                JSONObject item = new JSONObject();
                String path = playerRes.getPath();
                String title = playerRes.getTitle();
                String account = playerRes.getProperties().get("account", "");
                if (TextUtil.notEmpty(account) && account.equals(selectedAccount)) {
                    item.put("id", path);
                    item.put("name", title);
//                item.put("thumbnailURL", path);

                    items.put(item);
                }
            }
        }
    }

    root.put("items", items);
    root.put("results", items.length());
    out.write(root.toString());
%>
