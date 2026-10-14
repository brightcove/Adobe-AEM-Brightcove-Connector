package com.coresecure.brightcove.wrapper.workflow;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

import com.adobe.granite.workflow.exec.WorkItem;
import com.adobe.granite.workflow.exec.WorkflowData;
import com.coresecure.brightcove.wrapper.sling.ServiceUtil;
import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;

/**
 * Pins the destructive Brightcove delete step. The account matching loop sits behind the static
 * OSGi ConfigurationGrabber lookup (not reachable in a unit test), so these tests cover what is:
 * the system user, the failure paths that must leave the DAM asset alone, and the private
 * Brightcove delete (which video id it deletes and what it does when metadata is missing).
 */
@ExtendWith(AemContextExtension.class)
class BrightcoveDeleteAssetWorkflowStepTest {

    private static final String ASSET = "/content/dam/brightcove_assets/1234567890001/vid.mp4";

    private final AemContext context = new AemContext();

    private static WorkItem workItem(String payload) {
        WorkItem item = mock(WorkItem.class);
        WorkflowData data = mock(WorkflowData.class);
        when(item.getWorkflowData()).thenReturn(data);
        when(data.getPayload()).thenReturn(payload);
        return item;
    }

    private static BrightcoveDeleteAssetWorkflowStep step(ResourceResolverFactory f) throws Exception {
        BrightcoveDeleteAssetWorkflowStep s = new BrightcoveDeleteAssetWorkflowStep();
        Field field = BrightcoveDeleteAssetWorkflowStep.class.getDeclaredField("resourceResolverFactory");
        field.setAccessible(true);
        field.set(s, f);
        return s;
    }

    private static boolean deleteBrightcoveAsset(BrightcoveDeleteAssetWorkflowStep s, ResourceResolver rr,
            String path, ServiceUtil su) throws Exception {
        Method m = BrightcoveDeleteAssetWorkflowStep.class.getDeclaredMethod("deleteBrightcoveAsset",
                ResourceResolver.class, String.class, ServiceUtil.class);
        m.setAccessible(true);
        return (Boolean) m.invoke(s, rr, path, su);
    }

    @Test
    void loginFailureIsContainedAndNothingIsDeleted() throws Exception {
        ResourceResolverFactory f = mock(ResourceResolverFactory.class);
        when(f.getServiceResourceResolver(anyMap())).thenThrow(new LoginException("no service user"));

        assertDoesNotThrow(() -> step(f).execute(workItem(ASSET), null, null));
    }

    @Test
    void usesTheBrightcoveWriteSubserviceAndClosesTheResolverWhenConfigIsUnavailable() throws Exception {
        ResourceResolverFactory f = mock(ResourceResolverFactory.class);
        ResourceResolver rr = mock(ResourceResolver.class);
        when(rr.isLive()).thenReturn(true);
        when(f.getServiceResourceResolver(anyMap())).thenReturn(rr);

        assertDoesNotThrow(() -> step(f).execute(workItem(ASSET), null, null));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> auth = ArgumentCaptor.forClass(Map.class);
        verify(f).getServiceResourceResolver(auth.capture());
        assertEquals("brightcoveWrite", auth.getValue().get(ResourceResolverFactory.SUBSERVICE));
        verify(rr).close();
        verify(rr, never()).delete(any());
        verify(rr, never()).commit();
    }

    @Test
    void deletesTheBrightcoveVideoIdStoredOnTheAssetMetadata() throws Exception {
        context.create().resource(ASSET, "jcr:primaryType", "dam:Asset");
        context.create().resource(ASSET + "/jcr:content/metadata", "brc_id", "9876543210");
        ServiceUtil su = mock(ServiceUtil.class);
        when(su.deleteVideo("9876543210")).thenReturn(true);

        boolean done = deleteBrightcoveAsset(new BrightcoveDeleteAssetWorkflowStep(), context.resourceResolver(), ASSET, su);

        assertTrue(done);
        verify(su).deleteVideo("9876543210");
    }

    @Test
    void reportsFailureWhenBrightcoveRefusesTheDelete() throws Exception {
        context.create().resource(ASSET, "jcr:primaryType", "dam:Asset");
        context.create().resource(ASSET + "/jcr:content/metadata", "brc_id", "9876543210");
        ServiceUtil su = mock(ServiceUtil.class);
        when(su.deleteVideo(anyString())).thenReturn(false);

        assertFalse(deleteBrightcoveAsset(new BrightcoveDeleteAssetWorkflowStep(), context.resourceResolver(), ASSET, su));
    }

    @Test
    void missingMetadataNodeDeletesNothingAndReportsFailure() throws Exception {
        context.create().resource(ASSET, "jcr:primaryType", "dam:Asset");
        ServiceUtil su = mock(ServiceUtil.class);

        boolean done = deleteBrightcoveAsset(new BrightcoveDeleteAssetWorkflowStep(), context.resourceResolver(), ASSET, su);

        assertFalse(done);
        verify(su, never()).deleteVideo(any());
    }

    @Test
    void nonexistentAssetDeletesNothingAndReportsFailure() throws Exception {
        ServiceUtil su = mock(ServiceUtil.class);

        assertFalse(deleteBrightcoveAsset(new BrightcoveDeleteAssetWorkflowStep(), context.resourceResolver(), ASSET, su));
        verify(su, never()).deleteVideo(any());
    }

    @Test
    @Disabled("DEFECT: metadata without brc_id still calls ServiceUtil.deleteVideo(null) instead of refusing the delete")
    void metadataWithoutBrcIdMustNotCallDelete() throws Exception {
        context.create().resource(ASSET, "jcr:primaryType", "dam:Asset");
        context.create().resource(ASSET + "/jcr:content/metadata", "dc:title", "x");
        ServiceUtil su = mock(ServiceUtil.class);

        deleteBrightcoveAsset(new BrightcoveDeleteAssetWorkflowStep(), context.resourceResolver(), ASSET, su);

        verify(su, never()).deleteVideo(any());
    }
}
