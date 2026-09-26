package jaxle;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

final class ResponseWriter {
    private ResponseWriter() {

    }

    static void write(OutputStream out, Response response, boolean headRequest) throws IOException {
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

    static String reasonPhrase(int status) {
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
}
