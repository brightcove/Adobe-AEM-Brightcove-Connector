<%--

    Adobe CQ5 Brightcove Connector

    Copyright (C) 2015 Coresecure Inc.

        Authors:    Alessandro Bonfatti
                    Yan Kisen

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
<%@page trimDirectiveWhitespaces="true"
        import="org.apache.commons.httpclient.HttpClient,
                org.apache.commons.httpclient.HttpStatus,
                org.apache.commons.httpclient.methods.PostMethod,
                org.apache.commons.httpclient.methods.multipart.MultipartRequestEntity,
                org.apache.commons.httpclient.methods.multipart.Part,
                org.apache.commons.httpclient.methods.multipart.StringPart,
                org.slf4j.Logger,
                org.slf4j.LoggerFactory,
                java.util.Arrays,
                java.util.Enumeration,
                java.util.List,
                java.util.Set" %>
<%@ page import="com.coresecure.brightcove.wrapper.sling.ConfigurationService" %>
<%@ page import="com.coresecure.brightcove.wrapper.sling.ConfigurationGrabber" %>
<%@ page import="com.coresecure.brightcove.wrapper.sling.ServiceUtil" %>
<%@ page import="com.coresecure.brightcove.wrapper.api.Cms" %>

<%@include file="/libs/foundation/global.jsp" %>

<%
    ConfigurationService cs = null;
    String defaultAccount = "";
    String cookieAccount = "";
    String selectedAccount = "";
    String ReadToken = "";
    String WriteToken = "";
    ConfigurationGrabber cg = ServiceUtil.getConfigurationGrabber();
    Set<String> services = cg.getAvailableServices(slingRequest);
    if (services.size() > 0) {
        defaultAccount = (String) services.toArray()[0];
        cookieAccount = ServiceUtil.getAccountFromCookie(slingRequest);
        selectedAccount = (cookieAccount.trim().isEmpty()) ? defaultAccount : cookieAccount;

        cs = cg.getConfigurationService(selectedAccount);

    }


    response.reset();
    response.setContentType("application/json");

/****************************************************************
 *  proxy.jsp -- Media API Proxy
 * Takes  requests sent by client and forwards it to external API server
 * to avoid cross-domain scripting issues.
 *   Also forwards tokens to keep them hidden.
 *****************************************************************/

/*****************************************************************
 *Media API Strings go Here
 *   -Fill in with your info
 *       *Make sure you include the '.' at the end of the token*
 ******************************************************************/

    /*************************************************************
     *Don't do any error checking for paramters here, just forward them along
     * since the api server will check them anyway.
     **************************************************************/
    /*******************************************************************************
     *This list contains the names of all the write methods. It's used to check to see if a command
     * sent as a GET request should be forwarded as a multipart/post request.
     ********************************************************************************/
    final List<String> write_methods = Arrays.asList(new String[]{"update_video", "delete_video", "get_upload_status", "create_playlist", "update_playlist", "share_video"});

    Logger logger = LoggerFactory.getLogger("Brightcove");
/**************************************************************************
 * (*) bar is the all purpose utility string.  For write requests it's used to construct and
 * hold the formatted JSON request. For read requests it's used to construct and
 * hold the request URL.
 *
 *(*) useGet is to determine whether the request should be sent as a GET or POST request,
 * since some requests that should be sent as a POST arrive as a GET.
 ***************************************************************************/
    String bar = null;
    String[] ids = null;
    try {

        String command = slingRequest.getRequestParameter("command").getString();
        logger.info("Command: '" + command + "' ");
        if (write_methods.contains(command) && request.getMethod().equals("GET")) {
            ServiceUtil serviceUtil = new ServiceUtil(selectedAccount);
            switch (write_methods.indexOf(command)) {
                case 1:
                    useGet = false;
                    ids = slingRequest.getRequestParameter("ids").getString().split(",");
                    logger.info("Deleting videos");
                    Boolean cascade = true; // Deletes even if it is in use by playlists/players
                    Boolean deleteShares = true; // Deletes if shared to child accounts
                    for (String idStr : ids) {
                        boolean deleteResponse = serviceUtil.deleteVideo(idStr);
                        logger.info(idStr + " Response from server for delete (no message is perfectly OK): '" + deleteResponse + "'.");
                    }

                    break;

                default:
//                    String temp;
//                    //The method can't be part of the params section, so we write that out first, then the token and then loop through the rest of the parameters.
//                    bar = "{\"method\": \"" + command + "\", \"params\": {\"token\": \"" + apiWriteToken + "\"";
//                    for (Enumeration e = request.getParameterNames(); e.hasMoreElements(); ) {
//                        temp = (String) e.nextElement();
//                        //don't want to include command twice
//                        if (!temp.equals("command")) {
//                            bar += ",\"" + temp + "\": \"" + request.getParameter(temp) + "\"";
//                        }
//                    }
//                    bar += "}}";
//
//                    //out.print(bar);
//
//                    Part[] parts = {new StringPart("data", bar)};
//                    HttpClient client = new HttpClient();
//                    PostMethod postreq = new PostMethod(apiWriteLoc);
//                    postreq.setRequestEntity(new MultipartRequestEntity(parts, postreq.getParams()));
//                    client.executeMethod(postreq);
//                    if (postreq.getStatusCode() == HttpStatus.SC_OK) {
//                        out.print(postreq.getResponseBodyAsString());
//                        postreq.releaseConnection();
//                    } else {
//                        out.print("Post Failed, error: " + postreq.getStatusLine());
//                        postreq.releaseConnection();
//                    }

            }


            /******************************************************************************
             * The last case is an incoming GET request that should be forwarded as a GET request.
             * We set useGet to true and concatenate the read token at the end of the parameter string.
             * The JSTL  below sends the entire request and sends the response from the API server
             * back to the client.
             *******************************************************************************/
        } else {
            useGet = true;
            bar = apiReadLoc + '?' + request.getQueryString() + "&token=" + apiReadToken;
            /************************************************************************************
             *If you don't want to support JSTL, the block below duplicates the functionality of the JSTL block
             *at the bottom of this document. It might be faster to use JSTL but this hasn't been verified.
             *************************************************************************************/
        /*HttpClient client = new HttpClient();
        HttpMethod getreq = new GetMethod(bar);
        client.executeMethod(getreq);
        if(getreq.getStatusCode() == HttpStatus.SC_OK){
        out.print(getreq.getResponseBodyAsString());
        getreq.releaseConnection();
        }else{
        out.print( "Get Failed, error: " + getreq.getStatusLine());
        getreq.releaseConnection();
        }*/
        }
    } catch (Exception e) {
        out.write("{\"error\": \"Proxy Error, please check your tomcat logs.\", \"result\":null, \"id\": null}");
    }

%>
