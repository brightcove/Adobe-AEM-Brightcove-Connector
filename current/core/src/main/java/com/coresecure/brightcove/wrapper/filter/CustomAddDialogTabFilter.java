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
package com.coresecure.brightcove.wrapper.filter;

import com.day.cq.commons.TidyJsonItemWriter;
import org.apache.felix.scr.annotations.*;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.api.wrappers.SlingHttpServletResponseWrapper;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.servlet.*;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


@Component(immediate = true)
@Service(Filter.class)
@Properties({
        @Property(name = "sling.filter.scope", value = "request"),
        @Property(name = "service.ranking", intValue = -1),
        @Property(name = "sling.servlet.selectors", value = {"overlay", "infinity"})
})
public class CustomAddDialogTabFilter extends SlingSafeMethodsServlet implements Filter {

    final static private Logger LOGGER = LoggerFactory.getLogger(CustomAddDialogTabFilter.class);

    @Override
    public void init(FilterConfig filterConfig) {
        LOGGER.info("init");
    }

    @Override
    public void destroy() {
        LOGGER.info("destroy");
    }

    private class BufferingResponse extends SlingHttpServletResponseWrapper {
        private StringWriter stringWriter;

        public BufferingResponse(final SlingHttpServletResponse slingResponse) {
            super(slingResponse);
        }

        public String getContents() {
            if (this.stringWriter != null) {
                return this.stringWriter.toString();
            }
            return null;
        }

        public PrintWriter getWriter() throws IOException {
            if (stringWriter == null) {
                stringWriter = new StringWriter();
            }
            return new PrintWriter(stringWriter);
        }

        @Override
        public void resetBuffer() {
            if (this.stringWriter != null) {
                this.stringWriter = new StringWriter();
            }
            super.resetBuffer();
        }
    }


    @Override
    public void doFilter(ServletRequest pRequest, ServletResponse pResponse, FilterChain pChain) throws IOException, ServletException {
        // Implement Filter

        final SlingHttpServletResponse slingResponse = (SlingHttpServletResponse) pResponse;
        final SlingHttpServletRequest slingRequest = (SlingHttpServletRequest) pRequest;
        boolean doChain = !isDialogRequest(slingRequest);
        final BufferingResponse responseWrapper = new BufferingResponse(slingResponse);


        if (!doChain) {
            try {
                LOGGER.debug("use filter => " + slingRequest.getPathInfo());

                String fullRequestPath = slingRequest.getRequestURI();
                String componentPath = fullRequestPath.substring(0, fullRequestPath.lastIndexOf("/"));
                String dialogPath = getInheritDialog(slingRequest);
                if (dialogPath != null) {
                    LOGGER.debug("dialog path => " + dialogPath);
                    Resource dialogNodeRes = slingRequest.getResourceResolver().getResource(dialogPath);
                    if (dialogNodeRes != null) {
                        Node dialogNode = dialogNodeRes.adaptTo(Node.class);
                        TidyJsonItemWriter td = new TidyJsonItemWriter(null);
                        StringWriter writer = new StringWriter();
                        td.dump(dialogNode, writer, 1000);
                        JSONObject originalDialog = new JSONObject(writer.toString());
                        JSONObject originalTabs =  getOriginalTabs(componentPath, originalDialog, slingRequest);
                        slingResponse.getWriter().write(originalDialog.toString());
                    }
                }
                slingResponse.getWriter().close();
            } catch (JSONException je) {
                LOGGER.error("JE " + je.getMessage());
                pChain.doFilter(pRequest, pResponse);
            } catch (Exception e) {
                LOGGER.error("E " + e.getMessage());
                pChain.doFilter(pRequest, pResponse);
            }
        } else
        {
            pChain.doFilter(pRequest, pResponse);
        }
    }

