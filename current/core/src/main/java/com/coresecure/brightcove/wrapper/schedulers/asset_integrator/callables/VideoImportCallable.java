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
package com.coresecure.brightcove.wrapper.schedulers.asset_integrator.callables;

import com.coresecure.brightcove.wrapper.objects.BinaryObj;
import com.coresecure.brightcove.wrapper.sling.ServiceUtil;
import com.coresecure.brightcove.wrapper.utils.Constants;
import com.coresecure.brightcove.wrapper.utils.HttpServices;
import com.coresecure.brightcove.wrapper.utils.ImageUtil;
import com.day.cq.dam.api.Asset;
import com.day.cq.dam.api.AssetManager;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.apache.sling.commons.mime.MimeTypeService;
import org.jcodec.api.awt.AWTSequenceEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.jcr.RepositoryException;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Created by pablo.kropilnicki on 11/8/17.
 */
public class VideoImportCallable implements Callable<String> {
    private static final Logger LOGGER = LoggerFactory.getLogger(VideoImportCallable.class);
    private static final String ISO_8601_24H_FULL_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX";
    private static final String SERVICE_ACCOUNT_IDENTIFIER = "brightcoveWrite";

    private JSONObject innerObj;
    private String confPath;
    private String requestedServiceAccount;
    private MimeTypeService mType;
    private ServiceUtil serviceUtil;
    private ResourceResolverFactory resourceResolverFactory;
    private ResourceResolver resourceResolver = null;
    public VideoImportCallable(JSONObject innerObj, String confPath, String requestedServiceAccount, ResourceResolverFactory resourceResolverFactory, MimeTypeService mType, ServiceUtil serviceUtil){
        this.innerObj = innerObj;
        this.confPath = confPath;
        this.requestedServiceAccount = requestedServiceAccount;
        this.mType = mType;
        this.serviceUtil = serviceUtil;
        this.resourceResolverFactory = resourceResolverFactory;
    }

    private String getItemFromJson(JSONObject innerObj, String key) throws JSONException{
        return innerObj.has(key) && innerObj.get(key)!=null ? innerObj.getString(key) : "";
    }

