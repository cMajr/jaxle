package jaxle;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class TestRunner {
    private static int passed = 0;
    private static int failed = 0;

    interface TestFn {
        void run() throws Exception;
    }

    public static void main(String[] args) {
        RequestParserTest.run();
        PercentEncodingTest.run();
        QueryParserTest.run();
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

    static void assertStatus(int expected, TestFn fn) throws Exception {
        try {
            fn.run();
        } catch (HttpException e) {
            assertEquals(expected, e.status());
            return;
        }
        throw new AssertionError("expected HttpException with status " + expected + " but nothing was thrown");
    }

    static InputStream input(String raw) {
        return new ByteArrayInputStream(raw.getBytes(StandardCharsets.ISO_8859_1));
    }

    private static void report() {
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) {
            System.exit(1);
        }
    }
}