    private JSONObject getOriginalTabs(String componentPath, JSONObject originalDialog, final SlingHttpServletRequest slingRequest) throws JSONException, RepositoryException{
        JSONObject originalTabs = originalDialog.getJSONObject("items").getJSONObject("tabs").getJSONObject("items");
        Resource componentRes = slingRequest.getResourceResolver().getResource(componentPath);
        if (componentRes != null) {
            for (Resource item : getInheritResources(componentRes))
            {
                if (item != null)
                {
                    Node nodeItem = item.adaptTo(Node.class);
                    if(nodeItem!=null)
                    {
                        getOriginalTabsFromRes(nodeItem,originalTabs);
                    }
                }
            }
        }
        return originalTabs;
    }

    private void getOriginalTabsFromRes(Node nodeItem, JSONObject originalTabs) throws JSONException, RepositoryException{

        NodeIterator additionalTabs = nodeItem.getNodes("additional_tab_*");
        while (additionalTabs.hasNext())
        {
            Node tab = additionalTabs.nextNode();
            String includeURL = tab.getPath() + ".infinity.json";
            LOGGER.trace(includeURL);

            JSONObject newTab = new JSONObject();
            newTab.put("xtype", "cqinclude");
            newTab.put("jcr:primaryType", "cq:Widget");
            newTab.put("path", includeURL);
            if (!originalTabs.has(tab.getName())) {
                originalTabs.put(tab.getName(), newTab);
            }
        }

    }


    private String getInheritDialog(SlingHttpServletRequest slingRequest) throws RepositoryException {
        String fullRequestPath = slingRequest.getRequestURI();
        String componentPath = fullRequestPath.substring(0, fullRequestPath.lastIndexOf("/"));

        if (slingRequest.getContextPath().length() > 0) {
            componentPath = componentPath.replace(slingRequest.getContextPath(), "/");
        }

        String result = null;

        LOGGER.trace("contextPath --> " + slingRequest.getContextPath());
        LOGGER.trace("componentPath to start --> " + componentPath);

        Resource componentRes = slingRequest.getResourceResolver().getResource(componentPath);
        int avoidLoop = 0;


        while (componentRes != null && result == null && avoidLoop < 1000) {
            LOGGER.trace("getInheritDialog loop --> " + componentRes.getPath());
            Resource dialog = componentRes.getChild("dialog");
            if (dialog != null) {
                ValueMap properties = dialog.getValueMap();
                if (!properties.containsKey("cs_include") || !properties.get("cs_include",false)) {
                    result = dialog.getPath();
                    LOGGER.debug("found dialog {}", dialog.getPath());
                    break;
                } else {
                    String resSuperType = componentRes.getResourceSuperType();
                    if ( resSuperType != null && !resSuperType.equals(componentRes.getResourceType())) {
                        componentRes = componentRes.getResourceResolver().getResource(resSuperType);
                    } else {
                        componentRes = null;
                    }
                }

            }
            avoidLoop++;
        }

        return result;
    }


    private List<Resource> getInheritResources(Resource res) {
        List<Resource> result = new ArrayList<Resource>();
        Resource temp = res;
        while (temp != null) {
            if (result.contains(temp)) break; //prevent loop
            result.add(temp);
            String resSuperType = temp.getResourceSuperType();
            if(resSuperType!=null && !resSuperType.equals(temp.getResourceType()))
            {
                temp = temp.getResourceResolver().getResource(resSuperType);
            } else {
                temp = null;
            }
        }
        return result;
    }


    public String getServletResponseString(BufferingResponse responseWrapper) {
        return responseWrapper.getContents();
    }

    public boolean isDialogRequest(SlingHttpServletRequest slingRequest) {
        String pathInfo = slingRequest.getPathInfo();
        return pathInfo.endsWith("/dialog.overlay.infinity.json");
    }


    @Activate
    protected final void activate(final Map<String, Object> properties) throws Exception {
        LOGGER.info("activate");
    }

    @Deactivate
    protected final void deactivate(final Map<String, String> properties) {
        LOGGER.info("deactivate");
    }

}