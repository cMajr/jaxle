package jaxle;

import static jaxle.TestRunner.assertEquals;
import static jaxle.TestRunner.input;
import static jaxle.TestRunner.test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

final class ServerTest {
    private ServerTest() {

    }

    static void run() {
        dispatchTests();
        handlerErrorTests();
        headTests();
        optionsTests();
        requestLineTests();
        parseErrorTests();
    }

    private static String exchange(Consumer<Server> routes, InputStream in) throws Exception {
        try (var server = new Server(0)) {
            routes.accept(server);
            var out = new ByteArrayOutputStream();
            server.handleRequest(in, out);
            return out.toString(StandardCharsets.ISO_8859_1);
        }
    }

    private static String exchange(Consumer<Server> routes, String raw) throws Exception {
        return exchange(routes, input(raw));
    }

    private static String get(Consumer<Server> routes, String target) throws Exception {
        return exchange(routes, "GET " + target + " HTTP/1.1\r\nHost: x\r\n\r\n");
    }

    private static String statusLine(String response) {
        return response.substring(0, response.indexOf("\r\n"));
    }

    private static String body(String response) {
        return response.substring(response.indexOf("\r\n\r\n") + 4);
    }

    private static boolean hasHeader(String response, String header) {
        String head = response.substring(0, response.indexOf("\r\n\r\n") + 2);
        return head.contains("\r\n" + header + "\r\n");
    }

    private static void noRoutes(Server server) {

    }

    private static void dispatchTests() {
        test("handleRequest writes the response of the handler",
                () -> assertEquals("HTTP/1.1 200 OK\r\n"
                        + "content-type: text/plain; charset=utf-8\r\n"
                        + "content-length: 5\r\n"
                        + "connection: close\r\n"
                        + "\r\n"
                        + "hello", get(s -> s.get("/", r -> Response.ok("hello")), "/")));
        test("handleRequest passes path parameters to the handler",
                () -> assertEquals("42", body(get(s -> s.get("/users/{id}", r -> Response.ok(r.param("id"))),
                        "/users/42"))));
        test("handleRequest passes decoded query parameters to the handler",
                () -> assertEquals("a b", body(get(s -> s.get("/search", r -> Response.ok(r.query("q"))),
                        "/search?q=a+b"))));
        test("handleRequest passes the path without the query to the handler",
                () -> assertEquals("/search", body(get(s -> s.get("/search", r -> Response.ok(r.path())),
                        "/search?q=a"))));
        test("handleRequest passes lowercased headers to the handler",
                () -> assertEquals("abc", body(exchange(s -> s.get("/", r -> Response.ok(r.headers().get("x-token"))),
                        "GET / HTTP/1.1\r\nHost: x\r\nX-Token: abc\r\n\r\n"))));
        test("handleRequest passes the body to the handler",
                () -> assertEquals("alice", body(exchange(s -> s.post("/register", r -> Response.ok(r.text())),
                        "POST /register HTTP/1.1\r\nHost: x\r\nContent-Length: 5\r\n\r\nalice"))));
        test("handleRequest routes an absolute-form target",
                () -> assertEquals("1", body(get(s -> s.get("/users/{id}", r -> Response.ok(r.param("id"))),
                        "http://example.org/users/1"))));
        test("handleRequest routes a path with a trailing slash",
                () -> assertEquals("HTTP/1.1 200 OK", statusLine(get(s -> s.get("/users", r -> Response.ok("a")),
                        "/users/"))));
        test("handleRequest answers 404 for an unknown path",
                () -> {
                    String response = get(s -> s.get("/users", r -> Response.ok("a")), "/posts");
                    assertEquals("HTTP/1.1 404 Not Found", statusLine(response));
                    assertEquals("Not Found", body(response));
                });
        test("handleRequest answers 405 with the allowed methods",
                () -> {
                    String response = exchange(s -> s.get("/users", r -> Response.ok("a")),
                            "DELETE /users HTTP/1.1\r\nHost: x\r\n\r\n");
                    assertEquals("HTTP/1.1 405 Method Not Allowed", statusLine(response));
                    assertEquals(true, hasHeader(response, "allow: GET, HEAD, OPTIONS"));
                });
        test("handleRequest answers 404 for a bad query on an unknown path",
                () -> assertEquals("HTTP/1.1 404 Not Found", statusLine(get(ServerTest::noRoutes, "/a?q=%zz"))));
        test("handleRequest answers 405 for a bad query with an unsupported method",
                () -> assertEquals("HTTP/1.1 405 Method Not Allowed",
                        statusLine(exchange(s -> s.get("/a", r -> Response.ok("a")),
                                "POST /a?q=%zz HTTP/1.1\r\nHost: x\r\n\r\n"))));
        test("handleRequest answers 400 for a bad query on a route",
                () -> assertEquals("HTTP/1.1 400 Bad Request",
                        statusLine(get(s -> s.get("/a", r -> Response.ok("a")), "/a?q=%zz"))));
        test("handleRequest answers 400 for an encoded slash",
                () -> assertEquals("HTTP/1.1 400 Bad Request",
                        statusLine(get(s -> s.get("/{name}", r -> Response.ok("a")), "/a%2Fb"))));
    }

