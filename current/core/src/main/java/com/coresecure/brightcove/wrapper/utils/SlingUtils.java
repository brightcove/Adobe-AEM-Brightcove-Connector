package com.coresecure.brightcove.wrapper.utils;

import static org.apache.sling.api.servlets.HttpConstants.METHOD_GET;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.engine.SlingRequestProcessor;

import com.day.cq.contentsync.handler.util.RequestResponseFactory;
import com.day.cq.wcm.api.WCMMode;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * The Class SlingUtils.
 */
public class SlingUtils {

    /**
     * Makes a sling request to render Brightcove accounts json.
     *
     * @param requestResponseFactory RequestResponseFactory to use
     * @param requestProcessor RequestProcessor to use
     * @param resourceResolver ResourceResolver to use
     * @param requestPath the path of the request
     * @return a String containing the component's json
     * @throws ServletException the servlet exception
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public static String makeSlingRequest(
        RequestResponseFactory requestResponseFactory,
        SlingRequestProcessor requestProcessor,
        ResourceResolver resourceResolver,
        String requestPath
    ) throws ServletException, IOException {
        HttpServletRequest request = requestResponseFactory.createRequest(METHOD_GET, requestPath);
        WCMMode.DISABLED.toRequest(request);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        HttpServletResponse response = requestResponseFactory.createResponse(out);

        requestProcessor.processRequest(request, response, resourceResolver);
        JsonParser parser = new JsonParser();
        JsonObject jsonObject = (JsonObject) parser.parse(out.toString());
        String jsonArrayString = jsonObject.get("accounts").getAsJsonArray().toString();

        return jsonArrayString;
    }
}
