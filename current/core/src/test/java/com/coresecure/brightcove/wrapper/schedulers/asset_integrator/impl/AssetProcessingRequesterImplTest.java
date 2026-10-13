package com.coresecure.brightcove.wrapper.schedulers.asset_integrator.impl;

import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.engine.SlingRequestProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * BGS-1747: the requester sends exactly the Assets UI "Reprocess" command, and only for an asset that
 * nothing has picked up yet. Cloud build only (excluded from the onprem profile with its subject).
 * Context: current/docs/asset-processing.md
 */
@ExtendWith(AemContextExtension.class)
class AssetProcessingRequesterImplTest {

    private static final String ASSET = "/content/dam/brightcove_assets/acct1/123.mp4";

    private final AemContext context = new AemContext();
    private SlingRequestProcessor processor;
    private AssetProcessingRequesterImpl requester;

    @BeforeEach
    void setUp() {
        processor = mock(SlingRequestProcessor.class);
        requester = new AssetProcessingRequesterImpl(processor);
    }

    private void assetInState(String state) {
        if (state == null) {
            context.create().resource(ASSET + "/jcr:content");
        } else {
            context.create().resource(ASSET + "/jcr:content", "dam:assetState", state);
        }
    }

    private void respondWith(int status) throws Exception {
        doAnswer(inv -> {
            ((HttpServletResponse) inv.getArgument(1)).setStatus(status);
            return null;
        }).when(processor).processRequest(any(HttpServletRequest.class), any(HttpServletResponse.class), any(ResourceResolver.class));
    }

    @Test
    void anUnprocessedAssetGetsTheReprocessCommand() throws Exception {
        assetInState("unProcessed");
        respondWith(200);

        assertTrue(requester.requestProcessing(context.resourceResolver(), ASSET));

        ArgumentCaptor<HttpServletRequest> sent = ArgumentCaptor.forClass(HttpServletRequest.class);
        verify(processor).processRequest(sent.capture(), any(HttpServletResponse.class), any(ResourceResolver.class));
        SlingHttpServletRequest request = (SlingHttpServletRequest) sent.getValue();
        assertEquals("POST", request.getMethod());
        assertEquals("/bin/asynccommand", request.getResource().getPath());
        assertArrayEquals(new String[] {"PROCESS"}, request.getParameterValues("operation"));
        assertArrayEquals(new String[] {ASSET}, request.getParameterValues("asset"));
    }

    @Test
    void anAssetAlreadyBeingProcessedIsNotQueuedTwice() throws Exception {
        assetInState("processing");
        assertFalse(requester.requestProcessing(context.resourceResolver(), ASSET));
        verify(processor, never()).processRequest(any(), any(), any());
    }

    @Test
    void anUnknownStateIsNotTreatedAsUnprocessed() throws Exception {
        assetInState(null); // absent is not "unProcessed"
        assertFalse(requester.requestProcessing(context.resourceResolver(), ASSET));
        verify(processor, never()).processRequest(any(), any(), any());
    }

    @Test
    void aRefusedCommandReportsFalse() throws Exception {
        assetInState("unProcessed");
        respondWith(403);
        assertFalse(requester.requestProcessing(context.resourceResolver(), ASSET));
    }

    @Test
    void aFailingCommandNeverThrowsIntoTheImport() throws Exception {
        assetInState("unProcessed");
        doThrow(new IOException("boom")).when(processor).processRequest(any(), any(), any());
        assertFalse(requester.requestProcessing(context.resourceResolver(), ASSET));
    }
}
