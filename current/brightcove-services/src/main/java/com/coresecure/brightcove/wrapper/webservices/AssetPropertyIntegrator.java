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


//*Imports*//
import com.coresecure.brightcove.wrapper.objects.*;
import com.coresecure.brightcove.wrapper.sling.ConfigurationGrabber;
import com.coresecure.brightcove.wrapper.sling.ConfigurationService;
import com.coresecure.brightcove.wrapper.sling.ServiceUtil;
import com.coresecure.brightcove.wrapper.utils.AccountUtil;
import com.coresecure.brightcove.wrapper.utils.HttpServices;
import com.day.cq.commons.jcr.JcrUtil;
import com.day.cq.dam.api.Asset;
import com.day.cq.dam.api.AssetManager;
import org.apache.felix.scr.annotations.*;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.*;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.apache.sling.commons.mime.MimeTypeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.servlet.ServletException;
import java.io.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

@Service
@Component
@Properties(value = {
        @Property(name = "sling.servlet.extensions", value = {"html"}),
        @Property(name = "sling.servlet.paths", value = {"/bin/brightcove/dataload"})
})
public class AssetPropertyIntegrator extends SlingAllMethodsServlet {

    private static final String ISO_8601_24H_FULL_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX";
    private static final Logger LOGGER = LoggerFactory.getLogger(AssetPropertyIntegrator.class);

    @Reference
    MimeTypeService mType;

    @Override
    protected void doPost(final SlingHttpServletRequest req, final SlingHttpServletResponse resp) throws ServletException, IOException {
        executeRequest(req, resp);
    }

