package jaxle;

import static jaxle.TestRunner.assertEquals;
import static jaxle.TestRunner.assertThrows;
import static jaxle.TestRunner.test;

final class HttpExceptionTest {
    private HttpExceptionTest() {

    }

    static void run() {
        statusTests();
        messageTests();
        causeTests();
    }

    private static void statusTests() {
        test("HttpException accepts status 400",
                () -> assertEquals(400, new HttpException(400, "a").status()));
        test("HttpException accepts status 599",
                () -> assertEquals(599, new HttpException(599, "a").status()));
        test("HttpException rejects status 399",
                () -> assertThrows(IllegalArgumentException.class, () -> new HttpException(399, "a")));
        test("HttpException rejects status 600",
                () -> assertThrows(IllegalArgumentException.class, () -> new HttpException(600, "a")));
        test("HttpException rejects status 200",
                () -> assertThrows(IllegalArgumentException.class, () -> new HttpException(200, "a")));
        test("HttpException rejects a status out of range with a cause",
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new HttpException(302, "a", new RuntimeException())));
    }

    private static void messageTests() {
        test("HttpException keeps the message",
                () -> assertEquals("user not found", new HttpException(404, "user not found").getMessage()));
        test("HttpException rejects a null message",
                () -> assertThrows(NullPointerException.class, () -> new HttpException(404, null)));
        test("HttpException rejects a null message with a cause",
                () -> assertThrows(NullPointerException.class,
                        () -> new HttpException(404, null, new RuntimeException())));
    }

    private static void causeTests() {
        test("HttpException keeps the cause",
                () -> {
                    var cause = new NumberFormatException("x");
                    assertEquals(cause, new HttpException(400, "a", cause).getCause());
                });
        test("HttpException accepts a null cause",
                () -> assertEquals(null, new HttpException(400, "a", null).getCause()));
        test("HttpException has no cause without one",
                () -> assertEquals(null, new HttpException(400, "a").getCause()));
    }
}
