package jaxle;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

public record Request(
    Method method,
    String path,
    Map<String, String> headers,
    byte[] body,
    Map<String, String> params,
    Map<String, String> queryParams
) {
    public Request {
        headers = Map.copyOf(headers);
        body = body.clone();
        params = Map.copyOf(params);
        queryParams = Map.copyOf(queryParams);
    }

    /**
     * {@return a copy of the raw body bytes}
     *
     * <p>Each call returns a new array that can be changed without affecting
     * this request. A request without a body gives an empty array.
     */
    public byte[] body() {
        return body.clone();
    }

    /**
     * {@return the body decoded as UTF-8}
     *
     * <p>The charset parameter of the {@code Content-Type} header, if any,
     * is ignored. This method always replaces malformed-input sequences
     * with the replacement character {@code U+FFFD}, so it never fails,
     * even on a binary body. A request without a body gives an empty string.
     */
    public String text() {
        String contentType = headers.get("content-type");
        if (contentType == null) {
            return textUtf8();
        }

        String[] parts = contentType.split(";");

        String charset = "";
        for (String p : parts) {
            String part = p.strip();
            if (part.toLowerCase(Locale.ROOT).startsWith("charset=")) {
                charset = part.substring("charset=".length()).strip();
                break;
            }
        }
        
        if (charset.isEmpty()) {
            return textUtf8();
        }

        if (charset.length() > 1 && charset.startsWith("\"") && charset.endsWith("\"")) {
            charset = charset.substring(1, charset.length() - 1);
        }

        if (charset.isEmpty()) {
            return textUtf8();
        }

        try {
            return new String(body, charset);
        } catch (UnsupportedEncodingException e) {
            throw new HttpException(415, "unsupported charset " + charset);
        }
    }

    private String textUtf8() {
        return new String(body, StandardCharsets.UTF_8);
    }

    /**
     * {@return the value of the named path parameter}
     *
     * <p>The value is taken from the path as is and is not percent-decoded.
     *
     * @param name the name inside the braces of the route pattern
     * @throws IllegalArgumentException if the route has no such parameter
     */
    public String param(String name) {
        String value = params.get(name);
        if (value == null) {
            throw new IllegalArgumentException("no path parameter named " + name);
        }

        return value;
    }

    /**
     * {@return the named path parameter converted by the given function}
     *
     * <p>The converter receives the value as it appears in the path. A
     * converter that throws is taken as a rejection of the value, and the
     * request is answered with 400.
     *
     * @param name the parameter name, as written in the route
     * @param converter the function applied to the value
     * @param <T> the type the converter produces
     * @throws IllegalArgumentException if no path parameter has this name
     */
    public <T> T param(String name, Function<String, T> converter) {
        String value = param(name);
        try {
            return converter.apply(value);
        } catch (RuntimeException e) {
            throw new HttpException(400, "bad path parameter " + name);
        }
    }

    /**
     * {@return the value of the named query parameter}
     *
     * <p>A parameter missing from the query gives {@code null}, while a
     * parameter given without a value gives an empty string. A name that
     * repeats in the query keeps its first value. Names and values are
     * percent-decoded as UTF-8.
     *
     * @param name the parameter name, as it appears in the query
     */
    public String query(String name) {
        return queryParams.get(name);
    }
}
