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
     * {@return the body decoded with the charset from the content-type header}
     *
     * <p>Without a charset parameter, the body is decoded as UTF-8. Bytes
     * that are not valid in the charset are replaced with {@code U+FFFD}.
     *
     * @throws HttpException with status 415 if the charset is not supported
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
     * <p>The value is percent-decoded as UTF-8. Unlike
     * {@link #query(String) query}, it keeps a plus sign as is.
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
     * <p>The converter receives the same value that
     * {@link #param(String) param(name)} returns.
     *
     * @param name the name inside the braces of the route pattern
     * @param converter the function applied to the value
     * @param <T> the type the converter returns
     * @throws IllegalArgumentException if the route has no such parameter
     * @throws HttpException with status 400 if the converter throws
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
     * <p>A parameter missing from the query gives {@code null}, while one given
     * without a value gives an empty string. When a name repeats, its first
     * value is kept.
     *
     * <p>Names and values are percent-decoded as UTF-8. Unlike
     * {@link #param(String) param}, a plus sign becomes a space here, as in
     * HTML forms.
     *
     * @param name the decoded parameter name
     */
    public String query(String name) {
        return queryParams.get(name);
    }
}
