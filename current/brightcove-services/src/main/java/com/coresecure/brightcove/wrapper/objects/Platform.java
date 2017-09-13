package com.coresecure.brightcove.wrapper.objects;

import com.coresecure.brightcove.wrapper.utils.HttpServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.Map;

public class Platform {
    private final static String DEFAULT_OAUTH_URL = "https://oauth.brightcove.com/v3";
    private final static String DEFAULT_PLAYERS_API_URL = "https://players.api.brightcove.com/v1";
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
