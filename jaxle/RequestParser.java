package jaxle;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

final class RequestParser {
    private static final int MAX_BODY_BYTES = 500 * 1024;
    private static final int MAX_LINE_BYTES = 8 * 1024;
    private static final int MAX_HEADER_BYTES = 32 * 1024;
    private static final int MAX_HEADERS = 100;

    record Version(int major, int minor) {}

    private RequestParser() {

    }

    static String[] splitRequestLine(String requestLine) {
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

    static Version parseVersion(String version) {
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
    static String toOriginForm(String target) {
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

    static Method parseMethod(String name) {
        try {
            return Method.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static String readLine(InputStream in, int tooLongStatus) throws IOException {
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

    static Map<String, String> readHeaders(InputStream in) throws IOException {
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

            if (name.isEmpty() || HttpTokens.indexOfInvalid(name) != -1) {
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

    static void rejectTransferEncoding(Map<String, String> headers) {
        if (headers.containsKey("content-length") && headers.containsKey("transfer-encoding")) {
            throw new HttpException(400, "both content-length and transfer-encoding");
        }

        if (headers.containsKey("transfer-encoding")) {
            throw new HttpException(501, "transfer-encoding not supported");
        }
    }

    static byte[] readBody(InputStream in, Map<String, String> headers) throws IOException {
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

        if (value.isEmpty()) {
            throw new HttpException(400, "invalid content-length");
        }

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!isDigit(c)) {
                throw new HttpException(400, "invalid content-length");
            }
        }

        try {
            length = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new HttpException(413, "body exceeds " + MAX_BODY_BYTES + " bytes", e);
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
