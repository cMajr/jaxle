import java.util.Map;

public record Request(Method method, String path, Map<String, String> headers, byte[] body) {
    public Request {
        headers = Map.copyOf(headers);
        body = body.clone();
    }

    public byte[] body() {
        return body.clone();
    }
}
