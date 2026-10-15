package com.coresecure.brightcove.wrapper.webservices;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.junit.jupiter.api.Test;

import com.coresecure.brightcove.wrapper.schedulers.asset_integrator.AssetIntegratorCronBundle;

/**
 * Pins the manual /bin/brightcove/dataload trigger: GET and POST run the same import, sized by
 * the cron bundle's thread count, and a failed configuration lookup is swallowed (the servlet
 * answers normally) without opening a service resolver. The per-account import loop sits behind
 * the static OSGi ConfigurationGrabber lookup and is not reachable in a unit test.
 */
class AssetPropertyIntegratorTest {

    private final ResourceResolverFactory factory = mock(ResourceResolverFactory.class);
    private final AssetIntegratorCronBundle cron = mock(AssetIntegratorCronBundle.class);
    private final SlingHttpServletRequest req = mock(SlingHttpServletRequest.class);
    private final SlingHttpServletResponse resp = mock(SlingHttpServletResponse.class);

    private AssetPropertyIntegrator servlet() throws Exception {
        AssetPropertyIntegrator s = new AssetPropertyIntegrator();
        set(s, "resourceResolverFactory", factory);
        set(s, "assetIntegratorCronBundle", cron);
        when(cron.getMaxThreadNum()).thenReturn(2);
        return s;
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Test
    void getUsesTheConfiguredThreadCountAndSwallowsLookupFailure() throws Exception {
        AssetPropertyIntegrator s = servlet();

        assertDoesNotThrow(() -> s.doGet(req, resp));

        verify(cron, atLeastOnce()).getMaxThreadNum();
        verify(factory, never()).getServiceResourceResolver(anyMap());
    }

    @Test
    void postRunsTheSameImportAsGet() throws Exception {
        AssetPropertyIntegrator s = servlet();

        assertDoesNotThrow(() -> s.doPost(req, resp));

        verify(cron, atLeastOnce()).getMaxThreadNum();
    }
}
