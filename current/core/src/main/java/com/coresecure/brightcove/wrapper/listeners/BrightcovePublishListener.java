package com.coresecure.brightcove.wrapper.listeners;

import com.day.cq.replication.ReplicationAction;
import com.day.cq.replication.ReplicationActionType;
import com.day.cq.replication.ReplicationEvent;
import com.day.cq.replication.ReplicationException;
import com.day.cq.replication.Replicator;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.AttributeType;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.coresecure.brightcove.wrapper.sling.ConfigurationGrabber;
import com.coresecure.brightcove.wrapper.sling.ConfigurationService;
import com.coresecure.brightcove.wrapper.sling.ServiceUtil;
 
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Component(service = EventHandler.class,
    immediate = true,
    property = {
            Constants.SERVICE_DESCRIPTION + "=Brightcove Distribution Event Listener ",
            EventConstants.EVENT_TOPIC + "=" + ReplicationAction.EVENT_TOPIC
    }
)
@Designate(ocd = BrightcovePublishListener.EventListenerPageActivationListenerConfiguration.class)
public class BrightcovePublishListener implements EventHandler {

    @ObjectClassDefinition(name = "Brightcove Distribution Listener")
    public @interface EventListenerPageActivationListenerConfiguration {
 
        @AttributeDefinition(
                name = "Enabled",
                description = "Enable Distribution Event Listener",
                type = AttributeType.BOOLEAN
        )
        boolean isEnabled() default false;
    }
 
    @Reference
    private ResourceResolverFactory resourceResolverFactory;
 
    private static final Logger LOG = LoggerFactory.getLogger(BrightcovePublishListener.class);
    private boolean enabled = false;
    private Map<String, String> paths = null;

    @Activate
    @Modified
    protected void activate(EventListenerPageActivationListenerConfiguration config) {
        enabled = config.isEnabled();
        
        LOG.info("Brightcove Distribution Event Listener is enabled:" + enabled);
    }
 
    @Override
    public void handleEvent(Event event) {
        if (enabled) {
            ConfigurationGrabber cg = ServiceUtil.getConfigurationGrabber();
            Set<String> services = cg.getAvailableServices();

            paths = new LinkedHashMap<>();
            for ( String service : services ) {
                // for each service, check to see the integration path
                // add the account ID to a HashMap with a key of the path
                ConfigurationService brcService = cg.getConfigurationService(service);
                paths.put(brcService.getAssetIntegrationPath(), brcService.getAccountID());
            }

            String[] props = event.getPropertyNames();
            for(String s : props) {
                LOG.info(s + " = " + event.getProperty(s));
            }
        }
    }
    
}