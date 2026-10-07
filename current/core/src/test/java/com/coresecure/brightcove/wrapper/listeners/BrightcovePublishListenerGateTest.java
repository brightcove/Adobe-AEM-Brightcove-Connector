package com.coresecure.brightcove.wrapper.listeners;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.settings.SlingSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.osgi.service.event.Event;

import com.day.cq.replication.ReplicationAction;

/**
 * The two gates in front of {@link BrightcovePublishListener#handleEvent}: the
 * listener only does anything when its {@code isEnabled} config is true AND the
 * instance runs as author. Parity-matrix rows 31-33 depend on both, and the
 * metatype default for {@code isEnabled} is false (no config ships in ui.config), so
 * a fresh install never syncs to Brightcove on publish until someone enables it.
 *
 * <p>"Did anything" is observed at the first side effect, the request for the
 * {@code brightcoveWrite} service resolver. Past that point the listener calls
 * {@code ServiceUtil.getConfigurationGrabber()}, which needs a live OSGi framework,
 * so what happens after the gate (create vs update, folder sync) is not reachable
 * from a unit test without a seam; tests/e2e/specs/dam-publish-tier.spec.js measures
 * it against a real author/publish pair.</p>
 *
 * <p>Both event topics are driven (b5ac373 registered both): the classic CQ
 * replication topic and the Sling Content Distribution topic.</p>
 */
class BrightcovePublishListenerGateTest {

    private static final String DISTRIBUTION_TOPIC = "org/apache/sling/distribution/agent/package/distributed";
    private static final String ASSET = "/content/dam/brightcove_assets/12345/sample.mp4";

    private BrightcovePublishListener listener;
    private ResourceResolverFactory rrf;
    private ResourceResolver rr;
    private SlingSettingsService settings;

    @BeforeEach
    void setUp() throws Exception {
        listener = new BrightcovePublishListener();
        rrf = mock(ResourceResolverFactory.class);
        rr = mock(ResourceResolver.class);
        when(rrf.getServiceResourceResolver(any())).thenReturn(rr);
        settings = mock(SlingSettingsService.class);
        inject("resourceResolverFactory", rrf);
        inject("slingSettings", settings);
    }

    @Test
    void disabledListenerIgnoresBothTopics() throws Exception {
        configure(false, "author");
        listener.handleEvent(replicationEvent());
        listener.handleEvent(distributionEvent());
        verify(rrf, never()).getServiceResourceResolver(any());
    }

    @Test
    void enabledListenerIgnoresEventsOnPublish() throws Exception {
        configure(true, "publish");
        listener.handleEvent(replicationEvent());
        listener.handleEvent(distributionEvent());
        verify(rrf, never()).getServiceResourceResolver(any());
    }

    @Test
    void enabledListenerOnAuthorActsOnBothTopicsAsTheBrightcoveWriteServiceUser() throws Exception {
        configure(true, "author");
        listener.handleEvent(replicationEvent());
        listener.handleEvent(distributionEvent());
        // brightcoveWrite is the subservice ui.config maps to brightcove_admin
        // (ServiceUserMapperImpl.amended-brightcove_admin). A different name here would
        // fail login on a real instance and the listener would log and drop the event.
        verify(rrf, times(2)).getServiceResourceResolver(argThat(m ->
                "brightcoveWrite".equals(m.get(ResourceResolverFactory.SUBSERVICE))));
        // try-with-resources: the resolver is released even when the handler bails out.
        verify(rr, times(2)).close();
    }

    private void configure(boolean enabled, String runMode) {
        BrightcovePublishListener.EventListenerPageActivationListenerConfiguration config =
                mock(BrightcovePublishListener.EventListenerPageActivationListenerConfiguration.class);
        when(config.isEnabled()).thenReturn(enabled);
        listener.activate(config);
        when(settings.getRunModes()).thenReturn(new HashSet<>(Collections.singleton(runMode)));
    }

    private static Event replicationEvent() {
        Map<String, Object> props = new HashMap<>();
        props.put("type", "Activate");
        props.put("paths", new String[] {ASSET});
        props.put("userId", "admin");
        return new Event(ReplicationAction.EVENT_TOPIC, props);
    }

    private static Event distributionEvent() {
        Map<String, Object> props = new HashMap<>();
        props.put("distribution.type", "ADD");
        props.put("distribution.paths", new String[] {ASSET});
        return new Event(DISTRIBUTION_TOPIC, props);
    }

    private void inject(String field, Object value) throws Exception {
        Field f = BrightcovePublishListener.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(listener, value);
    }
}
