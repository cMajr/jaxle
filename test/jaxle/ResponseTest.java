package jaxle;

import static jaxle.TestRunner.assertEquals;
import static jaxle.TestRunner.assertThrows;
import static jaxle.TestRunner.test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

final class ResponseTest {
    private static final byte[] EMPTY = new byte[0];

    private ResponseTest() {

    }

    static void run() {
        statusTests();
        headerNameTests();
        headerValueTests();
        copyTests();
        equalityTests();
        factoryTests();
        withHeaderTests();
    }

    private static Response withHeaders(Map<String, String> headers) {
        return new Response(200, headers, EMPTY);
    }

    private static Response withHeader(String name, String value) {
        return withHeaders(Map.of(name, value));
    }

    private static String text(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void statusTests() {
        test("Response accepts status 200",
                () -> assertEquals(200, new Response(200, Map.of(), EMPTY).status()));
        test("Response accepts status 599",
                () -> assertEquals(599, new Response(599, Map.of(), EMPTY).status()));
        test("Response rejects status 199",
                () -> assertThrows(IllegalArgumentException.class, () -> new Response(199, Map.of(), EMPTY)));
        test("Response rejects status 600",
                () -> assertThrows(IllegalArgumentException.class, () -> new Response(600, Map.of(), EMPTY)));
        test("Response rejects status 100",
                () -> assertThrows(IllegalArgumentException.class, () -> new Response(100, Map.of(), EMPTY)));
        test("Response rejects a negative status",
                () -> assertThrows(IllegalArgumentException.class, () -> new Response(-200, Map.of(), EMPTY)));
    }

    private static void headerNameTests() {
        test("Response lowercases header names",
                () -> assertEquals(Map.of("x-request-id", "1"), withHeader("X-Request-ID", "1").headers()));
        test("Response accepts every token character in a name",
                () -> assertEquals(Map.of("a1!#$%&'*+-.^_`|~", "v"), withHeader("a1!#$%&'*+-.^_`|~", "v").headers()));
        test("Response rejects an empty name",
                () -> assertThrows(IllegalArgumentException.class, () -> withHeader("", "v")));
        test("Response rejects a space in a name",
                () -> assertThrows(IllegalArgumentException.class, () -> withHeader("x a", "v")));
        test("Response rejects a colon in a name",
                () -> assertThrows(IllegalArgumentException.class, () -> withHeader("x:a", "v")));
        test("Response rejects CRLF in a name",
                () -> assertThrows(IllegalArgumentException.class, () -> withHeader("x\r\nset-cookie", "v")));
        test("Response rejects a separator in a name",
                () -> assertThrows(IllegalArgumentException.class, () -> withHeader("x(a)", "v")));
        test("Response rejects a non-ASCII name",
                () -> assertThrows(IllegalArgumentException.class, () -> withHeader("x-café", "v")));
        test("Response rejects names that differ only in case",
                () -> {
                    var headers = new HashMap<String, String>();
                    headers.put("X-A", "1");
                    headers.put("x-a", "2");
                    assertThrows(IllegalArgumentException.class, () -> withHeaders(headers));
                });
        test("Response rejects content-length",
                () -> assertThrows(IllegalArgumentException.class, () -> withHeader("content-length", "5")));
        test("Response rejects connection",
                () -> assertThrows(IllegalArgumentException.class, () -> withHeader("connection", "keep-alive")));
        test("Response rejects transfer-encoding",
                () -> assertThrows(IllegalArgumentException.class, () -> withHeader("transfer-encoding", "chunked")));
        test("Response rejects a server header in another case",
                () -> assertThrows(IllegalArgumentException.class, () -> withHeader("Content-Length", "5")));
    }

    private static void headerValueTests() {
        test("Response keeps a header value as is",
                () -> assertEquals(Map.of("x-a", "Some Value"), withHeader("x-a", "Some Value").headers()));
        test("Response accepts an empty value",
                () -> assertEquals(Map.of("x-a", ""), withHeader("x-a", "").headers()));
        test("Response accepts spaces and tabs inside a value",
                () -> assertEquals(Map.of("x-a", "b \tc"), withHeader("x-a", "b \tc").headers()));
        test("Response rejects a leading space in a value",
                () -> assertThrows(IllegalArgumentException.class, () -> withHeader("x-a", " b")));
        test("Response rejects a trailing space in a value",
                () -> assertThrows(IllegalArgumentException.class, () -> withHeader("x-a", "b ")));
        test("Response rejects a leading tab in a value",
                () -> assertThrows(IllegalArgumentException.class, () -> withHeader("x-a", "\tb")));
        test("Response rejects a value of one space",
                () -> assertThrows(IllegalArgumentException.class, () -> withHeader("x-a", " ")));
        test("Response rejects CRLF in a value",
                () -> assertThrows(IllegalArgumentException.class,
                        () -> withHeader("x-a", "b\r\nset-cookie: c=d")));
        test("Response rejects a bare LF in a value",
                () -> assertThrows(IllegalArgumentException.class, () -> withHeader("x-a", "b\nc")));
        test("Response rejects a bare CR in a value",
                () -> assertThrows(IllegalArgumentException.class, () -> withHeader("x-a", "b\rc")));
        test("Response rejects NUL in a value",
                () -> assertThrows(IllegalArgumentException.class, () -> withHeader("x-a", "b\u0000c")));
        test("Response rejects DEL in a value",
                () -> assertThrows(IllegalArgumentException.class, () -> withHeader("x-a", "b\u007fc")));
        test("Response rejects a non-ASCII value",
                () -> assertThrows(IllegalArgumentException.class, () -> withHeader("x-a", "café")));
    }

    private static void copyTests() {
        test("Response keeps its own copy of the body",
                () -> {
                    byte[] body = {1, 2, 3};
                    var response = new Response(200, Map.of(), body);
                    body[0] = 9;
                    assertEquals(1, (int) response.body()[0]);
                });
        test("body gives a new copy on each call",
                () -> {
                    var response = new Response(200, Map.of(), new byte[] {1, 2, 3});
                    response.body()[0] = 9;
                    assertEquals(1, (int) response.body()[0]);
                });
        test("Response keeps its own copy of the headers",
                () -> {
                    var headers = new HashMap<String, String>();
                    headers.put("x-a", "1");
                    var response = withHeaders(headers);
                    headers.put("x-b", "2");
                    assertEquals(Map.of("x-a", "1"), response.headers());
                });
        test("headers gives an unmodifiable map",
                () -> assertThrows(UnsupportedOperationException.class,
                        () -> withHeader("x-a", "1").headers().put("x-b", "2")));
    }

    private static void equalityTests() {
        test("Response equals a response with the same content",
                () -> {
                    var a = new Response(200, Map.of("x-a", "1"), new byte[] {1, 2});
                    var b = new Response(200, Map.of("X-A", "1"), new byte[] {1, 2});
                    assertEquals(true, a.equals(b));
                    assertEquals(a.hashCode(), b.hashCode());
                });
        test("Response differs from a response with another body",
                () -> assertEquals(false, new Response(200, Map.of(), new byte[] {1})
                        .equals(new Response(200, Map.of(), new byte[] {2}))));
        test("Response differs from a response with another status",
                () -> assertEquals(false, Response.ok("a").equals(Response.text(201, "a"))));
        test("Response differs from a response with another header",
                () -> assertEquals(false, Response.ok("a").equals(Response.ok("a").withHeader("x-a", "1"))));
        test("Response does not equal null",
                () -> assertEquals(false, Response.ok("a").equals(null)));
        test("toString shows the body length",
                () -> assertEquals("Response[status=204, headers={}, body=0 bytes]", Response.noContent().toString()));
    }

    private static void factoryTests() {
        test("text sets the status, a plain-text type and a UTF-8 body",
                () -> {
                    var response = Response.text(418, "café");
                    assertEquals(418, response.status());
                    assertEquals(Map.of("content-type", "text/plain; charset=utf-8"), response.headers());
                    assertEquals(5, response.body().length);
                    assertEquals("café", text(response.body()));
                });
        test("text rejects a status out of range",
                () -> assertThrows(IllegalArgumentException.class, () -> Response.text(99, "a")));
        test("json sets the status, a JSON type and a UTF-8 body",
                () -> {
                    var response = Response.json(200, new Json("{\"name\":\"café\"}"));
                    assertEquals(200, response.status());
                    assertEquals(Map.of("content-type", "application/json"), response.headers());
                    assertEquals("{\"name\":\"café\"}", text(response.body()));
                });
        test("json rejects a status out of range",
                () -> assertThrows(IllegalArgumentException.class, () -> Response.json(600, new Json("{}"))));
        test("ok gives status 200",
                () -> assertEquals(Response.text(200, "a"), Response.ok("a")));
        test("badRequest gives status 400",
                () -> assertEquals(Response.text(400, "a"), Response.badRequest("a")));
        test("notFound gives status 404",
                () -> assertEquals(Response.text(404, "a"), Response.notFound("a")));
        test("conflict gives status 409",
                () -> assertEquals(Response.text(409, "a"), Response.conflict("a")));
        test("created gives status 201 with a location",
                () -> assertEquals(Response.text(201, "a").withHeader("location", "/users/1"),
                        Response.created("/users/1", "a")));
        test("created rejects CRLF in the location",
                () -> assertThrows(IllegalArgumentException.class,
                        () -> Response.created("/users/1\r\nset-cookie: a=b", "a")));
        test("noContent gives status 204 without headers or a body",
                () -> assertEquals(new Response(204, Map.of(), EMPTY), Response.noContent()));
    }

    private static void withHeaderTests() {
        test("withHeader adds a header",
                () -> assertEquals(Map.of("content-type", "text/plain; charset=utf-8", "x-a", "1"),
                        Response.ok("a").withHeader("x-a", "1").headers()));
        test("withHeader replaces a header in another case",
                () -> assertEquals(Map.of("content-type", "text/html"),
                        Response.ok("a").withHeader("Content-Type", "text/html").headers()));
        test("withHeader keeps the status and the body",
                () -> {
                    var response = Response.text(404, "a").withHeader("x-a", "1");
                    assertEquals(404, response.status());
                    assertEquals("a", text(response.body()));
                });
        test("withHeader leaves the original response unchanged",
                () -> {
                    var original = Response.ok("a");
                    original.withHeader("x-a", "1");
                    assertEquals(Map.of("content-type", "text/plain; charset=utf-8"), original.headers());
                });
        test("withHeader rejects an invalid name",
                () -> assertThrows(IllegalArgumentException.class, () -> Response.ok("a").withHeader("x a", "1")));
        test("withHeader rejects CRLF in a value",
                () -> assertThrows(IllegalArgumentException.class,
                        () -> Response.ok("a").withHeader("x-a", "1\r\nx-b: 2")));
        test("withHeader rejects a server header",
                () -> assertThrows(IllegalArgumentException.class,
                        () -> Response.ok("a").withHeader("Connection", "keep-alive")));
    }
}