    private static void handlerErrorTests() {
        test("handleRequest sends the message of an HttpException below 500",
                () -> {
                    String response = get(s -> s.get("/", r -> {
                        throw new HttpException(404, "user 1 not found");
                    }), "/");
                    assertEquals("HTTP/1.1 404 Not Found", statusLine(response));
                    assertEquals("user 1 not found", body(response));
                });
        test("handleRequest hides the message of an HttpException of 500 and above",
                () -> {
                    String response = get(s -> s.get("/", r -> {
                        throw new HttpException(503, "database is down");
                    }), "/");
                    assertEquals("HTTP/1.1 503 Service Unavailable", statusLine(response));
                    assertEquals("Service Unavailable", body(response));
                });
        test("handleRequest answers 500 when the handler throws",
                () -> {
                    String response = get(s -> s.get("/", r -> {
                        throw new IllegalStateException("broken");
                    }), "/");
                    assertEquals("HTTP/1.1 500 Internal Server Error", statusLine(response));
                    assertEquals("Internal Server Error", body(response));
                });
        test("handleRequest answers 500 when the handler returns null",
                () -> assertEquals("HTTP/1.1 500 Internal Server Error",
                        statusLine(get(s -> s.get("/", r -> null), "/"))));
        test("handleRequest answers 400 when a path parameter does not convert",
                () -> assertEquals("HTTP/1.1 400 Bad Request",
                        statusLine(get(s -> s.get("/users/{id}", r -> Response.ok("" + r.param("id", Integer::parseInt))),
                                "/users/abc"))));
    }

    private static void headTests() {
        test("handleRequest serves HEAD with the GET handler without a body",
                () -> assertEquals("HTTP/1.1 200 OK\r\n"
                        + "content-type: text/plain; charset=utf-8\r\n"
                        + "content-length: 5\r\n"
                        + "connection: close\r\n"
                        + "\r\n", exchange(s -> s.get("/", r -> Response.ok("hello")),
                                "HEAD / HTTP/1.1\r\nHost: x\r\n\r\n")));
        test("handleRequest prefers a HEAD handler over the GET handler",
                () -> assertEquals("HTTP/1.1 201 Created",
                        statusLine(exchange(s -> {
                            s.get("/", r -> Response.ok("get"));
                            s.addRoute(Method.HEAD, "/", r -> Response.text(201, "head"));
                        }, "HEAD / HTTP/1.1\r\nHost: x\r\n\r\n"))));
        test("handleRequest sends no body for a HEAD error",
                () -> {
                    String response = exchange(ServerTest::noRoutes, "HEAD /a HTTP/1.1\r\nHost: x\r\n\r\n");
                    assertEquals("HTTP/1.1 404 Not Found", statusLine(response));
                    assertEquals("", body(response));
                });
        test("handleRequest sends no body for a HEAD request with a bad header",
                () -> {
                    String response = exchange(ServerTest::noRoutes, "HEAD / HTTP/1.1\r\nBad Header: x\r\n\r\n");
                    assertEquals("HTTP/1.1 400 Bad Request", statusLine(response));
                    assertEquals("", body(response));
                });
        test("handleRequest answers 405 to HEAD on a path without GET",
                () -> assertEquals("HTTP/1.1 405 Method Not Allowed",
                        statusLine(exchange(s -> s.post("/", r -> Response.ok("a")),
                                "HEAD / HTTP/1.1\r\nHost: x\r\n\r\n"))));
    }

