package jaxle;

import static jaxle.HttpTokens.indexOfInvalid;
import static jaxle.TestRunner.assertEquals;
import static jaxle.TestRunner.test;

final class HttpTokensTest {
    private HttpTokensTest() {

    }

    static void run() {
        validTokenTests();
        invalidCharacterTests();
    }

    private static void validTokenTests() {
        test("indexOfInvalid gives -1 for letters and digits",
                () -> assertEquals(-1, indexOfInvalid("azAZ09")));
        test("indexOfInvalid gives -1 for every token symbol",
                () -> assertEquals(-1, indexOfInvalid("!#$%&'*+-.^_`|~")));
        test("indexOfInvalid gives -1 for a header name",
                () -> assertEquals(-1, indexOfInvalid("Content-Type")));
        test("indexOfInvalid gives -1 for an empty string",
                () -> assertEquals(-1, indexOfInvalid("")));
    }

    private static void invalidCharacterTests() {
        test("indexOfInvalid rejects every delimiter",
                () -> {
                    for (char c : "\"(),/:;<=>?@[\\]{}".toCharArray()) {
                        assertEquals(0, indexOfInvalid(String.valueOf(c)));
                    }
                });
        test("indexOfInvalid rejects a space",
                () -> assertEquals(1, indexOfInvalid("a b")));
        test("indexOfInvalid rejects a tab",
                () -> assertEquals(1, indexOfInvalid("a\tb")));
        test("indexOfInvalid rejects CR and LF",
                () -> {
                    assertEquals(1, indexOfInvalid("a\rb"));
                    assertEquals(1, indexOfInvalid("a\nb"));
                });
        test("indexOfInvalid rejects NUL",
                () -> assertEquals(0, indexOfInvalid("\u0000")));
        test("indexOfInvalid rejects DEL",
                () -> assertEquals(0, indexOfInvalid("\u007f")));
        test("indexOfInvalid rejects a Latin-1 letter",
                () -> assertEquals(3, indexOfInvalid("café")));
        test("indexOfInvalid rejects a character above Latin-1",
                () -> assertEquals(0, indexOfInvalid("Ж")));
        test("indexOfInvalid gives the index of the first invalid character",
                () -> assertEquals(2, indexOfInvalid("ab:c d")));
        test("indexOfInvalid finds an invalid last character",
                () -> assertEquals(3, indexOfInvalid("abc:")));
    }
}
