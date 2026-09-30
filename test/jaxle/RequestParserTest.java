package jaxle;

import static jaxle.RequestParser.parseMethod;
import static jaxle.RequestParser.parseVersion;
import static jaxle.RequestParser.readBody;
import static jaxle.RequestParser.readHeaders;
import static jaxle.RequestParser.readLine;
import static jaxle.RequestParser.rejectTransferEncoding;
import static jaxle.RequestParser.splitRequestLine;
import static jaxle.RequestParser.toOriginForm;
import static jaxle.TestRunner.assertEquals;
import static jaxle.TestRunner.assertStatus;
import static jaxle.TestRunner.input;
import static jaxle.TestRunner.test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

final class RequestParserTest {
    private RequestParserTest() {

    }

    static void run() {
        splitRequestLineTests();
        parseVersionTests();
        toOriginFormTests();
        parseMethodTests();
        readLineTests();
        readHeadersTests();
        rejectTransferEncodingTests();
        readBodyTests();
    }

    private static void splitRequestLineTests() {
        test("splitRequestLine splits method, target and version",
                () -> assertEquals(List.of("GET", "/users/1", "HTTP/1.1"),
                        List.of(splitRequestLine("GET /users/1 HTTP/1.1"))));
        test("splitRequestLine rejects two parts",
                () -> assertStatus(400, () -> splitRequestLine("GET /")));
        test("splitRequestLine rejects four parts",
                () -> assertStatus(400, () -> splitRequestLine("GET / HTTP/1.1 x")));
        test("splitRequestLine rejects a double space",
                () -> assertStatus(400, () -> splitRequestLine("GET  / HTTP/1.1")));
        test("splitRequestLine rejects a trailing space",
                () -> assertStatus(400, () -> splitRequestLine("GET / HTTP/1.1 ")));
        test("splitRequestLine rejects a non-ASCII target",
                () -> assertStatus(400, () -> splitRequestLine("GET /café HTTP/1.1")));
        test("splitRequestLine rejects a control character in the target",
                () -> assertStatus(400, () -> splitRequestLine("GET /a\u0001b HTTP/1.1")));
        test("splitRequestLine rejects an empty line",
                () -> assertStatus(400, () -> splitRequestLine("")));
        test("splitRequestLine rejects an empty method",
                () -> assertStatus(400, () -> splitRequestLine(" / HTTP/1.1")));
        test("splitRequestLine rejects an empty target",
                () -> assertStatus(400, () -> splitRequestLine("GET  HTTP/1.1")));
        test("splitRequestLine rejects an empty version",
                () -> assertStatus(400, () -> splitRequestLine("GET / ")));
        test("splitRequestLine rejects tabs between parts",
                () -> assertStatus(400, () -> splitRequestLine("GET\t/\tHTTP/1.1")));
    }

    private static void parseVersionTests() {
        test("parseVersion reads HTTP/1.1",
                () -> assertEquals(new RequestParser.Version(1, 1), parseVersion("HTTP/1.1")));
        test("parseVersion reads HTTP/1.0",
                () -> assertEquals(new RequestParser.Version(1, 0), parseVersion("HTTP/1.0")));
        test("parseVersion reads a major version other than 1",
                () -> assertEquals(new RequestParser.Version(2, 0), parseVersion("HTTP/2.0")));
        test("parseVersion rejects a lowercase name",
                () -> assertStatus(400, () -> parseVersion("http/1.1")));
        test("parseVersion rejects a missing minor version",
                () -> assertStatus(400, () -> parseVersion("HTTP/1")));
        test("parseVersion rejects a two-digit minor version",
                () -> assertStatus(400, () -> parseVersion("HTTP/1.10")));
        test("parseVersion rejects a comma instead of a dot",
                () -> assertStatus(400, () -> parseVersion("HTTP/1,1")));
        test("parseVersion rejects a letter instead of a digit",
                () -> assertStatus(400, () -> parseVersion("HTTP/a.1")));
    }

