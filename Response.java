import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public record Response(int status, Map<String, String> headers, byte[] body) {
    public Response {
        headers = Map.copyOf(headers);
        body = body.clone();
    }

    public byte[] body() {
        return body.clone();
    }

    public static Response ok(String content) {
        return text(200, content);
    }

    public static Response text(int status, String content) {
        Map<String, String> headers = Map.of("content-type", "text/plain; charset=utf-8");
        byte[] body = content.getBytes(StandardCharsets.UTF_8);
        return new Response(status, headers, body);
    }

    public Response withHeader(String name, String value) {
        var copy = new HashMap<>(this.headers);
        copy.put(name.toLowerCase(Locale.ROOT), value);
        return new Response(status, copy, this.body);
    }
}
