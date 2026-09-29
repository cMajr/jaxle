package jaxle;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

final class PercentEncoding {
    private PercentEncoding() {

    }

    static String decodePathSegment(String value) {
        return decode(value, false);
    }

    static String decodeQueryComponent(String value) {
        return decode(value, true);
    }

    private static String decode(String value, boolean plusAsSpace) {
        var buf = new ByteArrayOutputStream();
        int i = 0;
        while (i < value.length()) {
            char c = value.charAt(i);
            if (c == '%') {
                if (i + 2 >= value.length()) {
                    throw new HttpException(400, "incomplete percent-encoding");
                }

                char hi = value.charAt(i + 1);
                char lo = value.charAt(i + 2);

                if (!HexFormat.isHexDigit(hi) || !HexFormat.isHexDigit(lo)) {
                    throw new HttpException(400, "invalid percent-encoding");
                }

                int high = HexFormat.fromHexDigit(hi);
                int low = HexFormat.fromHexDigit(lo);
                buf.write(high * 16 + low);
                i += 3;
            } else if (c == '+' && plusAsSpace) {
                buf.write(' ');
                i++;
            } else {
                buf.write(c);
                i++;
            }
        }

        return buf.toString(StandardCharsets.UTF_8);
    }
}
