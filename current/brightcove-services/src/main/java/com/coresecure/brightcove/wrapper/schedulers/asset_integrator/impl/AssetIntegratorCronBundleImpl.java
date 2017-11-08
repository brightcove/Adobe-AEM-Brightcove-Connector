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
package com.coresecure.brightcove.wrapper.schedulers.asset_integrator.impl;

import com.coresecure.brightcove.wrapper.schedulers.asset_integrator.AssetIntegratorCronBundle;
import com.coresecure.brightcove.wrapper.schedulers.asset_integrator.runnables.AssetPropertyIntegratorRunnable;
import org.apache.felix.scr.annotations.*;
import org.apache.felix.scr.annotations.Properties;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.commons.mime.MimeTypeService;
import org.apache.sling.commons.scheduler.Scheduler;
import org.apache.sling.commons.threads.ThreadPoolManager;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;

import java.io.Serializable;
import java.util.*;

@Service
@Component(
        label="Brightcove Asset Integration Cronjob Scheduler Configuration",
        description="Brightcove Data Sync Cronjob Manager",
        metatype = true,
        immediate = true,
        name="com.coresecure.brightcove.wrapper.schedulers.asset_integrator.AssetIntegratorCronBundle"
)
@Properties({
        @Property(
                name="enable",
                label="CRON Enable",
                description="Enable CRON",
                boolValue=false),
        @Property(
                name="scheduler",label="CRON Scheduler",description="Scheduler CRON",value="0 5 0 ? * SUN"),
        @Property(
                name="maxthreads",label="Max Thread Number",description="Max Number of threads to call",intValue=20)

})
public class AssetIntegratorCronBundleImpl implements AssetIntegratorCronBundle {


    /** Default log. */
    protected final Logger log = LoggerFactory.getLogger(this.getClass());

    @Reference
    private Scheduler scheduler;

    @Reference
    MimeTypeService mType;


    @Reference
    private ThreadPoolManager threadPoolManager;
    @Reference
    ResourceResolverFactory resourceResolverFactory;

    private Dictionary<String, Object> prop;
    private Dictionary<String, Object> getProperties() {
        if (prop == null)
            return new Hashtable<String, Object>();
        return prop;
    }
    public String getCRON() {
        return (String) getProperties().get("scheduler");
    }
	public Boolean getStatus() {
        return (Boolean) getProperties().get("enable");
    }

    public int getMaxThreadNum() {
        return (Integer) getProperties().get("maxthreads");
    }



    @Activate
    protected void activate(ComponentContext componentContext) throws Exception, RepositoryException  {
       //case 1: with addJob() method: executes the job every minute

        //long schedulingPeriod = (86400*14); // (seconds in a day * 14 days)
        String jobName1 = "BrightcoveAssetIntegratorCron";
        try {
            this.scheduler.removeJob(jobName1);
            //log.info(jobName1 + " was in the scheduler and it has been removed");
        } catch (NoSuchElementException nj){
            //log.info(jobName1 + " was not in the scheduler");
        }
        prop = componentContext.getProperties();

        if (getStatus()) {
            log.debug(prop.toString());
            String schedulingExpression = getCRON();
            log.info(jobName1 + " is scheduled with the following CRON: " + schedulingExpression);

            Map<String, Serializable> config1 = new HashMap<String, Serializable>();
            boolean canRunConcurrently = false;
            AssetPropertyIntegratorRunnable assetPropertyIntegratorRunnable = new AssetPropertyIntegratorRunnable(mType, resourceResolverFactory,getMaxThreadNum());
            final Runnable job1 = assetPropertyIntegratorRunnable;
            try {
                this.scheduler.addJob(jobName1, job1, config1, schedulingExpression, canRunConcurrently);
            } catch (Exception e) {
                log.error("Error", e);
                //job1.run();
            }


        }
        
       
    }
    @Deactivate
    protected void deactivate(ComponentContext componentContext) {
        log.info("Deactivated, goodbye!");
    }
    
    
    
}
