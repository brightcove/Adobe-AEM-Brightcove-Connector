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

<%@ page import="com.day.cq.i18n.I18n" %>
<%@ page import="com.day.cq.wcm.api.WCMMode" %>
<%@ page import="java.util.ResourceBundle" %>
<%@ page import="com.day.cq.wcm.foundation.Placeholder" %>
<%@ page import="com.adobe.granite.license.ProductInfoService" %>
<%@ page import="com.adobe.granite.license.ProductInfo" %>
<%@include file="/libs/foundation/global.jsp" %>
<%
    // Localization
    ProductInfoService productInfo = sling.getService(ProductInfoService.class);
    String version ="";
    if(productInfo != null) {
        ProductInfo[] productInfos =  productInfo.getInfos();
        if (productInfos.length > 0) {
            version = productInfos[0].getShortVersion();
        }

    }

    final ResourceBundle resourceBundle = slingRequest.getResourceBundle(null);
    I18n i18n = new I18n(resourceBundle);

    // WCMMode

    WCMMode currentWCMMode = WCMMode.fromRequest(request);

    boolean isEditMode = (currentWCMMode == WCMMode.EDIT);
    boolean isTouchUI= Placeholder.isAuthoringUIModeTouch(slingRequest);
    boolean isDesignMode = (currentWCMMode == WCMMode.DESIGN);
    boolean isEditOrDesignMode = (isEditMode || isDesignMode);


    //Update Page Context
    pageContext.setAttribute("aemversion", version);
    pageContext.setAttribute("isTouchUI", isTouchUI);
    pageContext.setAttribute("isEditMode", isEditMode);
    pageContext.setAttribute("isDesignMode", isEditMode);
    pageContext.setAttribute("isEditOrDesignMode", isEditOrDesignMode);
%>