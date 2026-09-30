package jaxle;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

final class TestRunner {
    private static int passed = 0;
    private static int failed = 0;

    private TestRunner() {

    }

    interface TestFn {
        void run() throws Exception;
    }

    public static void main(String[] args) {
        RequestParserTest.run();
        PercentEncodingTest.run();
        QueryParserTest.run();
        RouterTest.run();
        ResponseTest.run();
        ResponseWriterTest.run();
        HttpTokensTest.run();
        HttpExceptionTest.run();
        RequestTest.run();
        ServerTest.run();
        report();
    }

    static void test(String name, TestFn fn) {
        try {
            fn.run();
            passed++;
        } catch (Throwable e) {
            failed++;
            if (e instanceof AssertionError) {
                System.err.println("FAIL " + name + ": " + e.getMessage());
            } else {
                System.err.println("FAIL " + name);
                e.printStackTrace();
            }
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
            if (e.status() != expected) {
                throw new AssertionError("expected status " + expected + " but was " + e.status());
            }
            return;
        }
        throw new AssertionError("expected HttpException with status " + expected + " but nothing was thrown");
    }

    static void assertThrows(Class<? extends Throwable> expected, TestFn fn) throws Exception {
        try {
            fn.run();
        } catch (Throwable e) {
            if (!expected.isInstance(e)) {
                throw new AssertionError("expected " + expected.getSimpleName() + " but was " + e);
            }
            return;
        }
        throw new AssertionError("expected " + expected.getSimpleName() + " but nothing was thrown");
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
