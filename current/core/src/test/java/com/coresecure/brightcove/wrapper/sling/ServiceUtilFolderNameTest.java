package com.coresecure.brightcove.wrapper.sling;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * The folder-name lookup the collision fix depends on: Video Cloud folder names are
 * unique case-insensitively, so a lookup that is case-SENSITIVE would miss the very
 * folder whose existence made the create fail, and the video would stay unfiled.
 *
 * <p>Pure function, no CMS connection: the list shape is the one
 * {@code CmsAPI.getFolders} returns (measured 2026-11-20: an array of
 * {@code id, account_id, name, created_at, updated_at, video_count}).
 * Context: current/docs/core-folder-sync.md "Trap 5".</p>
 */
class ServiceUtilFolderNameTest {

    private static final ArrayNode FOLDERS = JsonNodeFactory.instance.arrayNode();

    static {
        FOLDERS.add(folder("bc00000000000000000001", "12345"));
        FOLDERS.add(folder("bc00000000000000000002", "aem_test_folder"));
        FOLDERS.add(folder("bc00000000000000000003", "brightcove_assets"));
        FOLDERS.add(folder("bc00000000000000000004", "Studio Integration Tests"));
    }

    @Test
    void findsTheFolderWhoseNameIsExact() {
        assertEquals("bc00000000000000000003",
                ServiceUtil.folderIdByName(FOLDERS, "brightcove_assets"));
    }

    @Test
    void findsTheFolderWhoseNameDiffersOnlyByCase() {
        assertEquals("bc00000000000000000003",
                ServiceUtil.folderIdByName(FOLDERS, "Brightcove_Assets"),
                "Video Cloud refuses a create that differs only by case, so the lookup "
                        + "must find that folder or the video is left unfiled");
        assertEquals("bc00000000000000000004",
                ServiceUtil.folderIdByName(FOLDERS, "studio integration tests"));
    }

    @Test
    void noMatchIsEmptyNotTheFirstFolder() {
        assertEquals("", ServiceUtil.folderIdByName(FOLDERS, "not-a-folder"));
        assertEquals("", ServiceUtil.folderIdByName(FOLDERS, "brightcove_assets_2"),
                "a prefix is not a match: folders are a flat namespace, not a path");
    }

    @Test
    void anUnreadableListIsEmptyRatherThanAWrongId() {
        assertEquals("", ServiceUtil.folderIdByName(null, "brightcove_assets"),
                "a list that could not be read must not resolve to some other folder");
        assertEquals("", ServiceUtil.folderIdByName(JsonNodeFactory.instance.arrayNode(), "x"));
    }

    @Test
    void aBlankOrNullNameNeverMatches() {
        assertEquals("", ServiceUtil.folderIdByName(FOLDERS, null));
        assertEquals("", ServiceUtil.folderIdByName(FOLDERS, ""));
    }

    @Test
    void entriesWithoutANameAreSkipped() {
        ArrayNode odd = JsonNodeFactory.instance.arrayNode();
        odd.add(JsonNodeFactory.instance.objectNode().put("id", "no-name-here"));
        odd.add(folder("bc-real", "real"));
        assertEquals("bc-real", ServiceUtil.folderIdByName(odd, "real"));
    }

    private static ObjectNode folder(String id, String name) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("id", id);
        node.put("name", name);
        return node;
    }
}
