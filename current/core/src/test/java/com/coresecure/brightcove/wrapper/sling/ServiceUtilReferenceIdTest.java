package com.coresecure.brightcove.wrapper.sling;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * BGS-1705: a newly created Brightcove video must carry a stable, unique reference_id
 * so a duplicate create attempt is rejected by the CMS API. An explicit
 * brc_reference_id wins; otherwise the asset's jcr:uuid is used.
 */
class ServiceUtilReferenceIdTest {

    @Test
    void explicitReferenceIdWins() {
        assertEquals("my-ref", ServiceUtil.resolveReferenceId("my-ref", "uuid-123"));
    }

    @Test
    void fallsBackToJcrUuidWhenExplicitEmpty() {
        assertEquals("uuid-123", ServiceUtil.resolveReferenceId("", "uuid-123"));
    }

    @Test
    void fallsBackToJcrUuidWhenExplicitNull() {
        assertEquals("uuid-123", ServiceUtil.resolveReferenceId(null, "uuid-123"));
    }

    @Test
    void emptyWhenNoReferenceAndNoUuid() {
        assertEquals("", ServiceUtil.resolveReferenceId("", null));
        assertEquals("", ServiceUtil.resolveReferenceId(null, ""));
    }
}
