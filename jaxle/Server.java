package jaxle;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.ERROR;
import static jaxle.Response.invalidNameIndex;

public class Server {
    private static final int READ_TIMEOUT_MS = 20_000;
    private static final int MAX_BODY_BYTES = 500 * 1024;
    private static final int MAX_LINE_BYTES = 8 * 1024;
    private static final int MAX_HEADERS = 100;

    private static final System.Logger log = System.getLogger("jaxle.Server");

    private final ServerSocket socket;

    private final AtomicBoolean started = new AtomicBoolean();

    // Path is resolved first to distinguish 404 from 405.
    // When several patterns match, the first registered one wins.
    private final Map<String, Map<Method, Handler>> routes = new LinkedHashMap<>();

    private record RouteMatch(Map<Method, Handler> handlers, Map<String, String> params) {}
    private record Version(int major, int minor) {}

    /**
     * Creates a server bound to port 8080.
     *
     * <p>Same as {@link #Server(int) Server(8080)}.
     *
     * @throws UncheckedIOException if the port cannot be bound
     */
    public Server() {
        this(8080);
    }

    /**
     * Creates a server bound to the given port.
     *
     * <p>If the port is 0, the system picks a free one, which
     * {@link #port()} returns.
     *
     * @param port the port to listen on
     * @throws UncheckedIOException if the port cannot be bound
     * @throws IllegalArgumentException if the port is outside 0 to 65535
     */
    public Server(int port) {
        try {
            this.socket = new ServerSocket(port);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot bind port " + port, e);
        }
    }

    /**
     * {@return the port this server listens on}
     */
    public int port() {
        return socket.getLocalPort();
    }

    /**
     * Accepts and handles connections, blocking the calling thread.
     *
     * <p>This method never returns normally and ends only by throwing an
     * exception. Each connection is served in its own virtual thread and
     * carries a single request. Note that handlers may run concurrently,
     * including one handler serving several requests at once.
     *
     * @throws UncheckedIOException if accepting a connection fails
     * @throws IllegalStateException if the server has already been started
     */
    public void start() {
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException("server already started");
        }

