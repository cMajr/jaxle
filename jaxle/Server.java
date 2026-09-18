package jaxle;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.ERROR;

public class Server {
    private static final System.Logger log = System.getLogger("jaxle.Server");

    private final ServerSocket socket;

    // Path is resolved first to distinguish 404 from 405.
    // When several patterns match, the first registered one wins.
    private final Map<String, Map<Method, Handler>> routes = new LinkedHashMap<>();

    private record RouteMatch(Map<Method, Handler> handlers, Map<String, String> params) {}

    /**
     * Creates a server bound to port 8080.
     *
     * <p>Same as {@link #Server(int) Server(8080)}.
     *
     * @throws IOException if the port cannot be bound
     */
    public Server() throws IOException {
        this(8080);
    }

    /**
     * Creates a server bound to the given port.
     *
     * <p>The port is bound right away. If it is already in use, this
     * constructor fails before any route is registered.
     *
     * @param port the port to listen on
     * @throws IOException if the port cannot be bound
     * @throws IllegalArgumentException if the port is outside 0 to 65535
     */
    public Server(int port) throws IOException {
        this.socket = new ServerSocket(port);
    }

    /**
     * Accepts and handles connections until the process ends.
     *
     * <p>This method blocks the calling thread and never returns normally.
     * Connections are served one at a time, with a single request per connection.
     *
     * @throws IOException if the listening socket fails
     */
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

                    // For example "GET /users/42?page=2 HTTP/1.1" gives method, target and version.
                    String[] parts = requestLine.split(" ");

                    if (parts.length != 3) {
                        throw new HttpException(400, "malformed request line");
                    }

                    Method method;

                    try {
                        method = Method.valueOf(parts[0]);
                    } catch (IllegalArgumentException e) {
                        writeResponse(out, Response.text(501, "Not Implemented"));
                        continue;
                    }

                    String path = stripQuery(parts[1]);
                    RouteMatch route = findRoute(path);

                    if (route == null) {
                        writeResponse(out, Response.text(404, "Not Found"));
                    } else {
                        Handler handler = route.handlers().get(method);
                        if (handler == null) {
                            var allowedMethods = route.handlers()
                                .keySet()
                                .stream()
                                .map(Method::name)
                                .collect(Collectors.joining(", "));

                            writeResponse(out, Response.text(405, "Method Not Allowed").withHeader("Allow", allowedMethods));
                        } else {
                            Map<String, String> queryParams = parseQuery(rawQuery(parts[1]));
                            Request request = new Request(method, path, headers, body, route.params(), queryParams);
                            writeResponse(out, handler.handle(request));
                        }
                    }
                } catch (HttpException e) {
                    String message;
                    if (e.status() >= 500) {
                        log.log(ERROR, "Request failed", e);
                        message = reasonPhrase(e.status());
                    } else {
                        log.log(DEBUG, e.getMessage());
                        message = e.getMessage();
                    }

                    try {
                        var out = client.getOutputStream();
                        writeResponse(out, Response.text(e.status(), message));
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
        int status = response.status();
        boolean hasBody = status != 204 && status != 304;

        StringBuilder stringBuilder = new StringBuilder();
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
        if (hasBody) {
            stringBuilder.append("content-length: ").append(body.length).append("\r\n");
        }

        stringBuilder.append("\r\n");
        String head = stringBuilder.toString();

        out.write(head.getBytes(StandardCharsets.ISO_8859_1));

        if (hasBody) {
            out.write(body);
        }
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

    /**
     * Registers a handler for requests with the given method and path.
     *
     * <p>A segment of the path written in braces is a path parameter, as in
     * {@code /users/{id}}. Its value reaches the handler through
     * {@link Request#param(String)}. A request whose path matches nothing
     * is answered with 404, a request whose path matches a route with no
     * handler for its method with 405.
     *
     * <p>Registering the same method and path again replaces the previous
     * handler. An exact path always wins over a pattern, and among patterns
     * the one registered first wins.
     *
     * @param method the request method this handler answers
     * @param path the path to match
     * @param handler the handler called for a matching request
     */
    public void addRoute(Method method, String path, Handler handler) {
        Map<Method, Handler> inner = this.routes.get(path);

        if (inner == null) {
            inner = new EnumMap<>(Method.class);
            // First method for this path.
            this.routes.put(path, inner);
        }

        // Map from routes is modified in place.
        inner.put(method, handler);
    }

    /**
     * Registers a handler for GET requests to the given path.
     *
     * <p>Same as {@link #addRoute(Method, String, Handler)
     * addRoute(Method.GET, path, handler)}.
     *
     * @param path the path to match
     * @param handler the handler called for a matching request
     */
    public void get(String path, Handler handler) {
        addRoute(Method.GET, path, handler);
    }

    /**
     * Registers a handler for POST requests to the given path.
     *
     * <p>Same as {@link #addRoute(Method, String, Handler)
     * addRoute(Method.POST, path, handler)}.
     *
     * @param path the path to match
     * @param handler the handler called for a matching request
     */
    public void post(String path, Handler handler) {
        addRoute(Method.POST, path, handler);
    }

    /**
     * Registers a handler for PUT requests to the given path.
     *
     * <p>Same as {@link #addRoute(Method, String, Handler)
     * addRoute(Method.PUT, path, handler)}.
     *
     * @param path the path to match
     * @param handler the handler called for a matching request
     */
    public void put(String path, Handler handler) {
        addRoute(Method.PUT, path, handler);
    }

    /**
     * Registers a handler for PATCH requests to the given path.
     *
     * <p>Same as {@link #addRoute(Method, String, Handler)
     * addRoute(Method.PATCH, path, handler)}.
     *
     * @param path the path to match
     * @param handler the handler called for a matching request
     */
    public void patch(String path, Handler handler) {
        addRoute(Method.PATCH, path, handler);
    }

    /**
     * Registers a handler for DELETE requests to the given path.
     *
     * <p>Same as {@link #addRoute(Method, String, Handler)
     * addRoute(Method.DELETE, path, handler)}.
     *
     * @param path the path to match
     * @param handler the handler called for a matching request
     */
    public void delete(String path, Handler handler) {
        addRoute(Method.DELETE, path, handler);
    }

    private static String stripQuery(String target) {
        int queryStart = target.indexOf('?');
        if (queryStart < 0) {
            return target;
        }

        return target.substring(0, queryStart);
    }

    private static String rawQuery(String target) {
        int queryStart = target.indexOf('?');
        if (queryStart < 0) {
            return "";
        }

        return target.substring(queryStart + 1);
    }

    private static Map<String, String> parseQuery(String query) {
        Map<String, String> queryParams = new HashMap<>();
        String[] pairs = query.split("&");

        for (String pair : pairs) {
            if (pair.isEmpty()) {
                continue;
            }

            String[] pairParts = pair.split("=", 2);
            String name = decode(pairParts[0]);
            String value = pairParts.length >= 2 ? decode(pairParts[1]) : "";

            // A repeated name keeps its first value.
            queryParams.putIfAbsent(name, value);
        }

        return queryParams;
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new HttpException(400, "invalid percent-encoding");
        }
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
                throw new HttpException(400, "malformed header line");
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
            throw new HttpException(400, "invalid content-length");
        }

        if (length < 0) {
            throw new HttpException(400, "invalid content-length");
        }

        byte[] payloadBytes = in.readNBytes(length);
        int bytesRead = payloadBytes.length;

        if (length != bytesRead) {
            throw new HttpException(400, "incomplete body");
        }

        return payloadBytes;
    }
}
