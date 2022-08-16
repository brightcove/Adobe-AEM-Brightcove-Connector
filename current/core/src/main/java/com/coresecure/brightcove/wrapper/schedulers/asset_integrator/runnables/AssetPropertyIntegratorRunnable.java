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
package com.coresecure.brightcove.wrapper.schedulers.asset_integrator.runnables;


//*Imports*//

import com.coresecure.brightcove.wrapper.schedulers.asset_integrator.callables.VideoImportCallable;
import com.coresecure.brightcove.wrapper.sling.ConfigurationGrabber;
import com.coresecure.brightcove.wrapper.sling.ConfigurationService;
import com.coresecure.brightcove.wrapper.sling.ServiceUtil;
import com.coresecure.brightcove.wrapper.utils.Constants;
import com.day.cq.commons.jcr.JcrUtil;

import org.apache.jackrabbit.oak.jcr.Jcr;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.apache.sling.commons.mime.MimeTypeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.Session;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class AssetPropertyIntegratorRunnable implements Runnable {
    private static final String SERVICE_ACCOUNT_IDENTIFIER = "brightcoveWrite";

    private static final String ISO_8601_24H_FULL_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX";
    private static final Logger LOGGER = LoggerFactory.getLogger(AssetPropertyIntegratorRunnable.class);

    MimeTypeService mType;
    ResourceResolverFactory resourceResolverFactory;
    Integer maxThreadNum;


    public AssetPropertyIntegratorRunnable(MimeTypeService mType, ResourceResolverFactory resourceResolverFactory , int maxThreadNum){
        this.mType = mType;
        this.resourceResolverFactory = resourceResolverFactory;
        this.maxThreadNum = maxThreadNum;
    }



    public void run()
    {
        //INITIALIZE THREAD POOL


        //INITIALIZING THE RESOURCE RESOLVER
        ExecutorService executor = Executors.newFixedThreadPool(this.maxThreadNum);
        List<Future<String>> list = new ArrayList<Future<String>>();
        LOGGER.trace("BRIGHTCOVE ASSET INTEGRATION - SYNCHRONIZING DATABASE");
        try
        {
                //IF ACCOUNT IS VALID - INITIATE SYNC CONFIGURATION
            final Map<String, Object> authInfo = Collections.singletonMap(
                    ResourceResolverFactory.SUBSERVICE,
                    (Object) SERVICE_ACCOUNT_IDENTIFIER);
            ResourceResolverFactory rrf = resourceResolverFactory;
            final ResourceResolver resourceResolver = rrf.getServiceResourceResolver(authInfo);
            Session session = resourceResolver.adaptTo(Session.class); //GET CURRENT SESSION
            if (session == null) {
                return;
            }
            //MAIN TRY - CONFIGURATION GRAB SERVICE
            ConfigurationGrabber cg = ServiceUtil.getConfigurationGrabber();    //GETCONFIG SERVICE
            Set<String> services = cg.getAvailableServices();                            //BUILD SERVICES
            for(String requestedAccount: services)
            {
                final String requestedServiceAccount = requestedAccount;
                //GET CURRENT CONFIGURATION
                ConfigurationService cs = cg.getConfigurationService(requestedAccount);

                if (cs == null) {
                    throw new Exception("[Invalid or missing Brightcove configuration]");
                }
                final String confPath = cs.getAssetIntegrationPath();                     //GET PRECONFIGURED SYNC DAM TARGET PATH
                final String basePath = (confPath.endsWith("/") ? confPath : confPath.concat("/")).concat(requestedAccount).concat("/"); //CREATE BASE PATH
                //CREATE AND NAME BRIGHTCOVE ASSET FOLDERS PER ACCOUNT
                Node accountFolder = JcrUtil.createPath(basePath, "sling:OrderedFolder", session);
                accountFolder.setProperty("jcr:title", cs.getAccountAlias());
                session.save();
                final ServiceUtil serviceUtil = new ServiceUtil(requestedAccount);

                // get all the folders in the account
                JSONArray folders = serviceUtil.getFoldersAsJsonArray();
                if (folders.length() > 0) {
                    for (int x = 0; x < folders.length(); x++) {
                        JSONObject folder = folders.getJSONObject(x);
                        String folderId = folder.getString("id");
                        String folderName = folder.getString("name");

                        // create a new sling folder for each folder in the account
                        Node folderNode = JcrUtil.createPath(basePath + folderName.replaceAll(" ", "_").concat("/"),
                                            "sling:OrderedFolder", session);
                        folderNode.setProperty("jcr:title", folderName);
                        folderNode.setProperty("brc_folder_id", folderId);
                        session.save();
                    }
                }

                //GET VIDEOS
                int startOffset = 0;
                JSONObject jsonObject = new JSONObject(serviceUtil.searchVideo("", startOffset, 0, Constants.NAME, true)); //QUERY<------
                final JSONArray itemsArr = jsonObject.getJSONArray("items");


                LOGGER.trace("<<< " + itemsArr.length() + " INCOMING VIDEOS");

                //FOR EACH VIDEO IN THE ITEMS ARRAY
                for (int i = 0; i < itemsArr.length(); i++) {
                    final JSONObject innerObj = itemsArr.getJSONObject(i);

                    Callable<String> callable = new VideoImportCallable(innerObj, confPath, requestedServiceAccount, resourceResolverFactory, mType, serviceUtil);
                    Future<String> future = executor.submit(callable);
                    //add Future to the list, we can get return value using Future
                    list.add(future);
                }

                LOGGER.trace(">>>>FINISHED BRIGHTCOVE SYNC PAYLOAD TRAVERSAL>>>>");

            }


        }
        catch (Exception e)
        {
            LOGGER.error("ERROR" , e);
        }
        for(Future<String> fut : list){
            try {
                LOGGER.trace(new Date()+ "::"+fut.get());
            } catch (Exception e) {
                LOGGER.error("ERROR" , e);
            }
        }
        //shut down the executor service now
        executor.shutdown();

        //resourceResolver.close();
        //END MAIN TRY
    }

}
