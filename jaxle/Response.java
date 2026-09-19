package jaxle;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public record Response(int status, Map<String, String> headers, byte[] body) {
    public Response {
        Map<String, String> lowercased = new HashMap<>();

        for (Map.Entry<String, String> header : headers.entrySet()) {
            String key = header.getKey().toLowerCase(Locale.ROOT);
            String value = header.getValue();

            if (lowercased.putIfAbsent(key, value) != null) {
                throw new IllegalArgumentException("duplicate header " + key);
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
     * <p>The response carries no headers of its own. Neither a body nor a
     * {@code content-length} reaches the client, as required for this status.
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

    public static Response conflict(String content) {
        return text(409, content);
    }

    public static Response json(int status, String content) {
        Map<String, String> headers = Map.of("content-type", "application/json");
        byte[] body = content.getBytes(StandardCharsets.UTF_8);
        return new Response(status, headers, body);
    }

    /**
     * {@return a plain-text response with the given status}
     *
     * <p>The body is encoded in UTF-8 and sent as
     * {@code text/plain; charset=utf-8}. The status code is sent as is,
     * with an empty reason phrase if the server does not know it.
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
     * header with the same name. This instance is immutable and unaffected
     * by this method call.
     *
     * @param name the header name, in any case
     * @param value the header value
     */
    public Response withHeader(String name, String value) {
        var copy = new HashMap<>(this.headers);
        copy.put(name.toLowerCase(Locale.ROOT), value);
        return new Response(status, copy, this.body);
    }
}
