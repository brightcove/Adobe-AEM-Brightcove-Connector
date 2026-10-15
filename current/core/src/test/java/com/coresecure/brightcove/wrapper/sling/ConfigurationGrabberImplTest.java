package com.coresecure.brightcove.wrapper.sling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import javax.jcr.Session;

import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.ResourceResolver;
import org.junit.jupiter.api.Test;

/**
 * Pins the multi-account registry in {@link ConfigurationGrabberImpl}: a current configuration
 * masks a legacy one for the same account at lookup time (not bind time), unbinding one falls
 * back to the other, and the per-user account list is the intersection of the account's allowed
 * groups with the user's group memberships.
 */
class ConfigurationGrabberImplTest {

    private static ConfigurationService cfg(String account, boolean legacy, String... groups) {
        ConfigurationService c = mock(ConfigurationService.class);
        when(c.getAccountID()).thenReturn(account);
        when(c.isLegacy()).thenReturn(legacy);
        when(c.getAllowedGroupsList()).thenReturn(Arrays.asList(groups));
        return c;
    }

    private static void bind(ConfigurationGrabberImpl g, ConfigurationService c) {
        g.bindConfigurationService(c, new HashMap<String, Object>());
    }

    private static void unbind(ConfigurationGrabberImpl g, ConfigurationService c) {
        g.unbindConfigurationService(c, new HashMap<String, Object>());
    }

    @Test
    void lookupOfUnknownAccountIsNull() {
        assertNull(new ConfigurationGrabberImpl().getConfigurationService("1234567890001"));
        assertTrue(new ConfigurationGrabberImpl().getAvailableServices().isEmpty());
    }

    @Test
    void multipleAccountsAreIndependentlyResolvable() {
        ConfigurationGrabberImpl g = new ConfigurationGrabberImpl();
        ConfigurationService a = cfg("1234567890001", false);
        ConfigurationService b = cfg("1234567890002", true);
        bind(g, a);
        bind(g, b);

        assertSame(a, g.getConfigurationService("1234567890001"));
        assertSame(b, g.getConfigurationService("1234567890002"));
        assertEquals(new HashSet<>(Arrays.asList("1234567890001", "1234567890002")), g.getAvailableServices());
    }

    @Test
    void currentMasksLegacyRegardlessOfBindOrder() {
        ConfigurationService current = cfg("1234567890001", false);
        ConfigurationService legacy = cfg("1234567890001", true);

        ConfigurationGrabberImpl g1 = new ConfigurationGrabberImpl();
        bind(g1, legacy);
        bind(g1, current);
        assertSame(current, g1.getConfigurationService("1234567890001"));

        ConfigurationGrabberImpl g2 = new ConfigurationGrabberImpl();
        bind(g2, current);
        bind(g2, legacy);
        assertSame(current, g2.getConfigurationService("1234567890001"));
        assertEquals(1, g2.getAvailableServices().size());
    }

    @Test
    void deletingCurrentFallsBackToLegacy() {
        ConfigurationGrabberImpl g = new ConfigurationGrabberImpl();
        ConfigurationService current = cfg("1234567890001", false);
        ConfigurationService legacy = cfg("1234567890001", true);
        bind(g, current);
        bind(g, legacy);

        unbind(g, current);

        assertSame(legacy, g.getConfigurationService("1234567890001"));
        assertTrue(g.getAvailableServices().contains("1234567890001"));
    }

    @Test
    void unbindOfAReplacedInstanceLeavesTheNewOneBound() {
        ConfigurationGrabberImpl g = new ConfigurationGrabberImpl();
        ConfigurationService old = cfg("1234567890001", false);
        ConfigurationService replacement = cfg("1234567890001", false);
        bind(g, old);
        bind(g, replacement);

        unbind(g, old);

        assertSame(replacement, g.getConfigurationService("1234567890001"));
    }

    @Test
    void unbindRemovesTheLastConfiguration() {
        ConfigurationGrabberImpl g = new ConfigurationGrabberImpl();
        ConfigurationService c = cfg("1234567890001", false);
        bind(g, c);
        unbind(g, c);
        assertNull(g.getConfigurationService("1234567890001"));
        assertTrue(g.getAvailableServices().isEmpty());
    }

    private static SlingHttpServletRequest request(Session session, UserManager um) {
        SlingHttpServletRequest req = mock(SlingHttpServletRequest.class);
        ResourceResolver rr = mock(ResourceResolver.class);
        when(req.getResourceResolver()).thenReturn(rr);
        when(rr.adaptTo(Session.class)).thenReturn(session);
        when(rr.adaptTo(UserManager.class)).thenReturn(um);
        return req;
    }

    private static Group group(String id) {
        Group gr = mock(Group.class);
        try {
            when(gr.getID()).thenReturn(id);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return gr;
    }

    @Test
    void accountsForRequestAreFilteredByUserGroups() throws Exception {
        ConfigurationGrabberImpl g = new ConfigurationGrabberImpl();
        bind(g, cfg("1234567890001", false, "editors"));
        bind(g, cfg("1234567890002", false, "admins", "ops"));
        bind(g, cfg("1234567890003", false));

        Session session = mock(Session.class);
        when(session.getUserID()).thenReturn("alice");
        UserManager um = mock(UserManager.class);
        Authorizable auth = mock(Authorizable.class);
        when(um.getAuthorizable("alice")).thenReturn(auth);
        when(auth.memberOf()).thenAnswer(i -> Arrays.asList(group("editors"), group("other")).iterator());

        Set<String> result = g.getAvailableServices(request(session, um));

        assertEquals(Collections.singleton("1234567890001"), result);
    }

    @Test
    void accountsForRequestIsNullWithoutSessionOrUserManagerOrAuthorizable() throws Exception {
        ConfigurationGrabberImpl g = new ConfigurationGrabberImpl();
        bind(g, cfg("1234567890001", false, "editors"));

        assertNull(g.getAvailableServices(request(null, mock(UserManager.class))));
        assertNull(g.getAvailableServices(request(mock(Session.class), null)));

        Session session = mock(Session.class);
        when(session.getUserID()).thenReturn("ghost");
        UserManager um = mock(UserManager.class);
        when(um.getAuthorizable("ghost")).thenReturn(null);
        assertNull(g.getAvailableServices(request(session, um)));
    }

    @Test
    void accountsForRequestSwallowsFailuresAndReturnsWhatItHas() {
        ConfigurationGrabberImpl g = new ConfigurationGrabberImpl();
        SlingHttpServletRequest req = mock(SlingHttpServletRequest.class);
        when(req.getResourceResolver()).thenThrow(new IllegalStateException("boom"));

        assertTrue(g.getAvailableServices(req).isEmpty());
    }
}