    private static void toOriginFormTests() {
        test("toOriginForm keeps an origin-form target",
                () -> assertEquals("/users/1?x=1", toOriginForm("/users/1?x=1")));
        test("toOriginForm keeps an asterisk",
                () -> assertEquals("*", toOriginForm("*")));
        test("toOriginForm strips the scheme and authority",
                () -> assertEquals("/users/1?x=1", toOriginForm("http://example.org/users/1?x=1")));
        test("toOriginForm strips an https authority with a port",
                () -> assertEquals("/a", toOriginForm("https://example.org:8443/a")));
        test("toOriginForm ignores the case of the scheme",
                () -> assertEquals("/a", toOriginForm("HTTP://example.org/a")));
        test("toOriginForm gives a slash for an authority without a path",
                () -> assertEquals("/", toOriginForm("http://example.org")));
        test("toOriginForm puts a slash before a query without a path",
                () -> assertEquals("/?x=1", toOriginForm("http://example.org?x=1")));
        test("toOriginForm rejects user info",
                () -> assertStatus(400, () -> toOriginForm("http://user@example.org/a")));
        test("toOriginForm rejects an empty user info",
                () -> assertStatus(400, () -> toOriginForm("http://@/a")));
        test("toOriginForm rejects a port without a host",
                () -> assertStatus(400, () -> toOriginForm("http://:80/a")));
        test("toOriginForm accepts a host with a port",
                () -> assertEquals("/a", toOriginForm("http://example.org:80/a")));
        test("toOriginForm accepts an IPv6 host with a port",
                () -> assertEquals("/a", toOriginForm("http://[::1]:80/a")));
        test("toOriginForm keeps an at sign in the path",
                () -> assertEquals("/users/@alice", toOriginForm("http://example.org/users/@alice")));
        test("toOriginForm keeps an at sign in the query",
                () -> assertEquals("/?q=a@b", toOriginForm("http://example.org?q=a@b")));
        test("toOriginForm rejects a scheme with nothing after it",
                () -> assertStatus(400, () -> toOriginForm("http://")));
        test("toOriginForm rejects an empty authority before a path",
                () -> assertStatus(400, () -> toOriginForm("http:///a")));
        test("toOriginForm rejects an asterisk followed by other characters",
                () -> assertStatus(400, () -> toOriginForm("*x")));
        test("toOriginForm rejects an unknown scheme",
                () -> assertStatus(400, () -> toOriginForm("ftp://example.org/a")));
        test("toOriginForm rejects a relative path",
                () -> assertStatus(400, () -> toOriginForm("users/1")));
    }

    private static void parseMethodTests() {
        test("parseMethod reads GET",
                () -> assertEquals(Method.GET, parseMethod("GET")));
        test("parseMethod reads PATCH",
                () -> assertEquals(Method.PATCH, parseMethod("PATCH")));
        test("parseMethod gives null for a lowercase method",
                () -> assertEquals(null, parseMethod("get")));
        test("parseMethod gives null for an unsupported method",
                () -> assertEquals(null, parseMethod("CONNECT")));
    }

    private static void readLineTests() {
        test("readLine reads lines ending with CRLF one by one", () -> {
            InputStream in = input("first\r\nsecond\r\n");
            assertEquals("first", readLine(in, 414));
            assertEquals("second", readLine(in, 414));
            assertEquals(null, readLine(in, 414));
        });
        test("readLine accepts a bare LF",
                () -> assertEquals("abc", readLine(input("abc\n"), 414)));
        test("readLine gives an empty string for an empty line",
                () -> assertEquals("", readLine(input("\r\n"), 414)));
        test("readLine gives null for an empty stream",
                () -> assertEquals(null, readLine(input(""), 414)));
        test("readLine decodes bytes as ISO-8859-1",
                () -> assertEquals("café", readLine(input("café\r\n"), 414)));
        test("readLine rejects a line cut off by the end of the stream",
                () -> assertStatus(400, () -> readLine(input("abc"), 414)));
        test("readLine rejects a bare CR",
                () -> assertStatus(400, () -> readLine(input("a\rb\r\n"), 414)));
        test("readLine rejects a CR at the end of the stream",
                () -> assertStatus(400, () -> readLine(input("abc\r"), 414)));
        test("readLine rejects a CR before CRLF",
                () -> assertStatus(400, () -> readLine(input("abc\r\r\n"), 414)));
        test("readLine accepts a line of 8192 bytes",
                () -> assertEquals(8192, readLine(input("a".repeat(8192) + "\r\n"), 414).length()));
        test("readLine rejects a line of 8193 bytes",
                () -> assertStatus(414, () -> readLine(input("a".repeat(8193) + "\r\n"), 414)));
        test("readLine uses the given status for a long line",
                () -> assertStatus(431, () -> readLine(input("a".repeat(8193) + "\r\n"), 431)));
    }

