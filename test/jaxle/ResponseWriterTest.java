package jaxle;

import static jaxle.ResponseWriter.reasonPhrase;
import static jaxle.TestRunner.assertEquals;
import static jaxle.TestRunner.test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;

final class ResponseWriterTest {
    private ResponseWriterTest() {

    }

    static void run() {
        writeTests();
        bodylessStatusTests();
        headTests();
        reasonPhraseTests();
    }

    private static String written(Response response, boolean headRequest) throws Exception {
        var out = new ByteArrayOutputStream();
        ResponseWriter.write(out, response, headRequest);
        return out.toString(StandardCharsets.ISO_8859_1);
    }

    private static Response plain(int status, String body) {
        return new Response(status, Map.of(), body.getBytes(StandardCharsets.UTF_8));
    }

    private static void writeTests() {
        test("write sends the status line, content-length, connection and the body",
                () -> assertEquals("HTTP/1.1 200 OK\r\n"
                        + "content-length: 5\r\n"
                        + "connection: close\r\n"
                        + "\r\n"
                        + "hello", written(plain(200, "hello"), false)));
        test("write sends the user headers before content-length",
                () -> assertEquals("HTTP/1.1 201 Created\r\n"
                        + "location: /users/1\r\n"
                        + "content-length: 0\r\n"
                        + "connection: close\r\n"
                        + "\r\n", written(plain(201, "").withHeader("Location", "/users/1"), false)));
        test("write sends every user header",
                () -> {
                    var response = plain(200, "").withHeader("x-a", "1").withHeader("x-b", "2");
                    String head = written(response, false);
                    assertEquals(true, head.contains("\r\nx-a: 1\r\n"));
                    assertEquals(true, head.contains("\r\nx-b: 2\r\n"));
                });
        test("write sends content-length 0 for an empty body",
                () -> assertEquals("HTTP/1.1 200 OK\r\n"
                        + "content-length: 0\r\n"
                        + "connection: close\r\n"
                        + "\r\n", written(plain(200, ""), false)));
        test("write counts content-length in bytes",
                () -> assertEquals(true, written(plain(200, "café"), false).contains("\r\ncontent-length: 5\r\n")));
        test("write sends the body bytes unchanged",
                () -> {
                    var out = new ByteArrayOutputStream();
                    ResponseWriter.write(out, plain(200, "café"), false);
                    byte[] bytes = out.toByteArray();
                    byte[] expected = "café".getBytes(StandardCharsets.UTF_8);
                    byte[] tail = Arrays.copyOfRange(bytes, bytes.length - expected.length, bytes.length);
                    assertEquals(true, Arrays.equals(expected, tail));
                });
        test("write sends an empty reason phrase for an unknown status",
                () -> assertEquals(true, written(plain(418, ""), false).startsWith("HTTP/1.1 418 \r\n")));
    }

    private static void bodylessStatusTests() {
        test("write sends no content-length or body for 204",
                () -> assertEquals("HTTP/1.1 204 No Content\r\n"
                        + "connection: close\r\n"
                        + "\r\n", written(Response.noContent(), false)));
        test("write drops the body of a 204 response",
                () -> assertEquals("HTTP/1.1 204 No Content\r\n"
                        + "connection: close\r\n"
                        + "\r\n", written(plain(204, "hello"), false)));
        test("write sends no content-length or body for 304",
                () -> assertEquals("HTTP/1.1 304 Not Modified\r\n"
                        + "connection: close\r\n"
                        + "\r\n", written(plain(304, "hello"), false)));
        test("write keeps user headers on 204",
                () -> assertEquals("HTTP/1.1 204 No Content\r\n"
                        + "allow: GET\r\n"
                        + "connection: close\r\n"
                        + "\r\n", written(Response.noContent().withHeader("allow", "GET"), false)));
    }

    private static void headTests() {
        test("write keeps content-length and drops the body for HEAD",
                () -> assertEquals("HTTP/1.1 200 OK\r\n"
                        + "content-length: 5\r\n"
                        + "connection: close\r\n"
                        + "\r\n", written(plain(200, "hello"), true)));
        test("write drops the body of an error for HEAD",
                () -> assertEquals("HTTP/1.1 404 Not Found\r\n"
                        + "content-length: 9\r\n"
                        + "connection: close\r\n"
                        + "\r\n", written(plain(404, "not found"), true)));
        test("write sends no content-length for HEAD with 204",
                () -> assertEquals("HTTP/1.1 204 No Content\r\n"
                        + "connection: close\r\n"
                        + "\r\n", written(Response.noContent(), true)));
    }

    private static void reasonPhraseTests() {
        test("reasonPhrase gives OK for 200",
                () -> assertEquals("OK", reasonPhrase(200)));
        test("reasonPhrase gives Not Found for 404",
                () -> assertEquals("Not Found", reasonPhrase(404)));
        test("reasonPhrase gives the RFC 9110 name for 413",
                () -> assertEquals("Content Too Large", reasonPhrase(413)));
        test("reasonPhrase gives the RFC 9110 name for 422",
                () -> assertEquals("Unprocessable Content", reasonPhrase(422)));
        test("reasonPhrase gives Request Header Fields Too Large for 431",
                () -> assertEquals("Request Header Fields Too Large", reasonPhrase(431)));
        test("reasonPhrase gives Internal Server Error for 500",
                () -> assertEquals("Internal Server Error", reasonPhrase(500)));
        test("reasonPhrase gives an empty string for an unknown status",
                () -> assertEquals("", reasonPhrase(418)));
    }
}