    private Asset createAsset(String localpath, String id, String brightcove_filename) throws JSONException, IOException, RepositoryException {
        Asset newAsset = null;

        String mime_type = "";
        InputStream binary = null;

        //GET THUMBNAIL - SOURCE CHECK? - CONDITION THREE? WHy?
        String thumbnail_src =getItemFromJson(innerObj,Constants.THUMBNAIL_URL);

        BinaryObj binaryRes = null;

        if (HttpServices.isLocalPath(thumbnail_src)) //HAS LOCAL THUMB PATH
        {
            //IF THE THUMBNAIL SOURCE IST /CONTENT/DAM/ IT IS LOCAL - IF LOCAL >>
            LOGGER.trace("->>Pulling local image as this video's thumbnail image binary");
            LOGGER.trace("->>Thumbnail Source is/: " + thumbnail_src);
            LOGGER.trace("->>Looking for local thumbnail source at [INTERNAL]: " + localpath);
            binaryRes = com.coresecure.brightcove.wrapper.utils.JcrUtil.getLocalBinary(resourceResolver, thumbnail_src, mType);
        } else {
            LOGGER.trace("->>Pulling external image as this video's thumbnail image binary - Must do a GET");
            LOGGER.trace("->>Thumbnail Source is/: " + thumbnail_src + " DESTINATION >> " + localpath);
            binaryRes = HttpServices.getRemoteBinary(thumbnail_src, "", null);
            LOGGER.trace("->>[PULLING THUMBNAIL] : " + thumbnail_src);
        }
        if (binaryRes.binary != null) {
            binary = binaryRes.binary;
            mime_type = binaryRes.mime_type;
        } else {
            binaryRes = com.coresecure.brightcove.wrapper.utils.JcrUtil.getLocalBinary(resourceResolver, "/etc/designs/cs/brightcove/shared/img/noThumbnail.jpg", mType);
            if (binaryRes.binary != null) {
                binary = binaryRes.binary;
                mime_type = binaryRes.mime_type;
            } else {
                LOGGER.trace("FAIL EXTERNAL");
                return null;
            }
        }

        //CALL ASSET MANAGER
        AssetManager assetManager = resourceResolver.adaptTo(AssetManager.class);
        if (assetManager == null) {
            return null;
        }
        BufferedImage image = ImageIO.read(binary);

        //CRATE TEMPORARY MP4 FILE
        String prefix = id + "-";
        String suffix = ".mp4";
        File tempFile2 = File.createTempFile(prefix, suffix);
        tempFile2.deleteOnExit();

        AWTSequenceEncoder enc = AWTSequenceEncoder.createSequenceEncoder(tempFile2, 30);

        LOGGER.trace("ENCODING PROCESS");
        LOGGER.trace("height: {}" ,image.getType());
        LOGGER.trace("height: {}" ,image.getHeight());
        LOGGER.trace("width: {}" ,image.getWidth());


        if (image.getHeight() % 2 != 0) {
            image = ImageUtil.cropImage(image, new Rectangle(image.getWidth(), image.getHeight() - (image.getHeight() % 2)));
        }
        if (image.getWidth() % 2 != 0) {
            image = ImageUtil.cropImage(image, new Rectangle(image.getWidth() - (image.getWidth() % 2), image.getHeight()));
        }

        enc.encodeImage(image);
        enc.finish();

        InputStream in = null;
        FileInputStream fis = null;
        try {

            fis = new FileInputStream(tempFile2);
            in = new BufferedInputStream(fis);


            LOGGER.trace("***IMAGE->VIDEO: " + localpath + "  {}", mType.getMimeType(brightcove_filename));


            //CREATE ASSET - NEEDS BINARY OF THUMBNAIL IN ORDER TO SET IT FOR THE NEW ASSET
            newAsset = assetManager.createAsset(localpath, in, mType.getMimeType(brightcove_filename), true);


        }
        catch (Exception e)
        {
            LOGGER.error("VideoImportCallable {}",e);
        }
        finally {
            //Close binary
            if (in != null) in.close();
            if (fis != null) fis.close();
            if (tempFile2 != null && tempFile2.delete()) {
                LOGGER.trace("Temp File Removed");
            }
        }


        //SAVE CHANGES
        resourceResolver.commit();
        return newAsset;
    }

    private String cleanPath(String confPath, String filename) {
        return (confPath.endsWith("/") ? confPath : confPath.concat("/")).concat(requestedServiceAccount + "/").concat(filename);
    }

    private String cleanPath(String confPath, String filename, String folderId) {
        String accountFolder = (confPath.endsWith("/") ? confPath : confPath.concat("/")).concat(requestedServiceAccount + "/");
        if (folderId == null || folderId.length() == 0)
            return accountFolder.concat(filename);
        else
            return accountFolder.concat(folderId + "/").concat(filename);
    }

    private String cleanFilename(JSONObject innerObj) throws JSONException{
        return innerObj.getString(Constants.ORIGINAL_FILENAME) != null ? innerObj.getString(Constants.ORIGINAL_FILENAME).replaceAll("%20", " ") : null;
    }

    private Asset getAsset(String oldpath, String localpath ){
        Resource resource = resourceResolver.getResource(oldpath);
        if(resource != null) {
            return resource.adaptTo(Asset.class);
        }
        resource = resourceResolver.getResource(localpath);
        if(resource != null) {
            return resource.adaptTo(Asset.class);
        }
        return null;
    }

