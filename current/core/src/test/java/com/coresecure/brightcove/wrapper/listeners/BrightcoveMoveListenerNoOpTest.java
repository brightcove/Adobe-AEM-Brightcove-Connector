package com.coresecure.brightcove.wrapper.listeners;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.settings.SlingSettingsService;
import org.junit.jupiter.api.Test;
import org.osgi.service.event.Event;

import com.day.cq.replication.ReplicationAction;
import com.day.cq.replication.Replicator;

/**
 * Characterises {@link BrightcoveMoveListener} as it ships: despite the name it has
 * NO Brightcove behaviour. It is a "Lab2020 Activation Event Listener" template whose
 * only action (re-replicating a configured path) is commented out, and it never looks
 * at DAM moves at all. So moving an asset between DAM folders does not move the video
 * between Video Cloud folders; that only happens when the asset is activated again
 * (BrightcovePublishListener -> FolderSyncUtil.syncFolder). The live expectation is
 * pinned as a test.fixme in tests/e2e/specs/dam-publish-tier.spec.js.
 *
 * <p>Driven with every gate open (enabled, author, a replication event for a
 * configured listening path), so a pass means "does nothing" rather than "was never
 * reached". If a real move handler is implemented, this test is expected to fail and
 * should be replaced by one that asserts the move.</p>
 */
class BrightcoveMoveListenerNoOpTest {

    @Test
    void replicationEventForAConfiguredPathTouchesNothing() throws Exception {
        BrightcoveMoveListener listener = new BrightcoveMoveListener();
        ResourceResolverFactory rrf = mock(ResourceResolverFactory.class);
        Replicator replicator = mock(Replicator.class);
        SlingSettingsService settings = mock(SlingSettingsService.class);
        when(settings.getRunModes()).thenReturn(new HashSet<>(Collections.singleton("author")));
        inject(listener, "resourceResolverFactory", rrf);
        inject(listener, "replicator", replicator);
        inject(listener, "slingSettingsService", settings);

        String asset = "/content/dam/brightcove_assets/12345/folder-a/sample.mp4";
        BrightcoveMoveListener.EventListenerPageActivationListenerConfiguration config =
                mock(BrightcoveMoveListener.EventListenerPageActivationListenerConfiguration.class);
        when(config.isEnabled()).thenReturn(true);
        when(config.getListOfPaths()).thenReturn(new String[] {asset + ":/content/dam/brightcove_assets/12345/folder-b"});
        listener.activate(config);

        Map<String, Object> props = new HashMap<>();
        props.put("type", "Activate");
        props.put("paths", new String[] {asset});
        props.put("userId", "admin");
        listener.handleEvent(new Event(ReplicationAction.EVENT_TOPIC, props));

        verifyNoInteractions(rrf, replicator);
    }

    private static void inject(Object target, String field, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }
}
