package com.coresecure.brightcove.wrapper.utils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

/**
 * Keeps credentials out of log output. The shipped LogManager config enables DEBUG on non-prod
 * runmodes, so anything logged at DEBUG or TRACE can reach brightcove.log.
 *
 * <p>Rules: an Authorization header shows its scheme only ({@code Basic ***}, {@code Bearer ***});
 * a body shows no value for an OAuth token, client secret, password, AWS key or session token, or
 * an {@code X-Amz-*} signing parameter, whatever the spelling or encoding. Nothing is hashed or
 * truncated, so a log line can never be used to recover part of a credential.</p>
 */
// Context: docs/credential-logging.md (rules, what is pinned, what is deliberately not redacted)
public final class LogRedactor {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String MASK = "***";

    private static final Pattern SENSITIVE_HEADER = Pattern.compile(
            "(?i)^(proxy-)?authorization$|^cookie$|^set-cookie$|^x-api-key$"
                    + "|^x-amz-(security-token|signature|credential)$");

    /** Sensitive names with case, '_' and '-' removed: access_token, accessToken and Access-Token match. */
    private static final Set<String> SENSITIVE_KEYS = new HashSet<>(Arrays.asList(
            "accesstoken", "refreshtoken", "clientsecret", "password",
            "accesskeyid", "secretaccesskey", "sessiontoken",
            "xamzsecuritytoken", "xamzsignature", "xamzcredential"));

    private static final String KEY = "(?:access[_-]?token|refresh[_-]?token|client[_-]?secret|password"
            + "|access[_-]?key[_-]?id|secret[_-]?access[_-]?key|session[_-]?token"
            + "|x-amz-security-token|x-amz-signature|x-amz-credential)";

    /**
     * Non-JSON text: {@code key=v}, {@code key: v}, {@code "key":"v"}, {@code 'key':'v'},
     * {@code key%3Dv}, separated by {@code &}, {@code ;}, {@code ,} or whitespace. Group 1 is kept;
     * group 2/3 is a double/single-quoted value (escapes included), group 4 an unquoted one.
     */
    private static final Pattern KEY_VALUE = Pattern.compile(
            "(?i)((?<![A-Za-z0-9])([\"']?)" + KEY + "\\2\\s*(?::|=|%3D)\\s*)"
                    + "(?:\"((?:[^\"\\\\]|\\\\.)*)\"|'((?:[^'\\\\]|\\\\.)*)'"
                    + "|((?:(?!%26|%3B)[^&;,\\s\"'}\\]])*))");

    private LogRedactor() {
    }

    /** {@code key: value} for a log line, with the value reduced to its scheme when sensitive. */
    public static String header(String key, String value) {
        return key + ": " + headerValue(key, value);
    }

    /** The header value as safe to log: scheme only for Authorization-type headers. */
    public static String headerValue(String key, String value) {
        if (value == null || key == null || !SENSITIVE_HEADER.matcher(key).matches()) {
            return value;
        }
        String trimmed = value.trim();
        int space = trimmed.indexOf(' ');
        boolean authorization = key.toLowerCase(Locale.ROOT).endsWith("authorization");
        return authorization && space > 0 ? trimmed.substring(0, space) + " " + MASK : MASK;
    }

    /**
     * A request or response body with credential values masked. JSON is parsed and walked, so
     * escaped quotes and nesting cannot leak a tail; anything else goes through the text patterns.
     */
    public static String body(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                JsonNode tree = MAPPER.readTree(trimmed);
                if (tree != null && tree.isContainerNode()) {
                    return MAPPER.writeValueAsString(mask(tree));
                }
            } catch (Exception notJson) {
                // fall through to the text patterns
            }
        }
        return text(text);
    }

    private static JsonNode mask(JsonNode node) {
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> fields = obj.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (isSensitiveKey(field.getKey()) && !field.getValue().isNull()) {
                    field.setValue(TextNode.valueOf(MASK));
                } else {
                    field.setValue(mask(field.getValue()));
                }
            }
            return obj;
        }
        if (node.isArray()) {
            ArrayNode arr = (ArrayNode) node;
            for (int i = 0; i < arr.size(); i++) {
                arr.set(i, mask(arr.get(i)));
            }
            return arr;
        }
        if (node.isTextual()) {
            // A string value can itself carry credentials: a pre-signed URL's X-Amz-* query.
            String masked = text(node.asText());
            return masked.equals(node.asText()) ? node : TextNode.valueOf(masked);
        }
        return node;
    }

    private static boolean isSensitiveKey(String key) {
        return SENSITIVE_KEYS.contains(key.toLowerCase(Locale.ROOT).replace("_", "").replace("-", ""));
    }

    private static String text(String text) {
        Matcher m = KEY_VALUE.matcher(text);
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            String replacement;
            if (m.group(3) != null) {
                replacement = m.group(1) + "\"" + MASK + "\"";
            } else if (m.group(4) != null) {
                replacement = m.group(1) + "'" + MASK + "'";
            } else {
                replacement = m.group(1) + MASK;
            }
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        return out.toString();
    }
}
