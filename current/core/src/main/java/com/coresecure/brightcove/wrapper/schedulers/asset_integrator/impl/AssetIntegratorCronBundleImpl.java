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
package com.coresecure.brightcove.wrapper.schedulers.asset_integrator.impl;

import com.coresecure.brightcove.wrapper.schedulers.asset_integrator.AssetIntegratorCronBundle;
import com.coresecure.brightcove.wrapper.schedulers.asset_integrator.runnables.AssetPropertyIntegratorRunnable;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.commons.mime.MimeTypeService;
import org.apache.sling.commons.scheduler.ScheduleOptions;
import org.apache.sling.commons.scheduler.Scheduler;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Dictionary;
import java.util.Hashtable;

@Component(service = AssetIntegratorCronBundle.class)
@Designate(ocd = AssetIntegratorCronBundleImpl.Config.class)
public class AssetIntegratorCronBundleImpl implements AssetIntegratorCronBundle {

    @ObjectClassDefinition(name = "A simple cleanup task", description = "Simple demo for cron-job like task with properties")
	public static @interface Config {

		@AttributeDefinition(name = "CRON Scheduler")
		String scheduler_expression() default "0 5 0 ? * SUN";

        @AttributeDefinition(name = "CRON Enable", description = "Enable this cron task.")
		boolean enable_cron() default false;

		@AttributeDefinition(name = "Max Thread Number", description = "Max number of threads to use for asset integration.")
		int maxThreads() default 20;
	}


    /** Default log. */
    protected final Logger log = LoggerFactory.getLogger(this.getClass());

    private int maxThreads;

    @Reference
    private Scheduler scheduler;

    @Reference
    MimeTypeService mType;

    @Reference
    ResourceResolverFactory resourceResolverFactory;

    public int getMaxThreadNum() {
        return this.maxThreads;
    }

    @Activate
    protected void activate(final Config config) {
        String jobName1 = "BrightcoveAssetIntegratorCron";
        this.maxThreads = config.maxThreads();
        this.scheduler.unschedule(jobName1);
        if (config.enable_cron()) {
            String schedulingExpression = config.scheduler_expression();
            log.info(jobName1 + " is scheduled with the following CRON: " + schedulingExpression);
            boolean canRunConcurrently = false;
            AssetPropertyIntegratorRunnable assetPropertyIntegratorRunnable = new AssetPropertyIntegratorRunnable(mType, resourceResolverFactory, config.maxThreads());
            final Runnable job1 = assetPropertyIntegratorRunnable;
            final ScheduleOptions options = scheduler.EXPR(schedulingExpression).name(jobName1).canRunConcurrently(canRunConcurrently);
            this.scheduler.schedule(job1, options);
        }
    }

    @Deactivate
    protected void deactivate(final Config config) {
        String jobName1 = "BrightcoveAssetIntegratorCron";
        this.scheduler.unschedule(jobName1);
        log.info("Deactivated, goodbye!");
    }



}