    public String call(){
        // Get the Service resource resolver
        try {
            final Map<String, Object> authInfo = Collections.singletonMap(
                    ResourceResolverFactory.SUBSERVICE,
                    (Object) SERVICE_ACCOUNT_IDENTIFIER);
            resourceResolver = resourceResolverFactory.getServiceResourceResolver(authInfo);
            //FOR EACH VIDEO COMING BACK FROM THE QUERY


            //CHECK IF VIDEO'S STATE IS SET TO ACTIVE - CONDITION ONE
            Boolean active = innerObj.has(Constants.STATE) && innerObj.get(Constants.STATE) != null && "ACTIVE".equals(innerObj.getString(Constants.STATE)) &&  innerObj.has(Constants.ID) && innerObj.get(Constants.ID) != null;

            //CONDITIONN TWO - MUST HAVE AN ID
            String id = getItemFromJson(innerObj,Constants.ID);


            LOGGER.trace(">>>>START>>>>> {} >> {}", id ,active);

            if (!active) {
                LOGGER.warn("VIDEO INITIALIZATION FAILED - NOT ACTIVE / NO ID - skipping: " + innerObj.toString(1));
                if (resourceResolver != null) {
                    resourceResolver.close();
                }
                return  Thread.currentThread().getName();
            }

            String name = innerObj.getString(Constants.NAME);
            String brightcove_filename = id + ".mp4"; //BRIGHTCOVE FILE NAME IS ID + . MP4 <-
            String original_filename = cleanFilename(innerObj);
            String brightcove_folder_id = (innerObj.isNull(Constants.FOLDER_ID) ? "" : innerObj.getString(Constants.FOLDER_ID));

            LOGGER.trace("SYNCING VIDEO >> [" + name + "\tSTATE:ACTIVE\tTO BE:" + original_filename + "]");

            LOGGER.trace("VIDEO HAS FOLDER ID >> [" + brightcove_folder_id + "]");

            //INITIALIZING ASSET SEARCH // INITIALIZATION
            Asset newAsset = null;

            //USNIG THE CONFIGURATION - BUILD THE DIRECTORY TO SEARCH FOR THE LOCAL ASSETS OR BUILD INTO
            String localpath = cleanPath(confPath, brightcove_filename, brightcove_folder_id);
            String oldpath = cleanPath(confPath, original_filename, brightcove_folder_id);

            LOGGER.trace("SEARCHING FOR LOCAL ASSET");
            LOGGER.trace(">>ORIGINAL: " + oldpath);
            LOGGER.trace(">>PATH: " + localpath);


            //TRY TO GET THIS ASSET IN THE CONFIGURED BC NODE PATH - IF IT IS NULL - IT MUST BE CREATED
            newAsset = getAsset(oldpath,localpath);
            if (newAsset == null) {
                newAsset = createAsset(localpath,id,brightcove_filename);
                if (newAsset != null) serviceUtil.updateAsset(newAsset, innerObj, resourceResolver, requestedServiceAccount);
            } else {
                //START CASE - ASSET HAS BEEN FOUND LOCALLY - CAN BE UPDATED
                LOGGER.trace("ASSET FOUND - UPDATING");

                //DATE COMPARISON TO MAKE SURE IT MUST BE UPDATED
                Date local_mod_date = new Date(newAsset.getLastModified());

                SimpleDateFormat sdf = new SimpleDateFormat(ISO_8601_24H_FULL_FORMAT);
                Date remote_date = sdf.parse(innerObj.getString(Constants.UPDATED_AT));

                //LOCAL COMPARISON DATE TO SEE IF IT NEEDS TO UPDATE
                if (local_mod_date.compareTo(remote_date) < 0) {
                    LOGGER.trace("OLDERS-DATE>>>>>" + local_mod_date);
                    LOGGER.trace("PARSED-DATE>>>>>" + remote_date);
                    LOGGER.trace(local_mod_date + " < " + remote_date);
                    LOGGER.trace("MODIFICATION DETECTED");
                    serviceUtil.updateAsset(newAsset, innerObj, resourceResolver, requestedServiceAccount);

                } else {
                    LOGGER.trace("No Changes to be Made = Asset is equivalent");

                }

            }

            LOGGER.trace(">>>>>>>>>{" + id + "}>>>>>END>>>>");

            //MAIN VIDEO ARRAY TRAVERSAL LOOP
        } catch (Exception e) {
            LOGGER.error(e.getClass().getName(), e);
        } finally {
            if (resourceResolver != null) {
                resourceResolver.close();
            }
        }
//        Thread.currentThread().interrupt();
        return  Thread.currentThread().getName();
    }
}