    private static void readHeadersTests() {
        test("readHeaders lowercases names and strips values",
                () -> assertEquals(Map.of("host", "x", "content-length", "5"),
                        readHeaders(input("Host: x\r\nContent-Length:  5 \r\n\r\n"))));
        test("readHeaders strips tabs around a value",
                () -> assertEquals(Map.of("x-a", "b"), readHeaders(input("X-A:\tb\t\r\n\r\n"))));
        test("readHeaders gives an empty value for a value of spaces",
                () -> assertEquals(Map.of("x-a", ""), readHeaders(input("X-A:   \r\n\r\n"))));
        test("readHeaders keeps spaces inside a value",
                () -> assertEquals(Map.of("x-a", "b  c"), readHeaders(input("X-A: b  c\r\n\r\n"))));
        test("readHeaders accepts lines ending with a bare LF",
                () -> assertEquals(Map.of("host", "x", "x-a", "b"),
                        readHeaders(input("Host: x\nX-A: b\n\n"))));
        test("readHeaders accepts bytes above 0x7F in a value",
                () -> assertEquals(Map.of("x-a", "caf\u00e9"), readHeaders(input("X-A: caf\u00e9\r\n\r\n"))));
        test("readHeaders gives an empty map for no headers",
                () -> assertEquals(Map.of(), readHeaders(input("\r\n"))));
        test("readHeaders stops at the empty line and leaves the body", () -> {
            InputStream in = input("Host: x\r\n\r\nbody");
            readHeaders(in);
            assertEquals("body", new String(in.readAllBytes(), StandardCharsets.ISO_8859_1));
        });
        test("readHeaders splits at the first colon",
                () -> assertEquals(Map.of("host", "localhost:8081"),
                        readHeaders(input("Host: localhost:8081\r\n\r\n"))));
        test("readHeaders accepts an empty value",
                () -> assertEquals(Map.of("x-empty", ""), readHeaders(input("X-Empty:\r\n\r\n"))));
        test("readHeaders keeps a tab inside a value",
                () -> assertEquals(Map.of("x-a", "b\tc"), readHeaders(input("X-A: b\tc\r\n\r\n"))));
        test("readHeaders joins repeated headers with a comma",
                () -> assertEquals(Map.of("accept", "text/plain, text/html"),
                        readHeaders(input("Accept: text/plain\r\naccept: text/html\r\n\r\n"))));
        test("readHeaders joins three repeated headers",
                () -> assertEquals(Map.of("x-a", "1, 2, 3"),
                        readHeaders(input("X-A: 1\r\nX-A: 2\r\nX-A: 3\r\n\r\n"))));
        test("readHeaders rejects a duplicate host",
                () -> assertStatus(400, () -> readHeaders(input("Host: a\r\nHost: b\r\n\r\n"))));
        test("readHeaders rejects a duplicate content-length in another case",
                () -> assertStatus(400, () -> readHeaders(
                        input("Content-Length: 1\r\ncontent-length: 1\r\n\r\n"))));
        test("readHeaders rejects a line without a colon",
                () -> assertStatus(400, () -> readHeaders(input("Host x\r\n\r\n"))));
        test("readHeaders rejects a space before the colon",
                () -> assertStatus(400, () -> readHeaders(input("Host : x\r\n\r\n"))));
        test("readHeaders rejects an empty name",
                () -> assertStatus(400, () -> readHeaders(input(": x\r\n\r\n"))));
        test("readHeaders rejects a name with a character outside the token set",
                () -> assertStatus(400, () -> readHeaders(input("X(A: b\r\n\r\n"))));
        test("readHeaders rejects a tab inside a name",
                () -> assertStatus(400, () -> readHeaders(input("X\tA: b\r\n\r\n"))));
        test("readHeaders rejects an obsolete line folding",
                () -> assertStatus(400, () -> readHeaders(input("X-A: b\r\n c: d\r\n\r\n"))));
        test("readHeaders rejects a control character inside a value",
                () -> assertStatus(400, () -> readHeaders(input("X-A: b\u0001c\r\n\r\n"))));
        test("readHeaders rejects DEL inside a value",
                () -> assertStatus(400, () -> readHeaders(input("X-A: b\u007fc\r\n\r\n"))));
        test("readHeaders rejects a control character at the end of a value",
                () -> assertStatus(400, () -> readHeaders(input("X-A: b\u000b\r\n\r\n"))));
        test("readHeaders rejects a control character at the start of a value",
                () -> assertStatus(400, () -> readHeaders(input("X-A: \u000bb\r\n\r\n"))));
        test("readHeaders rejects NUL inside a value",
                () -> assertStatus(400, () -> readHeaders(input("X-A: b\u0000c\r\n\r\n"))));
        test("readHeaders rejects headers cut off before the empty line",
                () -> assertStatus(400, () -> readHeaders(input("Host: x\r\n"))));
        test("readHeaders accepts 100 headers",
                () -> assertEquals(100, readHeaders(input(headers(100) + "\r\n")).size()));
        test("readHeaders rejects 101 headers",
                () -> assertStatus(431, () -> readHeaders(input(headers(101) + "\r\n"))));
        test("readHeaders accepts header lines totaling 32768 bytes",
                () -> assertEquals(4, readHeaders(input(longHeaders(4) + "\r\n")).size()));
        test("readHeaders rejects header lines totaling more than 32768 bytes",
                () -> assertStatus(431, () -> readHeaders(input(longHeaders(4) + "b:c\r\n\r\n"))));
        test("readHeaders rejects a header line of 8193 bytes",
                () -> assertStatus(431, () -> readHeaders(
                        input("a:" + "v".repeat(8191) + "\r\n\r\n"))));
    }

