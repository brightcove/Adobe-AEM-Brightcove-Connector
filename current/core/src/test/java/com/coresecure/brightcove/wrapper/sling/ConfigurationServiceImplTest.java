package com.coresecure.brightcove.wrapper.sling;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Pins how {@link ConfigurationServiceImpl} surfaces its OSGi {@code Config}: each getter reads
 * the matching attribute, unset attributes fall back to the declared annotation defaults, and the
 * allowed-groups list drops blank entries. The Config annotation is faked with a Proxy that
 * answers from a map and otherwise returns the annotation's declared default, the same thing
 * the OSGi runtime does for an unset property.
 */
class ConfigurationServiceImplTest {

    private static ConfigurationServiceImpl.Config config(Map<String, Object> values) {
        InvocationHandler h = (proxy, method, args) -> {
            if (values.containsKey(method.getName())) {
                return values.get(method.getName());
            }
            return method.getDefaultValue();
        };
        return (ConfigurationServiceImpl.Config) Proxy.newProxyInstance(
                ConfigurationServiceImplTest.class.getClassLoader(),
                new Class<?>[] { ConfigurationServiceImpl.Config.class }, h);
    }

    private static ConfigurationServiceImpl service(Map<String, Object> values) {
        ConfigurationServiceImpl s = new ConfigurationServiceImpl();
        s.activate(config(values));
        return s;
    }

    @Test
    void gettersReturnConfiguredValues() {
        Map<String, Object> v = new HashMap<>();
        v.put("accountAlias", "alias");
        v.put("accountId", "1234567890001");
        v.put("clientId", "cid");
        v.put("clientSecret", "sec");
        v.put("playerStorePath", "/content/players");
        v.put("defaultVideoPlayerId", "vp");
        v.put("defaultVideoPlayerKey", "vpk");
        v.put("defaultPlaylistPlayerId", "pp");
        v.put("defaultPlaylistPlayerKey", "ppk");
        v.put("proxyServer", "proxy.example.test:3128");
        v.put("damIntegrationPath", "/content/dam/x");
        v.put("defaultIngestProfile", "prof");
        v.put("defaultTagInclude", "tag");
        ConfigurationServiceImpl s = service(v);

        assertEquals("alias", s.getAccountAlias());
        assertEquals("1234567890001", s.getAccountID());
        assertEquals("cid", s.getClientID());
        assertEquals("sec", s.getClientSecret());
        assertEquals("/content/players", s.getPlayersLoc());
        assertEquals("vp", s.getDefVideoPlayerID());
        assertEquals("vpk", s.getDefVideoPlayerKey());
        assertEquals("pp", s.getDefPlaylistPlayerID());
        assertEquals("ppk", s.getDefPlaylistPlayerKey());
        assertEquals("proxy.example.test:3128", s.getProxy());
        assertEquals("/content/dam/x", s.getAssetIntegrationPath());
        assertEquals("prof", s.getIngestProfile());
        assertEquals("tag", s.getTagInclude());
        assertFalse(s.isLegacy());
    }

    @Test
    void unsetAttributesFallBackToDeclaredDefaults() {
        ConfigurationServiceImpl s = service(new HashMap<>());

        assertEquals("", s.getAccountID());
        assertEquals("", s.getClientID());
        assertEquals("", s.getClientSecret());
        assertEquals("/content/brightcovetools/players", s.getPlayersLoc());
        assertEquals("default", s.getDefVideoPlayerID());
        assertEquals("default", s.getDefPlaylistPlayerID());
        assertEquals("/content/dam/brightcove_assets", s.getAssetIntegrationPath());
        assertEquals("", s.getProxy());
        assertEquals("", s.getIngestProfile());
        assertEquals("", s.getTagInclude());
        assertEquals("default", s.getDefVideoPlayerDataEmbedded());
    }

    @Test
    void defaultAllowedGroupsIsBlankSoNoGroupIsAllowed() {
        ConfigurationServiceImpl s = service(new HashMap<>());

        assertEquals(0, s.getAllowedGroups().length);
        assertTrue(s.getAllowedGroupsList().isEmpty());
    }

    @Test
    void allowedGroupsAreTrimmedAndBlankEntriesDropped() {
        Map<String, Object> v = new HashMap<>();
        v.put("allowedGroups", new String[] { " admins ", "", "  ", null, "editors" });
        ConfigurationServiceImpl s = service(v);

        assertArrayEquals(new String[] { "admins", "editors" }, s.getAllowedGroups());
        assertEquals(java.util.Arrays.asList("admins", "editors"), s.getAllowedGroupsList());
    }

    @Test
    void nullAllowedGroupsYieldsEmptyArray() {
        Map<String, Object> v = new HashMap<>();
        v.put("allowedGroups", null);
        assertEquals(0, service(v).getAllowedGroups().length);
    }

    @Test
    void reactivationReplacesTheConfig() {
        ConfigurationServiceImpl s = service(new HashMap<>());
        Map<String, Object> v = new HashMap<>();
        v.put("accountId", "1234567890002");
        s.activate(config(v));
        assertEquals("1234567890002", s.getAccountID());
    }
}
