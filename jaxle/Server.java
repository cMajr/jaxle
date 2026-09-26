package jaxle;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Map;
import java.util.concurrent.Semaphore;

import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.ERROR;

public class Server implements AutoCloseable {
    private static final System.Logger log = System.getLogger("jaxle.Server");

    private static final int REQUEST_TIMEOUT_MS = 30_000;
    private static final int MAX_CONNECTIONS = 500;
    private static final int ACCEPT_RETRY_DELAY_MS = 600;

    private final ServerSocket socket;
    private final Semaphore connections = new Semaphore(MAX_CONNECTIONS);
    private final Object lock = new Object();
    private boolean started = false;
    private boolean closed = false;

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
     * When accepting a connection fails for any other reason, the error is
     * logged and the server tries again after a short pause. If the
     * calling thread is interrupted during that pause, the server is
     * closed and this method returns with the interrupt status set.
     * Each connection is served in its own virtual thread and carries a
     * single request. Note that handlers may run concurrently, including
     * one handler serving several requests at once.
     *
     * @throws UncheckedIOException if the port cannot be released after
     *         the calling thread is interrupted
     * @throws IllegalStateException if the server has already been started
     */
    public void start() {
        synchronized (lock) {
            if (started) {
                throw new IllegalStateException("server already started");
            }

            started = true;
        }

        while (true) {
            Socket client;
            try {
                client = socket.accept();
            } catch (IOException e) {
                synchronized (lock) {
                    if (closed) {
                        return;
                    }
                }

                log.log(ERROR, "Accept failed", e);
                try {
                    Thread.sleep(ACCEPT_RETRY_DELAY_MS);
                } catch (InterruptedException i) {
                    Thread.currentThread().interrupt();
                    close();
                    return;
                }

                continue;
            }

            if (connections.tryAcquire()) {
                Thread.ofVirtual().start(() -> handleConnection(client));
            } else {
                reject(client);
            }
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
                String requestLine = RequestParser.readLine(in, 414);

                // Client closed the connection without sending any data.
                if (requestLine == null) {
                    return;
                }

                // Validate the request line before processing headers.
                // For example "GET /api/orders/1043/items?limit=20&offset=40 HTTP/1.1"
                // gives method, target and version.
                String[] parts = RequestParser.splitRequestLine(requestLine);
                Method method = RequestParser.parseMethod(parts[0]);
                RequestParser.Version version = RequestParser.parseVersion(parts[2]);
                String target = RequestParser.toOriginForm(parts[1]);

                // If a HEAD request arrives and the handler or the parsing methods throw
                // an exception, the server must respond with an error without a body.
                // But if this line is placed after the request parsing, handleConnection
                // will jump straight to catch, skipping the lines below, and the HEAD
                // request error will be sent with a body.
                headRequest = method == Method.HEAD;
                Map<String, String> headers = RequestParser.readHeaders(in);
                RequestParser.rejectTransferEncoding(headers);
                byte[] body = RequestParser.readBody(in, headers);

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
}
