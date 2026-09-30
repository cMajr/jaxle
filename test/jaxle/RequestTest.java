package jaxle;

import static jaxle.TestRunner.assertEquals;
import static jaxle.TestRunner.assertStatus;
import static jaxle.TestRunner.assertThrows;
import static jaxle.TestRunner.test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

final class RequestTest {
    private static final byte[] EMPTY = new byte[0];

    private RequestTest() {

    }

    static void run() {
        headerTests();
        copyTests();
        textTests();
        paramTests();
        queryTests();
        equalityTests();
    }

    private static Request withHeaders(Map<String, String> headers) {
        return new Request(Method.GET, "/", headers, EMPTY, Map.of(), Map.of());
    }

    private static Request withBody(String contentType, byte[] body) {
        return new Request(Method.POST, "/", Map.of("content-type", contentType), body, Map.of(), Map.of());
    }

    private static Request withParams(Map<String, String> params) {
        return new Request(Method.GET, "/", Map.of(), EMPTY, params, Map.of());
    }

    private static void headerTests() {
        test("Request lowercases header names",
                () -> assertEquals(Map.of("content-type", "text/plain"),
                        withHeaders(Map.of("Content-Type", "text/plain")).headers()));
        test("Request keeps header values as is",
                () -> assertEquals(Map.of("x-a", "Some Value"), withHeaders(Map.of("X-A", "Some Value")).headers()));
        test("Request rejects names that differ only in case",
                () -> {
                    var headers = new HashMap<String, String>();
                    headers.put("Host", "a");
                    headers.put("host", "b");
                    assertThrows(IllegalArgumentException.class, () -> withHeaders(headers));
                });
    }

    private static void copyTests() {
        test("Request keeps its own copy of the body",
                () -> {
                    byte[] body = {1, 2, 3};
                    var request = new Request(Method.POST, "/", Map.of(), body, Map.of(), Map.of());
                    body[0] = 9;
                    assertEquals(1, (int) request.body()[0]);
                });
        test("body gives a new copy on each call",
                () -> {
                    var request = new Request(Method.POST, "/", Map.of(), new byte[] {1, 2, 3}, Map.of(), Map.of());
                    request.body()[0] = 9;
                    assertEquals(1, (int) request.body()[0]);
                });
        test("Request keeps its own copy of the headers",
                () -> {
                    var headers = new HashMap<String, String>();
                    headers.put("x-a", "1");
                    var request = withHeaders(headers);
                    headers.put("x-b", "2");
                    assertEquals(Map.of("x-a", "1"), request.headers());
                });
        test("Request keeps its own copy of the params",
                () -> {
                    var params = new HashMap<String, String>();
                    params.put("id", "1");
                    var request = withParams(params);
                    params.put("id", "2");
                    assertEquals("1", request.param("id"));
                });
        test("Request keeps its own copy of the query params",
                () -> {
                    var query = new HashMap<String, String>();
                    query.put("page", "1");
                    var request = new Request(Method.GET, "/", Map.of(), EMPTY, Map.of(), query);
                    query.put("page", "2");
                    assertEquals("1", request.query("page"));
                });
        test("headers gives an unmodifiable map",
                () -> assertThrows(UnsupportedOperationException.class,
                        () -> withHeaders(Map.of("x-a", "1")).headers().put("x-b", "2")));
    }

