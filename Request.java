import java.nio.charset.StandardCharsets;
import java.util.Map;

public record Request(Method method, String path, Map<String, String> headers, byte[] body, Map<String, String> params) {
    public Request {
        headers = Map.copyOf(headers);
        body = body.clone();
        params = Map.copyOf(params);
    }

    /**
     * {@return a copy of the raw body bytes}
     *
     * <p>Each call returns a new array that can be changed without affecting
     * this request. A request without a body gives an empty array.
     */
    public byte[] body() {
        return body.clone();
    }

    /**
     * {@return the body decoded as UTF-8}
     *
     * <p>The charset parameter of the {@code Content-Type} header, if any,
     * is ignored. This method always replaces malformed-input sequences
     * with the replacement character {@code U+FFFD}, so it never fails,
     * even on a binary body. A request without a body gives an empty string.
     */
    public String text() {
        return new String(body, StandardCharsets.UTF_8);
    }

    /**
     * {@return the value of the named path parameter}
     *
     * <p>The value is taken from the path as is and is not percent-decoded.
     *
     * @param name the name inside the braces of the route pattern
     * @throws IllegalArgumentException if the route has no such parameter
     */
    public String param(String name) {
        String value = params.get(name);
        if (value == null) {
            throw new IllegalArgumentException("no path parameter named " + name);
        }

        return value;
    }
}
