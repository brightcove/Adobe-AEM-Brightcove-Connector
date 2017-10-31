/*

    Adobe AEM Brightcove Connector

    Copyright (C) 2017 Coresecure Inc.

    Authors:    Alessandro Bonfatti
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

 */
package com.coresecure.brightcove.wrapper.webservices;

import com.coresecure.brightcove.wrapper.sling.ServiceUtil;
import com.coresecure.brightcove.wrapper.utils.AccountUtil;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.commons.json.JSONObject;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.PrintWriter;


@Properties(value = {
        @Property(name = "sling.servlet.extensions", value = {"json"}),
        @Property(name = "sling.servlet.paths", value = "/bin/brightcove/suggestions")
})
public class BrcSuggestions extends SlingAllMethodsServlet {


    @Override
    protected void doGet(final SlingHttpServletRequest request,
                         final SlingHttpServletResponse response) throws ServletException,
            IOException {
        PrintWriter outWriter = response.getWriter();
        response.setContentType("application/json");
        JSONObject root = new JSONObject();


        int requestedAPI = 0;
        String requestedAccount = "";
        if (request.getParameter("query") != null && request.getParameter("account_id") != null) {
            requestedAccount = AccountUtil.getSelectedAccount(request);
            ServiceUtil serviceUtil = new ServiceUtil(requestedAccount);

            response.setContentType("application/json");
            if ("playlist".equalsIgnoreCase(request.getParameter("type"))) {
                outWriter.write(serviceUtil.getPlaylistByID(request.getParameter("query")).toString());
            } else {
                outWriter.write(serviceUtil.getList(false, request.getParameter("query")));
            }

        } else {
            outWriter.write("{\"items\":[],\"results\":0}");
        }

    }

}