    private static void optionsTests() {
        test("handleRequest answers OPTIONS with the allowed methods",
                () -> {
                    String response = exchange(s -> {
                        s.get("/users", r -> Response.ok("a"));
                        s.post("/users", r -> Response.ok("a"));
                    }, "OPTIONS /users HTTP/1.1\r\nHost: x\r\n\r\n");
                    assertEquals("HTTP/1.1 204 No Content", statusLine(response));
                    assertEquals(true, hasHeader(response, "allow: GET, HEAD, POST, OPTIONS"));
                });
        test("handleRequest prefers an OPTIONS handler",
                () -> assertEquals("custom", body(exchange(s -> s.addRoute(Method.OPTIONS, "/", r -> Response.ok("custom")),
                        "OPTIONS / HTTP/1.1\r\nHost: x\r\n\r\n"))));
        test("handleRequest answers 404 to OPTIONS on an unknown path",
                () -> assertEquals("HTTP/1.1 404 Not Found",
                        statusLine(exchange(ServerTest::noRoutes, "OPTIONS /a HTTP/1.1\r\nHost: x\r\n\r\n"))));
        test("handleRequest answers OPTIONS * with every method",
                () -> {
                    String response = exchange(ServerTest::noRoutes, "OPTIONS * HTTP/1.1\r\nHost: x\r\n\r\n");
                    assertEquals("HTTP/1.1 204 No Content", statusLine(response));
                    assertEquals(true, hasHeader(response, "allow: GET, HEAD, POST, PUT, DELETE, OPTIONS, PATCH"));
                });
        test("handleRequest rejects an asterisk for other methods",
                () -> assertEquals("HTTP/1.1 400 Bad Request", statusLine(get(ServerTest::noRoutes, "*"))));
    }

    private static void requestLineTests() {
        test("handleRequest writes nothing for an empty connection",
                () -> assertEquals("", exchange(ServerTest::noRoutes, "")));
        test("handleRequest answers 501 for an unknown method",
                () -> assertEquals("HTTP/1.1 501 Not Implemented",
                        statusLine(exchange(ServerTest::noRoutes, "BREW / HTTP/1.1\r\nHost: x\r\n\r\n"))));
        test("handleRequest answers 505 for HTTP/2.0",
                () -> assertEquals("HTTP/1.1 505 HTTP Version Not Supported",
                        statusLine(exchange(ServerTest::noRoutes, "GET / HTTP/2.0\r\nHost: x\r\n\r\n"))));
        test("handleRequest rejects HTTP/1.1 without a host",
                () -> {
                    String response = exchange(s -> s.get("/", r -> Response.ok("a")), "GET / HTTP/1.1\r\n\r\n");
                    assertEquals("HTTP/1.1 400 Bad Request", statusLine(response));
                    assertEquals("missing host header", body(response));
                });
        test("handleRequest accepts HTTP/1.0 without a host",
                () -> assertEquals("HTTP/1.1 200 OK",
                        statusLine(exchange(s -> s.get("/", r -> Response.ok("a")), "GET / HTTP/1.0\r\n\r\n"))));
        test("handleRequest sends the parser message for a malformed request line",
                () -> {
                    String response = exchange(ServerTest::noRoutes, "GET /\r\n\r\n");
                    assertEquals("HTTP/1.1 400 Bad Request", statusLine(response));
                    assertEquals("malformed request line", body(response));
                });
        test("handleRequest answers 414 for a long request line",
                () -> assertEquals("HTTP/1.1 414 URI Too Long",
                        statusLine(exchange(ServerTest::noRoutes, "GET /" + "a".repeat(8192) + " HTTP/1.1\r\n\r\n"))));
    }

    private static void parseErrorTests() {
        test("handleRequest answers 431 for too many headers",
                () -> {
                    var raw = new StringBuilder("GET / HTTP/1.1\r\n");
                    for (int i = 0; i < 101; i++) {
                        raw.append("x-").append(i).append(": v\r\n");
                    }
                    raw.append("\r\n");
                    assertEquals("HTTP/1.1 431 Request Header Fields Too Large",
                            statusLine(exchange(ServerTest::noRoutes, raw.toString())));
                });
        test("handleRequest answers 413 for a large body",
                () -> assertEquals("HTTP/1.1 413 Content Too Large",
                        statusLine(exchange(ServerTest::noRoutes,
                                "POST / HTTP/1.1\r\nHost: x\r\nContent-Length: 512001\r\n\r\n"))));
        test("handleRequest answers 400 for a body shorter than content-length",
                () -> assertEquals("HTTP/1.1 400 Bad Request",
                        statusLine(exchange(ServerTest::noRoutes,
                                "POST / HTTP/1.1\r\nHost: x\r\nContent-Length: 5\r\n\r\nab"))));
        test("handleRequest hides the message of a parser error of 500 and above",
                () -> {
                    String response = exchange(ServerTest::noRoutes,
                            "POST / HTTP/1.1\r\nHost: x\r\nTransfer-Encoding: chunked\r\n\r\n");
                    assertEquals("HTTP/1.1 501 Not Implemented", statusLine(response));
                    assertEquals("Not Implemented", body(response));
                });
        test("handleRequest answers 408 when reading times out",
                () -> {
                    InputStream timingOut = new InputStream() {
                        @Override
                        public int read() throws SocketTimeoutException {
                            throw new SocketTimeoutException("read timed out");
                        }
                    };
                    assertEquals("HTTP/1.1 408 Request Timeout", statusLine(exchange(ServerTest::noRoutes, timingOut)));
                });
    }
}
