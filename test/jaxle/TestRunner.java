package jaxle;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

import static jaxle.RequestParser.readHeaders;

public class TestRunner {
    private static int passed = 0;
    private static int failed = 0;

    interface TestFn {
        void run() throws Exception;
    }

    public static void main(String[] args) {
        test("readHeaders lowercases names and strips values",
                () -> assertEquals(Map.of("host", "x", "content-length", "5"),
                        readHeaders(input("Host: x\r\nContent-Length:  5 \r\n\r\n"))));
        report();
    }

    static void test(String name, TestFn fn) {
        try {
            fn.run();
            passed++;
        } catch (Throwable e) {
            failed++;
            System.err.println("FAIL " + name + ": " + e.getMessage());
        }
    }

    static void assertEquals(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("expected " + expected + " but was " + actual);
        }
    }

    private static InputStream input(String raw) {
        return new ByteArrayInputStream(raw.getBytes(StandardCharsets.ISO_8859_1));
    }

    private static void report() {
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) {
            System.exit(1);
        }
    }
}