    private static void textTests() {
        byte[] cafeUtf8 = "café".getBytes(StandardCharsets.UTF_8);
        byte[] cafeLatin1 = "café".getBytes(StandardCharsets.ISO_8859_1);

        test("text decodes UTF-8 without a content-type",
                () -> assertEquals("café", new Request(Method.POST, "/", Map.of(), cafeUtf8, Map.of(), Map.of()).text()));
        test("text decodes UTF-8 without a charset",
                () -> assertEquals("café", withBody("text/plain", cafeUtf8).text()));
        test("text decodes the given charset",
                () -> assertEquals("café", withBody("text/plain; charset=ISO-8859-1", cafeLatin1).text()));
        test("text ignores the case of the charset parameter name",
                () -> assertEquals("café", withBody("text/plain; CHARSET=ISO-8859-1", cafeLatin1).text()));
        test("text accepts a quoted charset",
                () -> assertEquals("café", withBody("text/plain; charset=\"ISO-8859-1\"", cafeLatin1).text()));
        test("text finds the charset after other parameters",
                () -> assertEquals("café", withBody("text/plain; format=flowed; charset=ISO-8859-1", cafeLatin1).text()));
        test("text reads a content-type given in another case",
                () -> assertEquals("café", new Request(Method.POST, "/",
                        Map.of("Content-Type", "text/plain; charset=ISO-8859-1"), cafeLatin1, Map.of(), Map.of()).text()));
        test("text decodes UTF-8 for an empty charset",
                () -> assertEquals("café", withBody("text/plain; charset=", cafeUtf8).text()));
        test("text decodes UTF-8 for an empty quoted charset",
                () -> assertEquals("café", withBody("text/plain; charset=\"\"", cafeUtf8).text()));
        test("text replaces invalid UTF-8 with U+FFFD",
                () -> assertEquals("caf�", withBody("text/plain", cafeLatin1).text()));
        test("text gives an empty string for an empty body",
                () -> assertEquals("", withBody("text/plain", EMPTY).text()));
        test("text rejects an unknown charset",
                () -> assertStatus(415, () -> withBody("text/plain; charset=no-such-charset", cafeUtf8).text()));
    }

    private static void paramTests() {
        test("param gives the value",
                () -> assertEquals("42", withParams(Map.of("id", "42")).param("id")));
        test("param rejects an unknown name",
                () -> assertThrows(IllegalArgumentException.class, () -> withParams(Map.of("id", "42")).param("name")));
        test("param converts the value",
                () -> assertEquals(42, withParams(Map.of("id", "42")).param("id", Integer::parseInt)));
        test("param turns a converter failure into 400",
                () -> assertStatus(400, () -> withParams(Map.of("id", "abc")).param("id", Integer::parseInt)));
        test("param passes an HttpException from the converter unchanged",
                () -> assertStatus(404, () -> withParams(Map.of("id", "42")).param("id", value -> {
                    throw new HttpException(404, "not found");
                })));
        test("param with a converter rejects an unknown name",
                () -> assertThrows(IllegalArgumentException.class,
                        () -> withParams(Map.of()).param("id", Integer::parseInt)));
    }

    private static void queryTests() {
        test("query gives the value",
                () -> assertEquals("2", new Request(Method.GET, "/", Map.of(), EMPTY, Map.of(), Map.of("page", "2"))
                        .query("page")));
        test("query gives null for an unknown name",
                () -> assertEquals(null, new Request(Method.GET, "/", Map.of(), EMPTY, Map.of(), Map.of())
                        .query("page")));
    }

    private static void equalityTests() {
        test("Request equals a request with the same content",
                () -> {
                    var a = new Request(Method.POST, "/a", Map.of("x-a", "1"), new byte[] {1}, Map.of(), Map.of());
                    var b = new Request(Method.POST, "/a", Map.of("X-A", "1"), new byte[] {1}, Map.of(), Map.of());
                    assertEquals(true, a.equals(b));
                    assertEquals(a.hashCode(), b.hashCode());
                });
        test("Request differs from a request with another body",
                () -> assertEquals(false, new Request(Method.POST, "/", Map.of(), new byte[] {1}, Map.of(), Map.of())
                        .equals(new Request(Method.POST, "/", Map.of(), new byte[] {2}, Map.of(), Map.of()))));
        test("Request differs from a request with another method",
                () -> assertEquals(false, new Request(Method.GET, "/", Map.of(), EMPTY, Map.of(), Map.of())
                        .equals(new Request(Method.HEAD, "/", Map.of(), EMPTY, Map.of(), Map.of()))));
        test("Request does not equal null",
                () -> assertEquals(false, withHeaders(Map.of()).equals(null)));
        test("toString shows the body length",
                () -> assertEquals("Request[method=POST, path=/, headers={}, body=3 bytes, params={}, queryParams={}]",
                        new Request(Method.POST, "/", Map.of(), new byte[3], Map.of(), Map.of()).toString()));
    }
}
