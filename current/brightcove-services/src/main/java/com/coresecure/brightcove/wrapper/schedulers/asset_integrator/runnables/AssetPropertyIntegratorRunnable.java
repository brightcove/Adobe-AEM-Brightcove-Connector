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
package com.coresecure.brightcove.wrapper.schedulers.asset_integrator.runnables;


//*Imports*//

import com.coresecure.brightcove.wrapper.objects.Binary;
import com.coresecure.brightcove.wrapper.schedulers.asset_integrator.AssetIntegratorCronBundle;
import com.coresecure.brightcove.wrapper.schedulers.asset_integrator.callables.VideoImportCallable;
import com.coresecure.brightcove.wrapper.sling.ConfigurationGrabber;
import com.coresecure.brightcove.wrapper.sling.ConfigurationService;
import com.coresecure.brightcove.wrapper.sling.ServiceUtil;
import com.coresecure.brightcove.wrapper.utils.AccountUtil;
import com.coresecure.brightcove.wrapper.utils.HttpServices;
import com.coresecure.brightcove.wrapper.utils.ImageUtil;
import com.day.cq.commons.jcr.JcrUtil;
import com.day.cq.dam.api.Asset;
import com.day.cq.dam.api.AssetManager;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.*;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.apache.sling.commons.mime.MimeTypeService;
import org.apache.sling.commons.threads.ModifiableThreadPoolConfig;
import org.apache.sling.commons.threads.ThreadPool;
import org.apache.sling.commons.threads.ThreadPoolConfig;
import org.apache.sling.commons.threads.ThreadPoolManager;
import org.jcodec.api.awt.AWTSequenceEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
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


    public AssetPropertyIntegratorRunnable(MimeTypeService mType,ResourceResolverFactory resourceResolverFactory , int maxThreadNum){
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


            //MAIN TRY - CONFIGURATION GRAB SERVICE
            ConfigurationGrabber cg = ServiceUtil.getConfigurationGrabber();    //GETCONFIG SERVICE
            Set<String> services = cg.getAvailableServices();                            //BUILD SERVICES
            for(String requestedAccount: services)
            {
                final String requestedServiceAccount = requestedAccount;
                //GET CURRENT CONFIGURATION
                ConfigurationService cs = cg.getConfigurationService(requestedAccount);

                if (cs != null)
                {
                    //IF ACCOUNT IS VALID - INITIATE SYNC CONFIGURATION
                    final Map<String, Object> authInfo = Collections.singletonMap(
                            ResourceResolverFactory.SUBSERVICE,
                            (Object) SERVICE_ACCOUNT_IDENTIFIER);
                    ResourceResolverFactory rrf = resourceResolverFactory;
                    final ResourceResolver resourceResolver = rrf.getServiceResourceResolver(authInfo);
                    synchronized (resourceResolver) {
                        Session session = resourceResolver.adaptTo(Session.class); //GET CURRENT SESSION
                        final String confPath = cs.getAssetIntegrationPath();                     //GET PRECONFIGURED SYNC DAM TARGET PATH
                        final String basePath = (confPath.endsWith("/") ? confPath : confPath.concat("/")).concat(requestedAccount).concat("/"); //CREATE BASE PATH
                        //CREATE AND NAME BRIGHTCOVE ASSET FOLDERS PER ACCOUNT
                        Node accountFolder = JcrUtil.createPath(basePath, "sling:OrderedFolder", session);
                        accountFolder.setProperty("jcr:title", cs.getAccountAlias());
                        session.save();
                        final ServiceUtil serviceUtil = new ServiceUtil(requestedAccount);

                        //GET VIDEOS
                        int startOffset = 0;
                        JSONObject jsonObject = new JSONObject(serviceUtil.searchVideo("", startOffset, 0)); //QUERY<------
                        final JSONArray itemsArr = (JSONArray) jsonObject.getJSONArray("items");

                        int success = 0;
                        int failure = 0;
                        int equal = 0;

                        LOGGER.trace("<<< " + itemsArr.length() + " INCOMING VIDEOS");

                        //FOR EACH VIDEO IN THE ITEMS ARRAY
                        for (int i = 0; i < itemsArr.length(); i++) {
                            final JSONObject innerObj = itemsArr.getJSONObject(i);

                            Callable<String> callable = new VideoImportCallable(innerObj,confPath,requestedServiceAccount, resourceResolverFactory, mType, serviceUtil);
                            Future<String> future = executor.submit(callable);
                            //add Future to the list, we can get return value using Future
                            list.add(future);
                        }

                        LOGGER.trace(">>>>FINISHED BRIGHTCOVE SYNC PAYLOAD TRAVERSAL>>>>");
                        LOGGER.warn(">>>> SYNC DATA: nochange: " + equal + " success: " + success + " skipped or failed: " + failure + " >>>>");
                    }
                } else {
                    throw new Exception("[Invalid or missing Brightcove configuration]");
                }
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
