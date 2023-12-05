package com.coresecure.brightcove.wrapper.listeners;

// import com.adobe.training.core.utils.Lab2020CommonMethods;
import com.day.cq.replication.ReplicationAction;
import com.day.cq.replication.ReplicationActionType;
import com.day.cq.replication.ReplicationException;
import com.day.cq.replication.Replicator;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.settings.SlingSettingsService;
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

import javax.jcr.Session;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.day.cq.commons.Externalizer.AUTHOR;

@Component(service = EventHandler.class, immediate = true, property = {
        Constants.SERVICE_DESCRIPTION + "=Lab2020 Activation Event Listener ",
        EventConstants.EVENT_TOPIC + "=" + ReplicationAction.EVENT_TOPIC
})
@Designate(ocd = BrightcoveMoveListener.EventListenerPageActivationListenerConfiguration.class)
public class BrightcoveMoveListener implements EventHandler {

    @ObjectClassDefinition(name = "Lab2020 - Activation Event Listener")
    public @interface EventListenerPageActivationListenerConfiguration {

        @AttributeDefinition(name = "Enabled", description = "Activation Event Listener is enabled", type = AttributeType.BOOLEAN)
        boolean isEnabled() default false;

        @AttributeDefinition(name = "List of paths", description = "Configure here all the listening paths and related paths to publish "
                +
                "with the format <listeningPath>:<pathToPublish> e.g. /content/lab2020/en/web/test:/content/lab2020/en/mobile/test. Use ':' as separator.")
        String[] getListOfPaths();
    }

    @Reference
    private SlingSettingsService slingSettingsService;

    @Reference
    private ResourceResolverFactory resourceResolverFactory;

    @Reference
    private Replicator replicator;

    private static final Logger LOG = LoggerFactory.getLogger(BrightcoveMoveListener.class);
    private Map<String, String> paths = null;
    private boolean enabled = false;

    @Activate
    @Modified
    protected void activate(EventListenerPageActivationListenerConfiguration config) {
        enabled = config.isEnabled();
        paths = new LinkedHashMap<>();
        String[] listOfPaths = config.getListOfPaths();
        if (listOfPaths != null) {
            for (String siteProperty : listOfPaths) {
                String[] sitePropertyConfig = siteProperty.split(":");
                if (sitePropertyConfig.length == 2) {
                    paths.put(sitePropertyConfig[0], sitePropertyConfig[1]);
                } else {
                    LOG.error("Invalid Site property configuration {}", siteProperty);
                }
            }
        }
        LOG.info("Event Handler is enabled:" + enabled);
    }

    @Override
    public void handleEvent(Event event) {
        if (enabled && slingSettingsService.getRunModes().contains(AUTHOR)) {
            final ReplicationAction action = ReplicationAction.fromEvent(event);
            if (action != null) {
                String listeningPath = action.getPath();
                String pagePath = paths.get(listeningPath);
                LOG.info("Listened {} ", listeningPath);
                if (StringUtils.isNotBlank(pagePath)) {
                    // try (ResourceResolver resourceResolver =
                    // Lab2020CommonMethods.getResourceResolver(resourceResolverFactory)) {
                    // LOG.info("Path to publish: {} ", pagePath);
                    // LOG.info("###########");
                    // replicator.replicate(resourceResolver.adaptTo(Session.class),
                    // ReplicationActionType.ACTIVATE,
                    // pagePath);
                    // } catch (ReplicationException e) {
                    // LOG.error("Failed to replicate", e);
                    // }
                }
            }
        }
    }
}
