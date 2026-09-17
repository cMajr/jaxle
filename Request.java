import java.nio.charset.StandardCharsets;
import java.util.Map;

public record Request(Method method, String path, Map<String, String> headers, byte[] body, Map<String, String> params) {
    public Request {
        headers = Map.copyOf(headers);
        body = body.clone();
        params = Map.copyOf(params);
    }

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

    public String param(String name) {
        String value = params.get(name);
        if (value == null) {
            throw new IllegalArgumentException("no path parameter named " + name);
        }

        return value;
    }
}
