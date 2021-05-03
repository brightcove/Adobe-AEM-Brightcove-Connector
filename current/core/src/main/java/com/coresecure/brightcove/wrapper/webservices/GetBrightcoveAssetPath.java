/*

    Adobe AEM Brightcove Connector

    Copyright (C) 2020 3|Share Corp.

    Authors:    Arvind Ragiphani

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


package com.coresecure.brightcove.wrapper.webservices;

import com.coresecure.brightcove.wrapper.sling.ConfigurationGrabber;
import com.coresecure.brightcove.wrapper.sling.ConfigurationService;
import com.coresecure.brightcove.wrapper.sling.ServiceUtil;
import com.coresecure.brightcove.wrapper.utils.TextUtil;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.models.annotations.injectorspecific.OSGiService;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.servlet.ServletException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

@Service
@Component
@Properties(value = {
        @Property(name = "sling.servlet.extensions", value = {"json"}),
        @Property(name = "sling.servlet.paths", value = {"/bin/brightcove/getBrightcoveAssetPath"})
})
public class GetBrightcoveAssetPath extends SlingAllMethodsServlet {

    @OSGiService
    ConfigurationGrabber cg;

    private static final Logger LOGGER = LoggerFactory.getLogger(GetBrightcoveAssetPath.class);

    Set<String> services;

    @Override
    protected void doGet(final SlingHttpServletRequest request,
                         final SlingHttpServletResponse response) throws ServletException,
            IOException {
        getBrightcoveAssetsPath(request, response);

    }


    public void getBrightcoveAssetsPath(final SlingHttpServletRequest request,
                    final SlingHttpServletResponse response) throws ServletException,
            IOException {
        PrintWriter outWriter = response.getWriter();
        response.setContentType("application/json");
        JSONObject root = new JSONObject();

        try {
            ConfigurationGrabber cg = ServiceUtil.getConfigurationGrabber();

            String brcAccountId = request.getParameter("account_id");
            
            if ( brcAccountId == null || brcAccountId.length() == 0 ) {
                brcAccountId = ServiceUtil.getAccountFromCookie(request);
            }

            if ( brcAccountId == null || brcAccountId.length() == 0 ) {
                services = cg.getAvailableServices(request);

                if ( services.size() > 0 ) {
                    brcAccountId = (String) services.toArray()[0];
                }
            }

            LOGGER.debug("brcAccountId:", brcAccountId);
            
            ConfigurationService brcService = cg.getConfigurationService(brcAccountId);
            
            String assetsPath = brcService.getAssetIntegrationPath();
            LOGGER.debug("Brightcove Videos Asset Path is {}", assetsPath);

            root.put("brightcoveAssetPath", assetsPath);
            outWriter.write(root.toString(1));
        } catch (JSONException e) {
            LOGGER.error("JSONException", e);
            outWriter.write("{\"accounts\":[],\"error\":\"" + e.getMessage() + "\"}");
        }

    }

}