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
package com.coresecure.brightcove.wrapper.objects;

import com.coresecure.brightcove.wrapper.utils.HttpServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.Map;

public class Platform {
    private final static String DEFAULT_OAUTH_URL = "https://oauth.brightcove.com/v4";
    private final static String DEFAULT_PLAYERS_API_URL = "https://players.api.brightcove.com/v2";
    private final static String DEFAULT_API_URL = "https://cms.api.brightcove.com/v1";
    private final static String DEFAULT_DI_API_URL = "https://ingest.api.brightcove.com/v1";
    private static String OAUTH_Url;
    private static String PLAYERS_API_Url;
    private static String API_Url;
    private static String DI_API_Url;
    private static Proxy PROXY = Proxy.NO_PROXY;
    private static final Logger LOGGER = LoggerFactory.getLogger(Platform.class);

    public Platform() {

    }


    public Platform(String aOAUTH_Url, String aAPI_Url, String aDI_API_Url, String aPLAYERS_API_Url) {
        OAUTH_Url = aOAUTH_Url;
        API_Url = aAPI_Url;
        DI_API_Url = aDI_API_Url;
        PLAYERS_API_Url = aPLAYERS_API_Url;
    }

    public String getOAUTH_Url() {
        return (OAUTH_Url != null && !OAUTH_Url.isEmpty()) ? OAUTH_Url : DEFAULT_OAUTH_URL;
    }

    public String getAPI_Url() {
        return (API_Url != null && !API_Url.isEmpty()) ? API_Url : DEFAULT_API_URL;
    }

    public String getPLAYERS_API_URL() {
        return (PLAYERS_API_Url != null && !PLAYERS_API_Url.isEmpty()) ? PLAYERS_API_Url : DEFAULT_PLAYERS_API_URL;
    }

    public String getDI_API_Url() {
        return (DI_API_Url != null && !DI_API_Url.isEmpty()) ? DI_API_Url : DEFAULT_DI_API_URL;
    }

    public void setOAUTH_Url(String aOAUTH_Url) {
        OAUTH_Url = aOAUTH_Url;
    }

    public void setAPI_Url(String aAPI_Url) {
        API_Url = aAPI_Url;
    }

    public void setDI_API_Url(String aDI_API_Url) {
        DI_API_Url = aDI_API_Url;
    }

    public String getAPI(String targetURL, String urlParameters, Map<String, String> headers) {
        String URL = getAPI_Url() + targetURL;
        String response = HttpServices.executeGet(URL, urlParameters, headers);
        return response;
    }

    public String getPLAYERS_API(String targetURL, String urlParameters, Map<String, String> headers) {
        String URL = getPLAYERS_API_URL() + targetURL;
        String response = HttpServices.executeGet(URL, urlParameters, headers);
        return response;
    }

    public String postAPI(String targetURL, String payload, Map<String, String> headers) {
        String URL = getAPI_Url() + targetURL;
        LOGGER.trace("POST URL: "+URL);

        String response = HttpServices.executePost(URL, payload, headers);
        LOGGER.trace(response);
        return response;
    }

    public String patchAPI(String targetURL, String payload, Map<String, String> headers) {
        String URL = getAPI_Url() + targetURL;
        LOGGER.trace("patchAPI URL: " + URL);
        String response = HttpServices.executePatch(URL, payload, headers);
        LOGGER.trace(response);
        return response;
    }

    public String postDI_API(String targetURL, String payload, Map<String, String> headers) {
        String URL = getDI_API_Url() + targetURL;
        LOGGER.trace("postDI_API: "+URL);
        String response = HttpServices.executePost(URL, payload, headers);
        LOGGER.trace(response);
        return response;
    }

    public String getDI_API(String targetURL, String payload, Map<String, String> headers) {
        String URL = getDI_API_Url() + targetURL;
        LOGGER.trace("getDI_API: "+URL);
        String response = HttpServices.executeGet(URL, payload, headers);
        LOGGER.trace(response);
        return response;
    }

    public String postDIRequest_API(String targetURL, String payload, Map<String, String> headers) {
        String URL = getDI_API_Url() + targetURL;
        LOGGER.trace("postDI_API: "+URL);
        String response = HttpServices.executePost(URL, payload, headers,"application/json");
        LOGGER.trace(response);
        return response;
    }

    public String deleteAPI(String targetURL, String videoID, Map<String, String> headers) {
        String URL = getAPI_Url() + targetURL;
        LOGGER.trace("deleteAPI: "+URL);
        String response = HttpServices.executeDelete(URL, headers);
        LOGGER.trace(response);
        return response;
    }


    public void setProxy(String proxy) {
      Proxy newProxy = Proxy.NO_PROXY;

      if(proxy!=null) {
        String[] parts = proxy.split(":");
        if (parts.length==2) {
          PROXY = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(parts[0], Integer.parseInt(parts[1])));
        }
      }

      HttpServices.setProxy(PROXY);
    }
}