    private static void rejectTransferEncodingTests() {
        test("rejectTransferEncoding accepts headers without transfer-encoding",
                () -> rejectTransferEncoding(Map.of("content-length", "5")));
        test("rejectTransferEncoding rejects transfer-encoding",
                () -> assertStatus(501, () -> rejectTransferEncoding(
                        Map.of("transfer-encoding", "chunked"))));
        test("rejectTransferEncoding rejects transfer-encoding with content-length",
                () -> assertStatus(400, () -> rejectTransferEncoding(
                        Map.of("transfer-encoding", "chunked", "content-length", "5"))));
    }

    private static void readBodyTests() {
        test("readBody gives an empty body without content-length",
                () -> assertEquals("", body(input("hello"), Map.of())));
        test("readBody reads exactly content-length bytes",
                () -> assertEquals("hello", body(input("hello world"), Map.of("content-length", "5"))));
        test("readBody reads an empty body for content-length 0",
                () -> assertEquals("", body(input("hello"), Map.of("content-length", "0"))));
        test("readBody accepts leading zeros",
                () -> assertEquals("hello", body(input("hello"), Map.of("content-length", "005"))));
        test("readBody rejects a body shorter than content-length",
                () -> assertStatus(400, () -> readBody(input("hi"), Map.of("content-length", "5"))));
        test("readBody rejects an empty content-length",
                () -> assertStatus(400, () -> readBody(input(""), Map.of("content-length", ""))));
        test("readBody rejects a negative content-length",
                () -> assertStatus(400, () -> readBody(input(""), Map.of("content-length", "-1"))));
        test("readBody rejects a content-length with a plus sign",
                () -> assertStatus(400, () -> readBody(input(""), Map.of("content-length", "+5"))));
        test("readBody rejects a content-length with letters",
                () -> assertStatus(400, () -> readBody(input(""), Map.of("content-length", "5a"))));
        test("readBody rejects a list of content-length values",
                () -> assertStatus(400, () -> readBody(input(""), Map.of("content-length", "5, 5"))));
        test("readBody accepts a body of 512000 bytes",
                () -> assertEquals(512000, readBody(input("a".repeat(512000)),
                        Map.of("content-length", "512000")).length));
        test("readBody rejects a body of 512001 bytes",
                () -> assertStatus(413, () -> readBody(input(""), Map.of("content-length", "512001"))));
        test("readBody rejects a content-length too large for an int",
                () -> assertStatus(413, () -> readBody(input(""), Map.of("content-length", "2147483648"))));
    }

    private static String headers(int count) {
        var sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append("x-").append(i).append(": v\r\n");
        }
        return sb.toString();
    }

    private static String longHeaders(int count) {
        var sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append("a").append(i).append(':').append("v".repeat(8189)).append("\r\n");
        }
        return sb.toString();
    }

    private static String body(InputStream in, Map<String, String> headers) throws Exception {
        return new String(readBody(in, headers), StandardCharsets.ISO_8859_1);
    }
}
