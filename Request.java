import java.nio.charset.StandardCharsets;
import java.util.Map;

public record Request(Method method, String path, Map<String, String> headers, byte[] body) {
    public Request {
        headers = Map.copyOf(headers);
        body = body.clone();
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
}
