package com.coresecure.brightcove.wrapper.listeners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * b5ac373 (merged as 8244647): a leading space in the {@code event.topics}
 * component property ({@code EventConstants.EVENT_TOPIC + "= org/apache/..."})
 * registered the wrong topic string with OSGi's EventAdmin, so
 * {@link BrightcovePublishListener} silently never received a single
 * distribution event on local AEM SDK / AEMaaCS. The fix also added the CQ
 * Replication topic ({@code ReplicationAction.EVENT_TOPIC}) so classic
 * replication keeps working too.
 *
 * <h2>Why this doesn't use reflection on {@code @Component}</h2>
 * {@code org.osgi.service.component.annotations.Component} is declared
 * {@code @Retention(RetentionPolicy.CLASS)} (verified with {@code javap} on
 * org.osgi.service.component.annotations:1.4.0 -- its
 * RuntimeVisibleAnnotations lists exactly {@code RetentionPolicy.CLASS}), so
 * the JVM discards it before the class loads and
 * {@code BrightcovePublishListener.class.getAnnotation(Component.class)} is
 * always {@code null} at test/runtime. There's also no Java constant backing
 * the topic literals -- they're inline strings in the annotation array. ASM
 * (which could read the CLASS-retention annotation straight from the
 * bytecode) is not on this module's test classpath, and pom.xml is out of
 * scope for this test-only change.
 *
 * <p>Instead this reads {@code target/classes/OSGI-INF/com.coresecure.
 * brightcove.wrapper.listeners.BrightcovePublishListener.xml}: the OSGi
 * Declarative Services descriptor that {@code bnd-maven-plugin} generates
 * FROM that same annotation during the {@code process-classes} phase, which
 * {@code mvn -pl core test} already runs before surefire. This is the literal
 * artifact the runtime Service Component Runtime consumes to register
 * event.topics, so it is closer to the real defect mechanism than re-parsing
 * the .java source text would be.</p>
 */
class BrightcovePublishListenerEventTopicsTest {

    private static final String DESCRIPTOR_RESOURCE =
            "OSGI-INF/com.coresecure.brightcove.wrapper.listeners.BrightcovePublishListener.xml";

    private static List<String> topicsFromDescriptorXml(String xml) throws Exception {
        // Note: not hardening against XXE here (no setFeature(disallow-doctype-decl)) --
        // the AEM uber-jar's bundled Xerces DocumentBuilderFactory throws
        // AbstractMethodError on that JAXP call, and both inputs to this method
        // are either our own generated build artifact or a string literal in
        // this test, never untrusted data.
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        try (InputStream in = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))) {
            Document doc = builder.parse(in);
            NodeList properties = doc.getElementsByTagName("property");
            for (int i = 0; i < properties.getLength(); i++) {
                Element property = (Element) properties.item(i);
                if ("event.topics".equals(property.getAttribute("name"))) {
                    String raw = property.getTextContent();
                    List<String> topics = new ArrayList<>();
                    for (String line : raw.split("\\r?\\n")) {
                        if (!line.isEmpty()) {
                            topics.add(line);
                        }
                    }
                    return topics;
                }
            }
        }
        fail("no event.topics property found in descriptor XML");
        return null;
    }

    private static void assertAllTrimmed(List<String> topics) {
        for (String topic : topics) {
            assertEquals(topic.trim(), topic, "topic \"" + topic + "\" carries "
                    + "leading/trailing whitespace -- OSGi's EventAdmin matches "
                    + "event.topics by exact string, so this silently drops all "
                    + "delivery (the b5ac373 defect)");
        }
    }

    /**
     * Reads the actual generated component descriptor for the real class and
     * checks the current, fixed state: both topics present, both trimmed.
     */
    @Test
    void generatedDescriptorRegistersBothTopicsTrimmed() throws Exception {
        InputStream in = getClass().getClassLoader().getResourceAsStream(DESCRIPTOR_RESOURCE);
        assertNotNull(in, "Generated SCR descriptor not found on the test classpath at "
                + DESCRIPTOR_RESOURCE + " -- this is produced by bnd-maven-plugin's "
                + "process-classes step, which `mvn -pl core test` runs before surefire; "
                + "if this is missing, the module wasn't compiled first.");
        String xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);

        List<String> topics = topicsFromDescriptorXml(xml);

        assertAllTrimmed(topics);
        assertTrue(topics.contains("org/apache/sling/distribution/agent/package/distributed"),
                "Sling Distribution topic missing: " + topics);
        assertTrue(topics.contains("com/day/cq/replication"),
                "CQ Replication topic (ReplicationAction.EVENT_TOPIC) missing: " + topics);
        assertEquals(2, topics.size(), "expected exactly two registered topics, found: " + topics);
    }

    /**
     * Discrimination check for the parsing/assertion logic itself: since main
     * code can't be mutated to reproduce the pre-fix descriptor, this feeds
     * {@link #assertAllTrimmed} a synthetic descriptor shaped exactly like the
     * pre-fix bug (a leading space baked into the property value, matching
     * {@code EventConstants.EVENT_TOPIC + "= org/apache/..."}) and confirms
     * the check actually fails on it, then passes on the clean equivalent.
     */
    @Test
    void trimCheckActuallyCatchesTheHistoricalLeadingSpaceDefect() throws Exception {
        String buggyXml = "<?xml version=\"1.0\"?>"
                + "<scr:component xmlns:scr=\"http://www.osgi.org/xmlns/scr/v1.3.0\">"
                + "<property name=\"event.topics\" type=\"String\">"
                + " org/apache/sling/distribution/agent/package/distributed\n"
                + "com/day/cq/replication</property>"
                + "</scr:component>";
        List<String> buggyTopics = topicsFromDescriptorXml(buggyXml);
        assertEquals(Arrays.asList(" org/apache/sling/distribution/agent/package/distributed",
                "com/day/cq/replication"), buggyTopics);
        assertThrows(org.opentest4j.AssertionFailedError.class, () -> assertAllTrimmed(buggyTopics),
                "the check must fail on the exact historical defect shape (leading space)");

        String cleanXml = buggyXml.replace(
                " org/apache/sling/distribution/agent/package/distributed",
                "org/apache/sling/distribution/agent/package/distributed");
        List<String> cleanTopics = topicsFromDescriptorXml(cleanXml);
        assertAllTrimmed(cleanTopics); // must not throw
    }
}
