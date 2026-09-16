import java.util.Map;

public record Request(Method method, String path, Map<String, String> headers) {
    public Request {
        headers = Map.copyOf(headers);
    }
}
