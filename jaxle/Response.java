package jaxle;

import java.nio.charset.StandardCharsets;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public record Response(int status, Map<String, String> headers, byte[] body) {
    private static final BitSet TOKEN = new BitSet(128);

    static {
        for (char c = 'a'; c <= 'z'; c++) TOKEN.set(c);
        for (char c = 'A'; c <= 'Z'; c++) TOKEN.set(c);
        for (char c = '0'; c <= '9'; c++) TOKEN.set(c);

        for (char c : "!#$%&'*+-.^_`|~".toCharArray()) TOKEN.set(c);
    }

    /**
     * Builds a response from the given status, headers and body.
     *
     * <p>The constructor keeps its own copies of the body and the headers,
     * beyond the reach of changes made outside, and converts header names to
     * lowercase. By RFC 9110 5.6.2 a name must be a non-empty token, whereas a
     * value must fit the field-value rule (5.5), with no space or tab at the
     * ends. Two names that differ only in case describe the same header and
     * cannot both be given. A {@code content-length} header is not accepted
     * here, the server setting it from the length of the body.
     *
     * @param status the status code
     * @param headers the response headers, with names in any case
     * @param body the raw body bytes
     * @throws IllegalArgumentException if a name or a value breaks the rules
     *         above, if two names match ignoring case, or if a
     *         {@code content-length} header is given
     */
    public Response {
        Map<String, String> lowercased = new HashMap<>();

        for (Map.Entry<String, String> header : headers.entrySet()) {
            String name = header.getKey();
            checkName(name);
            String value = header.getValue();
            checkValue(name, value);

            if (lowercased.putIfAbsent(name.toLowerCase(Locale.ROOT), value) != null) {
                throw new IllegalArgumentException("duplicate header " + name);
            }
        }

        headers = Map.copyOf(lowercased);
        body = body.clone();
    }

    /**
     * {@return a copy of the raw body bytes}
     *
     * <p>Each call returns a new array that can be changed without affecting
     * this response. A response without a body gives an empty array.
     */
    public byte[] body() {
        return body.clone();
    }

    /**
     * {@return a plain-text response with status 200}
     *
     * <p>Same as {@link #text(int, String) text(200, content)}.
     *
     * @param content the body text
     */
    public static Response ok(String content) {
        return text(200, content);
    }

    /**
     * {@return a plain-text response with status 201}
     *
     * <p>Same as {@link #text(int, String) text(201, content)} with the
     * location added as the {@code location} header.
     *
     * @param location the path or URL of the created resource
     * @param content the body text
     */
    public static Response created(String location, String content) {
        return text(201, content).withHeader("location", location);
    }

    /**
     * {@return a response with status 204 and no body}
     *
     * <p>Neither a body nor a {@code content-length} reaches the client, as
     * required for this status.
     */
    public static Response noContent() {
        return new Response(204, Map.of(), new byte[0]);
    }

    /**
     * {@return a plain-text response with status 400}
     *
     * <p>Same as {@link #text(int, String) text(400, content)}.
     *
     * @param content the body text
     */
    public static Response badRequest(String content) {
        return text(400, content);
    }

    /**
     * {@return a plain-text response with status 404}
     *
     * <p>Same as {@link #text(int, String) text(404, content)}.
     *
     * @param content the body text
     */
    public static Response notFound(String content) {
        return text(404, content);
    }

    /**
     * {@return a plain-text response with status 409}
     *
     * <p>Same as {@link #text(int, String) text(409, content)}.
     *
     * @param content the body text
     */
    public static Response conflict(String content) {
        return text(409, content);
    }

    /**
     * {@return a JSON response with the given status}
     *
     * <p>The content is encoded in UTF-8, which RFC 8259 requires for JSON
     * exchanged between systems.
     *
     * <p>Unlike {@link #text(int, String) text}, the {@code content-type}
     * header carries no charset parameter, since the {@code application/json}
     * media type does not define one. Note that the content is sent as is,
     * without being parsed or validated as JSON.
     *
     * @param status the status code
     * @param content the JSON text
     */
    public static Response json(int status, String content) {
        Map<String, String> headers = Map.of("content-type", "application/json");
        byte[] body = content.getBytes(StandardCharsets.UTF_8);
        return new Response(status, headers, body);
    }

    /**
     * {@return a plain-text response with the given status}
     *
     * <p>The body is encoded in UTF-8 and sent as
     * {@code text/plain; charset=utf-8}.
     *
     * @param status the status code
     * @param content the body text
     */
    public static Response text(int status, String content) {
        Map<String, String> headers = Map.of("content-type", "text/plain; charset=utf-8");
        byte[] body = content.getBytes(StandardCharsets.UTF_8);
        return new Response(status, headers, body);
    }

    /**
     * {@return a copy of this response with the given header set}
     *
     * <p>The name is converted to lowercase before it replaces any existing
     * header with the same name.
     *
     * @param name the header name, in any case
     * @param value the header value
     */
    public Response withHeader(String name, String value) {
        var copy = new HashMap<>(this.headers);
        copy.put(name.toLowerCase(Locale.ROOT), value);
        return new Response(status, copy, this.body);
    }

    static int invalidNameIndex(String name) {
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!TOKEN.get(c)) {
                return i;
            }
        }

        return -1;
    }

    private static int invalidValueIndex(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean allowed = (c >= 0x21 && c <= 0x7E) || c == ' ' || c == '\t';

            if (!allowed) {
                return i;
            }
        }

        return -1;
    }

    private static boolean isPadded(String value) {
        return !value.isEmpty() && (isSpaceOrTab(value.charAt(0)) || isSpaceOrTab(value.charAt(value.length() - 1)));
    }

    private static boolean isSpaceOrTab(char c) {
        return c == ' ' || c == '\t';
    }

    private static void checkName(String name) {
        if (name.isEmpty()) {
            throw new IllegalArgumentException("empty header name");
        }

        int invalidNameIndex = invalidNameIndex(name);
        if (invalidNameIndex != -1) {
            throw new IllegalArgumentException(
                "invalid header name, character " + Integer.toHexString(name.charAt(invalidNameIndex))
                + " at index " + invalidNameIndex);
        }

        if (name.equalsIgnoreCase("content-length")) {
            throw new IllegalArgumentException("content-length cannot be set by user");
        }
    }

    private static void checkValue(String name, String value) {
        if (isPadded(value)) {
            throw new IllegalArgumentException("padded header value " + name);
        }

        int invalidValueIndex = invalidValueIndex(value);
        if (invalidValueIndex != -1) {
            throw new IllegalArgumentException(
                "invalid header value, character " + Integer.toHexString(value.charAt(invalidValueIndex))
                + " at index " + invalidValueIndex);
        }
    }
}