    private void executeRequest(final SlingHttpServletRequest req, final SlingHttpServletResponse resp){
        //INITIALIZING THE RESOURCE RESOLVER
        ResourceResolver resourceResolver = req.getResourceResolver();

        LOGGER.trace("BRIGHTCOVE ASSET INTEGRATION - SYNCHRONIZING DATABASE");
        try
        {
            //MAIN TRY - CONFIGURATION GRAB SERVICE
            ConfigurationGrabber cg = ServiceUtil.getConfigurationGrabber();    //GETCONFIG SERVICE
            Set<String> services = AccountUtil.getSelectedServices(req);                            //BUILD SERVICES
            for(String requestedAccount: services)
            {
                //GET CURRENT CONFIGURATION
                ConfigurationService cs = cg.getConfigurationService(requestedAccount);

                if (cs != null)
                {
                    //IF ACCOUNT IS VALID - INITIATE SYNC CONFIGURATION
                    Session session = req.getResourceResolver().adaptTo(Session.class); //GET CURRENT SESSION
                    String confPath = cs.getAssetIntegrationPath();                     //GET PRECONFIGURED SYNC DAM TARGET PATH
                    String basePath = (confPath.endsWith("/") ? confPath : confPath.concat("/")).concat(requestedAccount).concat("/"); //CREATE BASE PATH
                    //CREATE AND NAME BRIGHTCOVE ASSET FOLDERS PER ACCOUNT
                    Node accountFolder = JcrUtil.createPath(basePath, "sling:OrderedFolder", session);
                    accountFolder.setProperty("jcr:title", cs.getAccountAlias());
                    session.save();
                    ServiceUtil serviceUtil = new ServiceUtil(requestedAccount);

                    //GET VIDEOS
                    int startOffset = 0;
                    JSONObject jsonObject = new JSONObject(serviceUtil.searchVideo("", startOffset, 0)); //QUERY<------
                    JSONArray itemsArr = (JSONArray) jsonObject.getJSONArray("items");

                    int success = 0;
                    int failure = 0;
                    int equal = 0;

                    LOGGER.trace("<<< " +itemsArr.length() + " INCOMING VIDEOS");

                    //FOR EACH VIDEO IN THE ITEMS ARRAY
                    for (int i = 0; i < itemsArr.length(); i++)
                    {
                        try {
                            //FOR EACH VIDEO COMING BACK FROM THE QUERY
                            JSONObject innerObj = itemsArr.getJSONObject(i);



                            //CHECK IF VIDEO'S STATE IS SET TO ACTIVE - CONDITION ONE
                            Boolean active = false;
                            if (innerObj.has("state") && !innerObj.get("state").equals(null)) {
                                active = "ACTIVE".equals(innerObj.getString("state")) ? true : false;
                            }

                            //CONDITIONN TWO - MUST HAVE AN ID
                            Boolean hasID = innerObj.has("id") && !innerObj.get("id").equals(null);
                            String ID = innerObj.has("id") ? innerObj.getString("id") : null;


                            LOGGER.trace(">>>>START>>>>>{"+ID +"}>> "+ (active && hasID) +">>>>>");

                            //TODO: CHECK IF VIDEO COMING INTO DAM (1) IS ACTIVE (2) HAS AN ID + SRC IMAGE??
                            if (active && hasID)
                            {
                                String name = innerObj.getString("name");
                                String brightcove_filename = ID + ".mp4"; //BRIGHTCOVE FILE NAME IS ID + . MP4 <-
                                String original_filename = innerObj.getString("original_filename") !=  null ? innerObj.getString("original_filename").replaceAll("%20", " ") : null ;

                                LOGGER.trace("SYNCING VIDEO>>["+name+"\tSTATE:ACTIVE\tTO BE:" + original_filename+"]");

                                //INITIALIZING ASSET SEARCH // INITIALIZATION
                                Asset newAsset = null;
                                InputStream binary = null;

                                //TODO: PRINTING DEBUGGER (ENABLE TO DEBUG)

                                //USNIG THE CONFIGURATION - BUILD THE DIRECTORY TO SEARCH FOR THE LOCAL ASSETS OR BUILD INTO
                                String localpath = (confPath.endsWith("/") ? confPath : confPath.concat("/")).concat(requestedAccount + "/").concat(brightcove_filename);
                                String oldpath = (confPath.endsWith("/") ? confPath : confPath.concat("/")).concat(requestedAccount + "/").concat(original_filename);

                                LOGGER.trace("SEARCHING FOR LOCAL ASSET");
                                LOGGER.trace(">>ORIGINAL: " + oldpath);
                                LOGGER.trace(">>PATH: " + localpath);


                                //TRY TO GET THIS ASSET IN THE CONFIGURED BC NODE PATH - IF IT IS NULL - IT MUST BE CREATED
                                newAsset = resourceResolver.getResource(oldpath) != null ? resourceResolver.getResource(oldpath).adaptTo(Asset.class) : resourceResolver.getResource(localpath) != null ? resourceResolver.getResource(localpath).adaptTo(Asset.class) : null;

                                if (newAsset == null)
                                {
                                    //IF NEW ASSET IS NULL MEANS THAT - IT MUST BE CREATED


                                    String mime_type = "";

                                    //GET THUMBNAIL - SOURCE CHECK? - CONDITION THREE? WHy?
                                    String thumbnail_src = innerObj.has("thumbnailURL") && !innerObj.get("thumbnailURL").equals(null) ? innerObj.getString("thumbnailURL") : "";


                                    if (HttpServices.isLocalPath(thumbnail_src)) //HAS LOCAL THUMB PATH
                                    {
                                        //IF THE THUMBNAIL SOURCE IST /CONTENT/DAM/ IT IS LOCAL - IF LOCAL >>

                                        LOGGER.trace("->>Pulling local image as this video's thumbnail image binary");
                                        LOGGER.trace("->>Thumbnail Source is/: " + thumbnail_src);
                                        LOGGER.trace("->>Looking for local thumbnail source at [INTERNAL]: " + localpath);


                                        Binary binaryRes = com.coresecure.brightcove.wrapper.utils.JcrUtil.getLocalBinary(resourceResolver, thumbnail_src, mType);
                                        if (binaryRes.binary != null) {
                                            binary = binaryRes.binary;
                                            mime_type = binaryRes.mime_type;

                                            if (binary == null) //IF REMOTE IMAGE LOAD UNSUCCESSFUL - LOAD DEFAULT
                                            {
                                                LOGGER.error("External thumbnail could not be read");
                                                LOGGER.error("FAILURE TO LOAD THUMBNAIL SOURCE FOR VIDEO " + newAsset.getPath());
                                                break;
                                            }
                                        }
                                    }
                                    else
                                    {
                                        LOGGER.trace("->>Pulling external image as this video's thumbnail image binary - Must do a GET");
                                        LOGGER.trace("->>Thumbnail Source is/: " + thumbnail_src + " DESTINATION >> " + localpath);
                                        Binary remoteBinary = HttpServices.getRemoteBinary(thumbnail_src, "", null);
                                        LOGGER.trace("->>[PULLING THUMBNAIL] : " + thumbnail_src);

                                        if (remoteBinary.binary != null) {
                                            binary = remoteBinary.binary;
                                            mime_type = remoteBinary.mime_type;
                                        }
                                        else
                                        {
                                            Binary binaryRes = com.coresecure.brightcove.wrapper.utils.JcrUtil.getLocalBinary(resourceResolver, "/etc/designs/cs/brightcove/shared/img/noThumbnail.jpg", mType);
                                            if (binaryRes.binary != null) {
                                                binary = binaryRes.binary;
                                                mime_type = binaryRes.mime_type;
                                            } else {
                                                break;
                                            }
                                        }
                                    }

                                    //CALL ASSET MANAGER
                                    AssetManager assetManager = resourceResolver.adaptTo(AssetManager.class);

                                    //CREATE ASSET - NEEDS BINARY OF THUMBNAIL IN ORDER TO SET IT FOR THE NEW ASSET
                                    newAsset = assetManager.createAsset(localpath, binary, mime_type, true);

                                    //SAVE CHANGES
                                    resourceResolver.commit();

                                    //AFTER ASSET HAS BEEN CREATED --> UPDATE THE ASSET WITH THE INNER OBJ WE ARE STiLL PROCESSING
                                    serviceUtil.updateAsset(newAsset, innerObj, resourceResolver, requestedAccount);

                                    //END CASE - ASSET NOT FOUND - MUST BE CREATED
                                    success++;
                                }
                                else
                                {

                                    //START CASE - ASSET HAS BEEN FOUND LOCALLY - CAN BE UPDATED
                                    LOGGER.trace("ASSET FOUND - UPDATING");

                                    //DATE COMPARISON TO MAKE SURE IT MUST BE UPDATED
                                    Date local_mod_date = new Date(newAsset.getLastModified());

                                    SimpleDateFormat sdf = new SimpleDateFormat(ISO_8601_24H_FULL_FORMAT);
                                    Date remote_date = sdf.parse(innerObj.getString("updated_at"));

                                    //LOCAL COMPARISON DATE TO SEE IF IT NEEDS TO UPDATE
                                    if (local_mod_date.compareTo(remote_date) < 0)
                                    {
                                        LOGGER.trace("OLDERS-DATE>>>>>" + local_mod_date);
                                        LOGGER.trace("PARSED-DATE>>>>>" + remote_date);
                                        LOGGER.trace(local_mod_date + " < " + remote_date);
                                        LOGGER.trace("MODIFICATION DETECTED");
                                        serviceUtil.updateAsset(newAsset, innerObj, resourceResolver, requestedAccount);
                                        success++;
                                    }
                                    else
                                    {
                                        LOGGER.trace("No Changes to be Made = Asset is equivalent");
                                        equal++;
                                    }


                                }

                            }
                            else
                            {
                                LOGGER.warn("VIDEO INITIALIZATION FAILED - NOT ACTIVE / NO ID - skipping: " + innerObj.toString(1));
                                failure++;
                            }


                            LOGGER.trace(">>>>>>>>>{"+ID +"}>>>>>END>>>>");

                            //MAIN VIDEO ARRAY TRAVERSAL LOOP
                        } catch (JSONException e) {
                            LOGGER.error("JSON EXCEPTION", e);
                            failure++;
                        } catch (IllegalArgumentException e) {
                            LOGGER.error("IllegalArgumentException", e);
                            failure++;
                        } catch (RepositoryException e) {
                            LOGGER.error("RepositoryException", e);
                            failure++;
                        } catch (RuntimeException e) {
                            LOGGER.error("RuntimeException", e);
                            failure++;
                        } catch (ParseException e) {
                            LOGGER.error("ParseException", e);
                            failure++;
                        }
                    }

                    LOGGER.trace(">>>>FINISHED BRIGHTCOVE SYNC PAYLOAD TRAVERSAL>>>>");
                    LOGGER.warn(">>>> SYNC DATA: nochange: " + equal + " success: " + success + " skipped or failed: " + failure+" >>>>");

                } else {
                    throw new Exception("[Invalid or missing Brightcove configuration]");
                }
            }


            resourceResolver.close();
            //END MAIN TRY
        }
        catch (Exception e)
        {
            LOGGER.error("ERROR" , e);
        }
    }
    @Override
    protected void doGet(final SlingHttpServletRequest req, final SlingHttpServletResponse resp) throws ServletException, IOException
    {
        executeRequest(req, resp);
    }

}
