import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import static java.lang.System.Logger.Level.ERROR;

public class Server {
    private static final System.Logger log = System.getLogger("jaxle.Server");

    ServerSocket socket;

    // Path is resolved first to distinguish 404 from 405.
    Map<String, Map<Method, Handler>> routes = new HashMap<>();

    public Server() throws IOException {
        this.socket = new ServerSocket(8080);
    }

    public void start() throws IOException {
        while (true) {
            Socket client = socket.accept();
            try (client) {
                try {
                    var in = new BufferedInputStream(client.getInputStream());
                    String requestLine = readLine(in);

                    // Client closed the connection without sending any data.
                    if (requestLine == null) {
                        continue;
                    }

                    var headers = readHeaders(in);

                    String[] parts = requestLine.split(" ");

                    if (parts.length != 3) {
                        throw new BadRequestException("malformed request line");
                    }

                    Method method;

                    try { 
                        method = Method.valueOf(parts[0]);
                    } catch (IllegalArgumentException e) {
                        client.getOutputStream().write(response(501, "Not Implemented", "Not Implemented").getBytes(StandardCharsets.UTF_8));
                        continue;
                    }

                    String path = parts[1];

                    var out = client.getOutputStream();

                    Map<Method, Handler> inner = routes.get(path);

                    if (inner == null) {
                        out.write(response(404, "Not Found", "Not Found").getBytes(StandardCharsets.UTF_8));
                    } else {
                        Handler handler = inner.get(method);
                        if (handler == null) {
                            var allowedMethods = inner
                                .keySet()
                                .stream()
                                .map(Method::name)
                                .collect(Collectors.joining(", "));

                            out.write(response(405, "Method Not Allowed", "Method Not Allowed", Map.of("Allow", allowedMethods)).getBytes(StandardCharsets.UTF_8));
                        } else {
                            Request request = new Request(method, path, headers);
                            out.write(response(200, "OK", handler.handle(request)).getBytes(StandardCharsets.UTF_8));
                        }
                    }
                } catch (BadRequestException e) {
                    client.getOutputStream().write(response(400, "Bad Request", "Bad Request").getBytes(StandardCharsets.UTF_8));
                } catch (Exception e) {
                    log.log(ERROR, "Request failed", e);
                    try {
                        var out = client.getOutputStream();
                        out.write(response(500, "Internal Server Error", "Internal Server Error").getBytes(StandardCharsets.UTF_8));
                    } catch (Exception suppressed) {
                        // Connection is already broken, the real cause is logged above.
                    }
                }
            }
        }
    }

    String response(int status, String text, String body) {
        return response(status, text, body, Map.of());
    }

    String response(int status, String text, String body, Map<String, String> headers) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder
            .append("HTTP/1.1 ")
            .append(status)
            .append(" ")
            .append(text)
            .append("\r\n")
            .append("Content-Length: ")
            .append(body.getBytes(StandardCharsets.UTF_8).length)
            .append("\r\n");

        for (Map.Entry<String, String> header : headers.entrySet()) {
            stringBuilder.append(header.getKey()).append(": ").append(header.getValue()).append("\r\n");
        }

        stringBuilder.append("\r\n");
        stringBuilder.append(body);

        return stringBuilder.toString();
    }

    void addRoute(Method method, String path, Handler handler) {
        Map<Method, Handler> inner = this.routes.get(path);

        if (inner == null) {
            inner = new HashMap<>();
            // First method for this path.
            this.routes.put(path, inner);
        }

        // Map from routes is modified in place.
        inner.put(method, handler);
    }

    String readLine(InputStream in) throws IOException {
        var buf = new ByteArrayOutputStream();
        while (true) {
            int b = in.read();
            if (b == '\n') break;
            if (b == '\r') continue;
            if (b == -1) {
                if (buf.size() == 0) {
                    return null;
                } else {
                    break;
                }
            }
            buf.write(b);
        }

        return buf.toString(StandardCharsets.ISO_8859_1);
    }

    Map<String, String> readHeaders(InputStream in) throws IOException {
        Map<String, String> headers = new HashMap<>();

        while (true) {
            String headerLine = readLine(in);
            if (headerLine == null || headerLine.isEmpty()) break;
            String[] headerParts = headerLine.split(":", 2);
            if (headerParts.length != 2) {
                throw new BadRequestException("Bad Request");
            }
            headers.put(headerParts[0].toLowerCase(Locale.ROOT), headerParts[1].strip());
        }

        return headers;
    }
}
