package jaxle;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

final class PercentEncoding {
    private PercentEncoding() {

    }

    static String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new HttpException(400, "invalid percent-encoding", e);
        }
    }
}
