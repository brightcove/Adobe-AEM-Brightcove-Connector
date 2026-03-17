package com.coresecure.brightcove.wrapper.utils;

import static org.apache.sling.api.servlets.HttpConstants.METHOD_GET;

import java.io.IOException;

import javax.servlet.ServletException;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.request.builder.Builders;
import org.apache.sling.api.request.builder.SlingHttpServletResponseResult;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.engine.SlingRequestProcessor;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * The Class SlingUtils.
 */
public class SlingUtils {

    /**
     * Makes a sling request to render Brightcove accounts json.
     *
     * @param requestProcessor RequestProcessor to use
     * @param resourceResolver ResourceResolver to use
     * @param requestPath the path of the request
     * @return a String containing the component's json
     * @throws ServletException the servlet exception
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public static String makeSlingRequest(
        SlingRequestProcessor requestProcessor,
        ResourceResolver resourceResolver,
        String requestPath
    ) throws ServletException, IOException {
        Resource resource = resourceResolver.resolve(requestPath);
        SlingHttpServletRequest request = Builders.newRequestBuilder(resource)
            .withRequestMethod(METHOD_GET)
            .build();
        SlingHttpServletResponseResult responseResult = Builders.newResponseBuilder().build();
        requestProcessor.processRequest(request, responseResult, resourceResolver);
        JsonObject jsonObject = JsonParser.parseString(responseResult.getOutputAsString()).getAsJsonObject();
        return jsonObject.get("accounts").getAsJsonArray().toString();
    }
}
