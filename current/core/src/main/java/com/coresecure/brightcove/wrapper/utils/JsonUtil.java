package com.coresecure.brightcove.wrapper.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

/**
 * Jackson helpers that work on every Jackson 2.x the connector is deployed against.
 *
 * <p>⚠️ Do not call {@code JsonNode#toPrettyString()} anywhere in this bundle. It exists only
 * from Jackson 2.10, and the on-prem support floor (AEM 6.5 LTS, and the local 6.5.0 GA test
 * bed which ships Jackson 2.9.5) must resolve and run this bundle. This class is the one
 * place that knows how to pretty-print, so the floor is a compile-time fact rather than a
 * runtime {@code NoSuchMethodError}. Context: ONPREM-PARITY-PLAN.md §0 and §3 Phase 2.</p>
 */
public final class JsonUtil {

    private static final ObjectWriter PRETTY = new ObjectMapper().writerWithDefaultPrettyPrinter();

    private JsonUtil() {
    }

    /**
     * Pretty-prints a JSON tree. Equivalent to {@code node.toPrettyString()} on Jackson ≥ 2.10.
     *
     * @param node the tree; {@code null} yields the literal {@code "null"}
     * @return the indented JSON text
     */
    public static String pretty(JsonNode node) {
        if (node == null) {
            return "null";
        }
        try {
            return PRETTY.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            // A tree that was already built cannot fail to serialise; fall back to compact form
            // rather than throw from a logging or payload path.
            return node.toString();
        }
    }
}
