package jaxle;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public record Response(int status, Map<String, String> headers, byte[] body) {
    public Response {
        headers = Map.copyOf(headers);
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
