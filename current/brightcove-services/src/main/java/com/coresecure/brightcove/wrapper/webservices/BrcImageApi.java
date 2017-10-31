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

import com.coresecure.brightcove.wrapper.BrightcoveAPI;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.commons.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.servlet.ServletException;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;

@Service
@Component
@Property(name = "sling.servlet.paths", value = {"/bin/brightcove/image","/bin/services/brightcove/image","/bin/services/brightcove/cache/image"})
public class BrcImageApi extends SlingAllMethodsServlet {

    @Override
    protected void doPost(final SlingHttpServletRequest request,
                          final SlingHttpServletResponse response) throws ServletException,
            IOException {
        PrintWriter outWriter = response.getWriter();
        response.setStatus(404);


    }

    @Override
    protected void doGet(final SlingHttpServletRequest request,
                         final SlingHttpServletResponse response) throws ServletException,
            IOException {

        int requestedAPI = 0;
        String requestedToken = "";
        Logger logger = LoggerFactory.getLogger(BrcImageApi.class);
        logger.debug("request image:");

        if (request.getParameter("id") != null) {
            String VideoIDStr = request.getParameter("id");
            String accountKeyStr = request.getParameter("key");
            try {
                BrightcoveAPI brAPI = new BrightcoveAPI(accountKeyStr);

                JSONObject video = brAPI.cms.getVideoImages(VideoIDStr);


                //TODO: MODULARIZE

                // Find a single video
                if (video != null && video.has("poster")) {
                    JSONObject poster = video.getJSONObject("poster");
                    String urlStr = poster.getString("src");

                    URL url = new URL(urlStr);
                    BufferedImage img = null;
                    try {
                        img = ImageIO.read(url);
                        //out.println(" READ SUCCESS" + "<br>");
                    } catch (Exception e) {
                        response.setStatus(404);
                        PrintWriter outWriter = response.getWriter();
                        outWriter.println("READ ERROR " + "<br>");
                    }
                    if (img != null) {
                        try {
                            response.setContentType("image/jpeg");
                            ImageIO.write(img, "jpeg", response.getOutputStream());
                        } catch (Exception ee) {
                            response.setStatus(404);
                            response.setContentType("text/html");
                            PrintWriter outWriter = response.getWriter();
                            outWriter.println("ENCODING ERROR " + "<br>");
                        }
                    }
                } else {
                    response.setStatus(404);

                }
            } catch (Exception e) {
                //System.out.println("Exception caught: '" + e + "'.");
                //System.exit(1);
                response.setStatus(404);
            }
        } else {
            if (request.getRequestPathInfo().getSuffix() != null) {
                String suffix = request.getRequestPathInfo().getSuffix();
                logger.debug("suffix:" + suffix);
                String[] sections = suffix.split("/");
                if (sections.length == 3) {
                    String VideoIDStr = sections[2];
                    VideoIDStr = VideoIDStr.substring(0, VideoIDStr.indexOf("."));
                    String accountKeyStr = sections[1];
                    logger.debug("VideoIDStr:" + VideoIDStr);
                    logger.debug("accountKeyStr:" + accountKeyStr);

                    try {
                        BrightcoveAPI brAPI = new BrightcoveAPI(accountKeyStr);

                        JSONObject video = brAPI.cms.getVideoImages(VideoIDStr);
                        logger.debug("video:" + video.toString(1));


                        // Find a single video
                        if (video != null && video.has("poster")) {
                            JSONObject poster = video.getJSONObject("poster");
                            String urlStr = poster.getString("src");

                            URL url = new URL(urlStr);
                            BufferedImage img = null;
                            try {
                                img = ImageIO.read(url);
                                //out.println(" READ SUCCESS" + "<br>");
                            } catch (Exception e) {
                                response.setStatus(404);
                                PrintWriter outWriter = response.getWriter();
                                outWriter.println("READ ERROR " + "<br>");
                            }
                            if (img != null) {
                                try {
                                    response.setContentType("image/jpeg");
                                    ImageIO.write(img, "jpeg", response.getOutputStream());
                                } catch (Exception ee) {
                                    response.setStatus(404);
                                    response.setContentType("text/html");
                                    PrintWriter outWriter = response.getWriter();
                                    outWriter.println("ENCODING ERROR " + "<br>");
                                }
                            }
                        } else {
                            response.setStatus(404);

                        }
                    } catch (Exception e) {
                        //System.out.println("Exception caught: '" + e + "'.");
                        //System.exit(1);
                        logger.error("Exception:",e);

                        response.setStatus(404);
                    }

                    //out.write(vidID);
                }
                //{out.write("{\"items\":[],\"results\":0}");}
            }
        }
    }
}
