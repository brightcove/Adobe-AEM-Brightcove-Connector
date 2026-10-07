package com.coresecure.brightcove.wrapper.utils;

import java.util.regex.Pattern;

/**
 * Keeps credentials out of log output. The shipped LogManager config enables DEBUG on non-prod
 * runmodes, so anything logged at DEBUG or TRACE can reach brightcove.log.
 *
 * <p>Rules: an Authorization header shows its scheme only ({@code Basic ***}, {@code Bearer ***});
 * a body shows no value for {@code access_token}, {@code refresh_token}, {@code client_secret} or
 * {@code password}, in JSON or form encoding. Nothing is hashed or truncated, so a log line can
 * never be used to recover part of a credential.</p>
 */
// Context: docs/credential-logging.md (rules, what is pinned, what is deliberately not redacted)
public final class LogRedactor {

    private static final Pattern SENSITIVE_HEADER =
            Pattern.compile("(?i)^(proxy-)?authorization$|^cookie$|^set-cookie$|^x-api-key$");
    private static final Pattern JSON_FIELD = Pattern.compile(
            "(?i)(\"(?:access_token|refresh_token|client_secret|password)\"\\s*:\\s*)\"[^\"]*\"");
    private static final Pattern FORM_FIELD = Pattern.compile(
            "(?i)((?:^|[?&\\s])(?:access_token|refresh_token|client_secret|password)=)[^&\\s]*");

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
        boolean authorization = key.toLowerCase().endsWith("authorization");
        return authorization && space > 0 ? trimmed.substring(0, space) + " ***" : "***";
    }

    /** A request or response body with credential fields masked. */
    public static String body(String text) {
        if (text == null) {
            return null;
        }
        String masked = JSON_FIELD.matcher(text).replaceAll("$1\"***\"");
        return FORM_FIELD.matcher(masked).replaceAll("$1***");
    }
}
