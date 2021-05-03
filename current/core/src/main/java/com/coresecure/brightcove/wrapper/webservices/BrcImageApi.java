/*

    Adobe AEM Brightcove Connector

    Copyright (C) 2018 Coresecure Inc.

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

import com.coresecure.brightcove.wrapper.BrightcoveAPI;
import com.coresecure.brightcove.wrapper.utils.Constants;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.propertytypes.ServiceDescription;
import javax.servlet.Servlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.servlet.ServletException;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;


@Component(service = { Servlet.class },
    property = {
        "sling.servlet.extensions=json",
        "sling.servlet.paths=/bin/brightcove/image",
        "sling.servlet.paths=/bin/services/brightcove/image",
        "sling.servlet.paths=/bin/services/brightcove/cache/image"
    }
)
@ServiceDescription("Brightcove Image Cache Servlet")
public class BrcImageApi extends SlingAllMethodsServlet {

    @Override
    protected void doPost(final SlingHttpServletRequest request,
                          final SlingHttpServletResponse response) throws ServletException,
            IOException {
        PrintWriter outWriter = response.getWriter();
        response.setStatus(404);


    }

    private String getPoster(String accountKeyStr, String VideoIDStr) throws JSONException {
        String urlStr = null;
        BrightcoveAPI brAPI = new BrightcoveAPI(accountKeyStr);

        JSONObject video = brAPI.cms.getVideoImages(VideoIDStr);


        //TODO: MODULARIZE

        // Find a single video
        if (video != null && video.has(Constants.POSTER)) {
            JSONObject poster = video.getJSONObject(Constants.POSTER);
            urlStr = poster.getString(Constants.SRC);
        }


        return urlStr;
    }

    @Override
    protected void doGet(final SlingHttpServletRequest request,
                         final SlingHttpServletResponse response) throws ServletException,
            IOException {

        int requestedAPI = 0;
        String requestedToken = "";
        Logger logger = LoggerFactory.getLogger(BrcImageApi.class);
        logger.debug("request image:");
        try {
            String VideoIDStr = "";
            String accountKeyStr = "";
            if (request.getParameter("id") != null) {
                VideoIDStr = request.getParameter("id");
                accountKeyStr = request.getParameter("key");
            } else if (request.getRequestPathInfo().getSuffix() != null) {
                String suffix = request.getRequestPathInfo().getSuffix();
                if (suffix != null) {
                    logger.debug("suffix:" + suffix);
                    String[] sections = suffix.split("/");
                    if (sections.length == 3) {
                        VideoIDStr = sections[2];
                        VideoIDStr = VideoIDStr.substring(0, VideoIDStr.indexOf("."));
                        accountKeyStr = sections[1];
                        logger.debug("VideoIDStr:" + VideoIDStr);
                        logger.debug("accountKeyStr:" + accountKeyStr);

                    }
                }
            }
            String urlStr = getPoster(accountKeyStr, VideoIDStr);
            URL url = new URL(urlStr);
            BufferedImage img = ImageIO.read(url);
            if (img == null) {
                response.setStatus(404);
                return;
            }
            response.setContentType("image/jpeg");
            ImageIO.write(img, "jpeg", response.getOutputStream());
        } catch (Exception e) {
            response.setStatus(500);
        }
    }
}
