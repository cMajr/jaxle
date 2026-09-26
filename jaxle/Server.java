package jaxle;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Semaphore;

import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.ERROR;
import static jaxle.Response.invalidNameIndex;

public class Server implements AutoCloseable {
    private static final System.Logger log = System.getLogger("jaxle.Server");

    private static final int REQUEST_TIMEOUT_MS = 30_000;
    private static final int MAX_CONNECTIONS = 500;
    private static final int MAX_BODY_BYTES = 500 * 1024;
    private static final int MAX_LINE_BYTES = 8 * 1024;
    private static final int MAX_HEADER_BYTES = 32 * 1024;
    private static final int MAX_HEADERS = 100;

    private final ServerSocket socket;
    private final Semaphore connections = new Semaphore(MAX_CONNECTIONS);
    private final Object lock = new Object();
    private boolean started = false;
    private boolean closed = false;

    private record Version(int major, int minor) {}

    private final Router router = new Router();

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
     * Accepts and handles connections, blocking the calling thread until
     * the server is closed.
     *
     * <p>This method returns once {@link #close()} is called from another
     * thread, or right away if the server was closed before it started.
     * Each connection is served in its own virtual thread and carries a
     * single request. Note that handlers may run concurrently, including
     * one handler serving several requests at once.
     *
     * @throws UncheckedIOException if accepting a connection fails for a
     *         reason other than {@link #close()}
     * @throws IllegalStateException if the server has already been started
     */
    public void start() {
        synchronized (lock) {
            if (started) {
                throw new IllegalStateException("server already started");
            }

            started = true;
        }

        try {
            while (true) {
                Socket client = socket.accept();

                if (connections.tryAcquire()) {
                    Thread.ofVirtual().start(() -> handleConnection(client));
                } else {
                    reject(client);
                }
            }
        } catch (IOException e) {
            synchronized (lock) {
                if (closed) {
                    return;
                }
            }
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Stops accepting connections and releases the port.
     *
     * <p>A thread blocked in {@link #start()} returns normally. Requests
     * already accepted are still served to the end in their own threads
     * without this method waiting for them. Closing an already closed
     * server has no effect.
     *
     * @throws UncheckedIOException if the port cannot be released
     */
    @Override
    public void close() {
        try {
            synchronized (lock) {
                closed = true;
            }
            this.socket.close();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot close port " + port(), e);
        }
    }

    private void reject(Socket client) {
        try {
            ResponseWriter.write(client.getOutputStream(), errorResponse(503), false);
        } catch (IOException e) {
            // nothing to do
        } finally {
            try {
                client.close();
            } catch (IOException e) {
                // nothing to do
            }
        }
    }

    private void handleConnection(Socket client) {
        try {
            boolean headRequest = false;
            Response response;
            try {
                var in = new BufferedInputStream(new DeadlineInputStream(client, REQUEST_TIMEOUT_MS));
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
                String target = toOriginForm(parts[1]);

                // If a HEAD request arrives and the handler or the parsing methods throw
                // an exception, the server must respond with an error without a body.
                // But if this line is placed after the request parsing, handleConnection
                // will jump straight to catch, skipping the lines below, and the HEAD
                // request error will be sent with a body.
                headRequest = method == Method.HEAD;
                Map<String, String> headers = readHeaders(in);
                rejectTransferEncoding(headers);
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
                    message = ResponseWriter.reasonPhrase(e.status());
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
                ResponseWriter.write(out, response, headRequest);
            } catch (IOException e) {
                // Client went away before the response was written.
            }
        } finally {
            try {
                client.close();
            } catch (IOException e) {
                // nothing to do
            }

            connections.release();
        }
    }

    private Response dispatch(Method method, String target, Map<String, String> headers, byte[] body) {
        String path = QueryParser.stripQuery(target);
        Router.Match route = router.findRoute(path);

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
            return Response.noContent().withHeader("allow", Router.allowedMethods(route));
        }

        // "The origin server MUST generate an Allow header field in a 405
        // response containing a list of the target resource's currently
        // supported methods." (RFC 9110 15.5.6)
        if (handler == null) {
            return errorResponse(405).withHeader("allow", Router.allowedMethods(route));
        }

        // The query is parsed only once a handler is found, because a bad query
        // like ?q=%zz would otherwise turn a 404 or 405 into a 400.
        String rawQuery = QueryParser.rawQuery(target);
        Map<String, String> queryParams = QueryParser.parseQuery(rawQuery);
        Request request = new Request(method, path, headers, body, route.params(), queryParams);
        var response = handler.handle(request);

        if (response == null) {
            throw new IllegalStateException("handler returned null for " + path);
        }

        return response;
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

    // A client talking to a proxy puts the whole URI in the request line
    // (RFC 9112 3.2.2), as in "GET http://example.org/users/1 HTTP/1.1".
    // The server accepts this form as well and keeps only the path and query.
    private static String toOriginForm(String target) {
        if (target.startsWith("/") || target.equals("*")) {
            return target;
        }

        int authorityStart;
        if (target.regionMatches(true, 0, "http://", 0, 7)) {
            authorityStart = 7;
        } else if (target.regionMatches(true, 0, "https://", 0, 8)) {
            authorityStart = 8;
        } else {
            throw new HttpException(400, "invalid request target");
        }

        int end = target.length();
        for (int i = authorityStart; i < target.length(); i++) {
            char c = target.charAt(i);

            if (c == '/' || c == '?') {
                end = i;
                break;
            }
        }

        // RFC 9110 4.2.1 requires rejecting an http URI with an empty host.
        if (end == authorityStart) {
            throw new HttpException(400, "invalid request target");
        }

        String rest = target.substring(end);

        if (!rest.startsWith("/")) {
            return "/" + rest;
        }

        return rest;
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
        return Response.text(status, ResponseWriter.reasonPhrase(status));
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
        synchronized (lock) {
            if (started) {
                throw new IllegalStateException("cannot add routes after start()");
            }

            router.add(method, path, handler);
        }
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
        int headerBytes = 0;

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

            headerBytes = headerBytes + headerLine.length();
            if (headerBytes > MAX_HEADER_BYTES) {
                throw new HttpException(431, "headers exceed " + MAX_HEADER_BYTES + " bytes");
            }

            if (headers.containsKey(name) && (name.equals("content-length") || name.equals("host"))) {
                throw new HttpException(400, "duplicate " + name);
            }

            headers.merge(name, value, (old, add) -> old + ", " + add);
        }

        return headers;
    }

    private static void rejectTransferEncoding(Map<String, String> headers) {
        if (headers.containsKey("content-length") && headers.containsKey("transfer-encoding")) {
            throw new HttpException(400, "both content-length and transfer-encoding");
        }

        if (headers.containsKey("transfer-encoding")) {
            throw new HttpException(501, "transfer-encoding not supported");
        }
    }

    byte[] readBody(InputStream in, Map<String, String> headers) throws IOException {
        String contentLength = headers.get("content-length");

        if (contentLength == null) {
            return new byte[0];
        }

        int length = parseContentLength(contentLength);

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

    private static int parseContentLength(String value) {
        int length;

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!isDigit(c)) {
                throw new HttpException(400, "invalid content-length");
            }
        }

        try {
            length = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new HttpException(400, "invalid content-length", e);
        }

        return length;
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
