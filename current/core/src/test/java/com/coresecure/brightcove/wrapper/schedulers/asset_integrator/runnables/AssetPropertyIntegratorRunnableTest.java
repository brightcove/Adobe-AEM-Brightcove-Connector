package com.coresecure.brightcove.wrapper.schedulers.asset_integrator.runnables;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import javax.jcr.Session;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.commons.mime.MimeTypeService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Pins the entry guards of the scheduled DAM import. Only the part before the OSGi
 * ConfigurationGrabber lookup is reachable in a unit test (ServiceUtil.getConfigurationGrabber is
 * a static OSGi call and the per-account loop builds ServiceUtil with new), so the per-account
 * iteration is NOT covered here. What is pinned: the system user used, that a missing session
 * writes nothing, and that no failure escapes the scheduler thread.
 */
class AssetPropertyIntegratorRunnableTest {

    private final MimeTypeService mime = mock(MimeTypeService.class);

    @Test
    void loginUsesTheBrightcoveWriteSubservice() throws Exception {
        ResourceResolverFactory factory = mock(ResourceResolverFactory.class);
        ResourceResolver rr = mock(ResourceResolver.class);
        when(factory.getServiceResourceResolver(anyMap())).thenReturn(rr);

        new AssetPropertyIntegratorRunnable(mime, factory, 1).run();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> auth = ArgumentCaptor.forClass(Map.class);
        verify(factory).getServiceResourceResolver(auth.capture());
        assertEquals("brightcoveWrite", auth.getValue().get(ResourceResolverFactory.SUBSERVICE));
    }

    @Test
    void noJcrSessionMeansNothingIsSaved() throws Exception {
        ResourceResolverFactory factory = mock(ResourceResolverFactory.class);
        ResourceResolver rr = mock(ResourceResolver.class);
        when(factory.getServiceResourceResolver(anyMap())).thenReturn(rr);
        when(rr.adaptTo(Session.class)).thenReturn(null);

        assertDoesNotThrow(() -> new AssetPropertyIntegratorRunnable(mime, factory, 1).run());

        verify(rr, never()).commit();
    }

    @Test
    void loginFailureDoesNotEscapeTheScheduler() throws Exception {
        ResourceResolverFactory factory = mock(ResourceResolverFactory.class);
        when(factory.getServiceResourceResolver(anyMap())).thenThrow(new LoginException("no service user"));

        assertDoesNotThrow(() -> new AssetPropertyIntegratorRunnable(mime, factory, 1).run());
    }

    @Test
    void unavailableConfigurationLookupDoesNotEscapeAndSavesNothing() throws Exception {
        ResourceResolverFactory factory = mock(ResourceResolverFactory.class);
        ResourceResolver rr = mock(ResourceResolver.class);
        Session session = mock(Session.class);
        when(factory.getServiceResourceResolver(anyMap())).thenReturn(rr);
        when(rr.adaptTo(Session.class)).thenReturn(session);

        // Outside OSGi the grabber lookup throws; the run must swallow it and touch no content.
        assertDoesNotThrow(() -> new AssetPropertyIntegratorRunnable(mime, factory, 1).run());

        verify(session, never()).save();
    }
}
