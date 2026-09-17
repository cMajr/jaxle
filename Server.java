import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.ERROR;

public class Server {
    private static final System.Logger log = System.getLogger("jaxle.Server");

    ServerSocket socket;

    // Path is resolved first to distinguish 404 from 405.
    // When several patterns match, the first registered one wins.
    Map<String, Map<Method, Handler>> routes = new LinkedHashMap<>();

    private record RouteMatch(Map<Method, Handler> handlers, Map<String, String> params) {}

    public Server() throws IOException {
        this.socket = new ServerSocket(8080);
    }

    public void start() throws IOException {
        while (true) {
            Socket client = socket.accept();
            try (client) {
                try {
                    var in = new BufferedInputStream(client.getInputStream());
                    var out = client.getOutputStream();
                    String requestLine = readLine(in);

                    // Client closed the connection without sending any data.
                    if (requestLine == null) {
                        continue;
                    }

                    var headers = readHeaders(in);
                    byte[] body = readBody(in, headers);

                    String[] parts = requestLine.split(" ");

                    if (parts.length != 3) {
                        throw new BadRequestException("malformed request line");
                    }

                    Method method;

                    try {
                        method = Method.valueOf(parts[0]);
                    } catch (IllegalArgumentException e) {
                        writeResponse(out, Response.text(501, "Not Implemented"));
                        continue;
                    }

                    // TODO: strip the query string, "/users/42?x=1" currently yields id "42?x=1".
                    String path = parts[1];

                    RouteMatch match = findRoute(path);

                    if (match == null) {
                        writeResponse(out, Response.text(404, "Not Found"));
                    } else {
                        Handler handler = match.handlers().get(method);
                        if (handler == null) {
                            var allowedMethods = match.handlers()
                                .keySet()
                                .stream()
                                .map(Method::name)
                                .collect(Collectors.joining(", "));

                            writeResponse(out, Response.text(405, "Method Not Allowed").withHeader("Allow", allowedMethods));
                        } else {
                            Request request = new Request(method, path, headers, body, match.params());
                            writeResponse(out, handler.handle(request));
                        }
                    }
                } catch (BadRequestException e) {
                    log.log(DEBUG, e.getMessage());
                    try {
                        var out = client.getOutputStream();
                        writeResponse(out, Response.text(400, "Bad Request"));
                    } catch (Exception suppressed) {
                        // Connection is already broken.
                    }
                } catch (Exception e) {
                    log.log(ERROR, "Request failed", e);
                    try {
                        var out = client.getOutputStream();
                        writeResponse(out, Response.text(500, "Internal Server Error"));
                    } catch (Exception suppressed) {
                        // Connection is already broken, the real cause is logged above.
                    }
                }
            }
        }
    }

    void writeResponse(OutputStream out, Response response) throws IOException {
        StringBuilder stringBuilder = new StringBuilder();
        int status = response.status();
        stringBuilder
            .append("HTTP/1.1 ")
            .append(status)
            .append(" ")
            .append(reasonPhrase(status))
            .append("\r\n");

        for (Map.Entry<String, String> header : response.headers().entrySet()) {
            stringBuilder.append(header.getKey()).append(": ").append(header.getValue()).append("\r\n");
        }

        byte[] body = response.body();
        // TODO: ignore or reject a user-supplied content-length,
        // otherwise the response carries two conflicting values.
        stringBuilder.append("content-length: ").append(body.length).append("\r\n");
        stringBuilder.append("\r\n");
        String head = stringBuilder.toString();

        out.write(head.getBytes(StandardCharsets.ISO_8859_1));
        out.write(body);
    }

    private static String reasonPhrase(int status) {
        return switch (status) {
            case 200 -> "OK";
            case 201 -> "Created";
            case 204 -> "No Content";
            case 301 -> "Moved Permanently";
            case 302 -> "Found";
            case 304 -> "Not Modified";
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 409 -> "Conflict";
            case 413 -> "Content Too Large";
            case 415 -> "Unsupported Media Type";
            case 422 -> "Unprocessable Content";
            case 429 -> "Too Many Requests";
            case 500 -> "Internal Server Error";
            case 501 -> "Not Implemented";
            case 503 -> "Service Unavailable";
            default -> "";
        };
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

    RouteMatch findRoute(String path) {
        var inner = routes.get(path);

        if (inner != null) {
            return new RouteMatch(inner, Map.of());
        }

        for (Map.Entry<String, Map<Method, Handler>> route : routes.entrySet()) {
            var params = matchPath(route.getKey(), path);
            if (params != null) {
                return new RouteMatch(route.getValue(), params);
            }
        }

        return null;
    }

    Map<String, String> matchPath(String pattern, String path) {
        String[] patternParts = pattern.split("/");
        String[] pathParts = path.split("/");

        if (patternParts.length != pathParts.length) {
            return null;
        }

        Map<String, String> params = new HashMap<>();

        for (int i = 0; i < patternParts.length; i++) {
            String patternPart = patternParts[i];
            String pathPart = pathParts[i];

            if (patternPart.startsWith("{") && patternPart.endsWith("}")) {
                String name = patternPart.substring(1, patternPart.length() - 1);
                params.put(name, pathPart);
            } else if (!patternPart.equals(pathPart)) {
                return null;
            }
        }

        return params;
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
                throw new BadRequestException("malformed header line");
            }
            headers.put(headerParts[0].toLowerCase(Locale.ROOT), headerParts[1].strip());
        }

        return headers;
    }

    byte[] readBody(InputStream in, Map<String, String> headers) throws IOException {
        String contentLength = headers.get("content-length");
        if (contentLength == null) {
            return new byte[0];
        }

        int length;
        try {
            length = Integer.parseInt(contentLength);
        } catch (NumberFormatException e) {
            throw new BadRequestException("invalid content-length");
        }

        if (length < 0) {
            throw new BadRequestException("invalid content-length");
        }

        byte[] payloadBytes = in.readNBytes(length);
        int bytesRead = payloadBytes.length;

        if (length != bytesRead) {
            throw new BadRequestException("incomplete body");
        }

        return payloadBytes;
    }
}