        try {
            while (true) {
                Socket client = socket.accept();
                Thread.ofVirtual().start(() -> handleConnection(client));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void handleConnection(Socket client) {
        try {
            boolean headRequest = false;
            Response response;
            try {
                client.setSoTimeout(READ_TIMEOUT_MS);
                var in = new BufferedInputStream(client.getInputStream());
                String requestLine = readLine(in, 414);

                // Client closed the connection without sending any data.
                if (requestLine == null) {
                    return;
                }

                // Validate the request line before processing headers.
                // For example "GET /api/orders/1043/items?limit=20&offset=40 HTTP/1.1"
                // gives method, target and version.
                String[] parts = splitRequestLine(requestLine);
                Method method = parseMethod(parts[0]);
                Version version = parseVersion(parts[2]);
                String target = parts[1];

                // If a HEAD request arrives and the handler or the parsing methods throw
                // an exception, the server must respond with an error without a body.
                // But if this line is placed after the request parsing, handleConnection
                // will jump straight to catch, skipping the lines below, and the HEAD
                // request error will be sent with a body.
                headRequest = method == Method.HEAD;
                Map<String, String> headers = readHeaders(in);
                byte[] body = readBody(in, headers);

                if (version.major() != 1) {
                    response = Response.text(505, "only HTTP/1.x is supported");
                } else if (version.minor() >= 1 && !headers.containsKey("host")) {
                    throw new HttpException(400, "missing host header");
                } else if (method == null) {
                    response = errorResponse(501);
                } else {
                    response = dispatch(method, target, headers, body);
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

                response = Response.text(e.status(), message);
            } catch (SocketTimeoutException e) {
                log.log(DEBUG, "Request timeout");
                response = errorResponse(408);
            } catch (Throwable e) {
                log.log(ERROR, "Request failed", e);
                response = errorResponse(500);
            }

            try {
                var out = client.getOutputStream();
                writeResponse(out, response, headRequest);
            } catch (IOException e) {
                // Client went away before the response was written.
            }
        } finally {
            try {
                client.close();
            } catch (IOException e) {
                // nothing to do
            }
        }
    }

    private Response dispatch(Method method, String target, Map<String, String> headers, byte[] body) {
        String path = stripQuery(target);
        RouteMatch route = findRoute(path);

        if (route == null) {
            return errorResponse(404);
        }

        Handler handler = route.handlers().get(method);

        // "The HEAD method is identical to GET except that the server MUST NOT
        // send content in the response." (RFC 9110 9.3.2)
        // The GET handler is used only when no HEAD handler is registered.
        if (handler == null && method == Method.HEAD) {
            handler = route.handlers().get(Method.GET);
        }

        // The server answers OPTIONS itself only when no OPTIONS handler is registered.
        if (handler == null && method == Method.OPTIONS) {
            return Response.noContent().withHeader("allow", allowedMethods(route));
        }

        // "The origin server MUST generate an Allow header field in a 405
        // response containing a list of the target resource's currently
        // supported methods." (RFC 9110 15.5.6)
        if (handler == null) {
            return errorResponse(405).withHeader("allow", allowedMethods(route));
        }

        // The query is parsed only once a handler is found, because a bad query
        // like ?q=%zz would otherwise turn a 404 or 405 into a 400.
        Map<String, String> queryParams = parseQuery(rawQuery(target));
        Request request = new Request(method, path, headers, body, route.params(), queryParams);
        var response = handler.handle(request);

        if (response == null) {
            throw new IllegalStateException("handler returned null for " + path);
        }

        return response;
    }

    void writeResponse(OutputStream out, Response response, boolean headRequest) throws IOException {
        int status = response.status();
        // A 204 or 304 response "cannot contain content" (RFC 9110 15.3.5, 15.4.5).
        // Neither gets content-length either. It is forbidden on 204 (8.6), and on
        // 304 it would have to match a 200 response the server never built.
        boolean statusAllowsBody = status != 204 && status != 304;

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

        // A response to HEAD keeps the content-length a GET would get (RFC 9110 8.6).
        if (statusAllowsBody) {
            stringBuilder.append("content-length: ").append(body.length).append("\r\n");
        }

        stringBuilder.append("connection: close").append("\r\n");

        stringBuilder.append("\r\n");
        String head = stringBuilder.toString();

        out.write(head.getBytes(StandardCharsets.ISO_8859_1));

        if (statusAllowsBody && !headRequest) {
            out.write(body);
        }
    }

    private static String[] splitRequestLine(String requestLine) {
        String[] parts = requestLine.split(" ", -1);

        if (parts.length != 3) {
            throw new HttpException(400, "malformed request line");
        }

        // The target may hold only visible ASCII because the URI
        // grammar requires anything else to be percent-encoded
        String target = parts[1];
        for (int i = 0; i < target.length(); i++) {
            char c = target.charAt(i);
            boolean allowed = c >= 0x21 && c <= 0x7E;
            if (!allowed) {
                throw new HttpException(400, "invalid character in request target");
            }
        }

        for (String part : parts) {
            if (part.isEmpty()) {
                throw new HttpException(400, "malformed request line");
            }
        }

        return parts;
    }

    private static Version parseVersion(String version) {
        if (!version.startsWith("HTTP/")
            || version.length() != 8
            || version.charAt(6) != '.'
            || !isDigit(version.charAt(5)) || !isDigit(version.charAt(7))
        ) {
            throw new HttpException(400, "invalid HTTP version");
        }

        int major = version.charAt(5) - '0';
        int minor = version.charAt(7) - '0';

        return new Version(major, minor);
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static Method parseMethod(String name) {
        try {
            return Method.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Response errorResponse(int status) {
        return Response.text(status, reasonPhrase(status));
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
            case 408 -> "Request Timeout";
            case 409 -> "Conflict";
            case 413 -> "Content Too Large";
            case 414 -> "URI Too Long";
            case 415 -> "Unsupported Media Type";
            case 422 -> "Unprocessable Content";
            case 429 -> "Too Many Requests";
            case 431 -> "Request Header Fields Too Large";
            case 500 -> "Internal Server Error";
            case 501 -> "Not Implemented";
            case 502 -> "Bad Gateway";
            case 503 -> "Service Unavailable";
            case 504 -> "Gateway Timeout";
            case 505 -> "HTTP Version Not Supported";
            default -> "";
        };
    }

    private static String allowedMethods(RouteMatch route) {
        var allowed = EnumSet.copyOf(route.handlers().keySet());
        // The server answers OPTIONS on every path, with or without a handler.
        allowed.add(Method.OPTIONS);

        // Every path with GET also answers HEAD through the GET handler.
        if (allowed.contains(Method.GET)) {
            allowed.add(Method.HEAD);
        }

        return allowed
            .stream()
            .map(Method::name)
            .collect(Collectors.joining(", "));
    }

    /**
     * Registers a handler for requests with the given method and path.
     *
     * <p>A segment in braces, as in {@code /users/{id}}, declares a path
     * parameter whose value the handler reads through
     * {@link Request#param(String)}.
     *
     * <p>When the request path matches no route, the server answers with 404.
     * When the path matches but has no handler for the request method, the
     * server answers with 405, except for HEAD and OPTIONS. A HEAD request
     * without its own handler is served by the GET handler with the response
     * body left out, while an OPTIONS request without one gets 204 with the
     * methods of the path listed in the {@code allow} header.
     *
     * <p>Registering the same method and path again replaces the previous
     * handler. An exact path wins over a pattern even when it has no handler
     * for the request method, in which case the answer is 405. Among
     * patterns, the one registered first wins.
     *
     * <p>A trailing slash in the path is ignored, which lets {@code /users/}
     * match {@code /users}.
     *
     * @param method the request method this handler answers
     * @param path the path to match
     * @param handler the handler called for a matching request
     * @throws IllegalStateException if the server has already been started
     */
    public void addRoute(Method method, String path, Handler handler) {
        if (started.get()) {
            throw new IllegalStateException("cannot add routes after start()");
        }

        String normalizedPath = normalizePath(path);
        Map<Method, Handler> inner = this.routes.get(normalizedPath);

        if (inner == null) {
            inner = new EnumMap<>(Method.class);
            // First method for this path.
            this.routes.put(normalizedPath, inner);
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
        var normalizedPath = normalizePath(path);

        var inner = routes.get(normalizedPath);

        if (inner != null) {
            return new RouteMatch(inner, Map.of());
        }

        for (Map.Entry<String, Map<Method, Handler>> route : routes.entrySet()) {
            var params = matchPath(route.getKey(), normalizedPath);
            if (params != null) {
                return new RouteMatch(route.getValue(), params);
            }
        }

        return null;
    }

    private static String normalizePath(String path) {
        if (path.endsWith("/") && !path.equals("/")) {
            return path.substring(0, path.length() - 1);
        }

        return path;
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
                params.put(name, decode(pathPart.replace("+", "%2B")));
            } else if (!patternPart.equals(pathPart)) {
                return null;
            }
        }

        return params;
    }

    String readLine(InputStream in, int tooLongStatus) throws IOException {
        var buf = new ByteArrayOutputStream();
        while (true) {
            int b = in.read();
            if (b == '\n') break;
            if (b == '\r') {
                if (in.read() == '\n') break;
                throw new HttpException(400, "bare CR in line");
            }
            if (b == -1) {
                if (buf.size() == 0) {
                    return null;
                }

                // EOF in the middle of a line means an incomplete request.
                // If a client sent an incomplete request,
                // the server may respond with an error (RFC 9112, 8).
                throw new HttpException(400, "incomplete line");
            }

            if (buf.size() >= MAX_LINE_BYTES) {
                throw new HttpException(tooLongStatus, "line exceeds " + MAX_LINE_BYTES + " bytes");
            }

            buf.write(b);
        }

        return buf.toString(StandardCharsets.ISO_8859_1);
    }

    Map<String, String> readHeaders(InputStream in) throws IOException {
        Map<String, String> headers = new HashMap<>();
        int headerCount = 0;

        while (true) {
            String headerLine = readLine(in, 431);

            if (headerLine == null) {
                throw new HttpException(400, "incomplete headers");
            }

            if (headerLine.isEmpty()) break;

            String[] headerParts = headerLine.split(":", 2);
            if (headerParts.length != 2) {
                throw new HttpException(400, "malformed header line");
            }

            String name = headerParts[0].toLowerCase(Locale.ROOT);
            String value = headerParts[1].strip();

            if (name.isEmpty() || invalidNameIndex(name) != -1) {
                throw new HttpException(400, "invalid header name");
            }

            if (hasControlCharacter(value)) {
                throw new HttpException(400, "invalid header value");
            }

            headerCount++;
            if (headerCount > MAX_HEADERS) {
                throw new HttpException(431, "more than " + MAX_HEADERS + " headers");
            }

            if (headers.containsKey(name) && (name.equals("content-length") || name.equals("host"))) {
                throw new HttpException(400, "duplicate " + name);
            }

            headers.merge(name, value, (old, add) -> old + ", " + add);
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

        if (length > MAX_BODY_BYTES) {
            throw new HttpException(413, "body exceeds " + MAX_BODY_BYTES + " bytes");
        }

        byte[] payloadBytes = in.readNBytes(length);
        int bytesRead = payloadBytes.length;

        if (length != bytesRead) {
            throw new HttpException(400, "incomplete body");
        }

        return payloadBytes;
    }

    private static boolean hasControlCharacter(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean allowed = c == '\t' || (c >= 0x20 && c != 0x7F);

            if (!allowed) {
                return true;
            }
        }

        return false;
    }
}
