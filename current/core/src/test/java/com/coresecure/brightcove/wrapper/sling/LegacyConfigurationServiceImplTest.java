package com.coresecure.brightcove.wrapper.sling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

/**
 * Pins the attribute ids bnd generates for {@link LegacyConfigurationServiceImpl.LegacyConfig}
 * to the exact 6.0.x property names. A single underscore in a component-property-type method
 * name maps to a dot ({@code client_id()} -> {@code client.id}), which made every legacy value
 * except {@code key} read as empty on 2026-09-17; the names use double underscores for that
 * reason. Reads the metatype XML from target/classes, so it runs after compilation without an
 * OSGi framework.
 */
class LegacyConfigurationServiceImplTest {

    private static final Set<String> LEGACY_KEYS = new HashSet<String>(java.util.Arrays.asList(
            "accountAlias", "key", "client_id", "client_secret", "allowed_groups", "playersstore",
            "defVideoPlayerID", "defVideoPlayerKey", "defPlaylistPlayerID", "defPlaylistPlayerKey",
            "proxy", "asset_integration_path", "ingest_profile"));

    @Test
    void metatypeAttributeIdsAreTheLegacyPropertyNames() throws Exception {
        String resource = "/OSGI-INF/metatype/com.coresecure.brightcove.wrapper.sling.LegacyConfigurationServiceImpl$LegacyConfig.xml";
        InputStream in = getClass().getResourceAsStream(resource);
        assertNotNull(in, "metatype descriptor not generated: " + resource);
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        Document doc = f.newDocumentBuilder().parse(in);
        NodeList ads = doc.getElementsByTagNameNS("*", "AD");
        Set<String> ids = new HashSet<String>();
        for (int i = 0; i < ads.getLength(); i++) {
            ids.add(ads.item(i).getAttributes().getNamedItem("id").getNodeValue());
        }
        assertEquals(LEGACY_KEYS, ids, "legacy metatype attribute ids must equal the 6.0.x property names");
    }

    @Test
    void legacyPidIsTheSixDotZeroFactoryPid() {
        assertEquals("com.coresecure.brightcove.wrapper.sling.BrcServiceImpl", LegacyConfigurationServiceImpl.LEGACY_PID);
    }
}
