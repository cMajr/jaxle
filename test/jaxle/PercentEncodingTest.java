package jaxle;

import static jaxle.PercentEncoding.decodePathSegment;
import static jaxle.PercentEncoding.decodeQueryComponent;
import static jaxle.TestRunner.assertEquals;
import static jaxle.TestRunner.assertStatus;
import static jaxle.TestRunner.test;

final class PercentEncodingTest {
    private PercentEncodingTest() {

    }

    static void run() {
        decodeQueryComponentTests();
        decodePathSegmentTests();
    }

    private static void decodeQueryComponentTests() {
        test("decodeQueryComponent keeps a string without escapes",
                () -> assertEquals("users", decodeQueryComponent("users")));
        test("decodeQueryComponent gives an empty string for an empty string",
                () -> assertEquals("", decodeQueryComponent("")));
        test("decodeQueryComponent decodes an ASCII escape",
                () -> assertEquals("a b", decodeQueryComponent("a%20b")));
        test("decodeQueryComponent accepts lowercase hex digits",
                () -> assertEquals("/", decodeQueryComponent("%2f")));
        test("decodeQueryComponent decodes a UTF-8 sequence",
                () -> assertEquals("café", decodeQueryComponent("caf%C3%A9")));
        test("decodeQueryComponent turns a plus into a space",
                () -> assertEquals("a b", decodeQueryComponent("a+b")));
        test("decodeQueryComponent decodes an escaped plus to a plus",
                () -> assertEquals("a+b", decodeQueryComponent("a%2Bb")));
        test("decodeQueryComponent decodes an escaped percent sign once",
                () -> assertEquals("%41", decodeQueryComponent("%2541")));
        test("decodeQueryComponent replaces invalid UTF-8 with U+FFFD",
                () -> assertEquals("\uFFFD", decodeQueryComponent("%FF")));
        test("decodeQueryComponent replaces a cut UTF-8 sequence with U+FFFD",
                () -> assertEquals("\uFFFD", decodeQueryComponent("%C3")));
        test("decodeQueryComponent rejects a lone percent sign",
                () -> assertStatus(400, () -> decodeQueryComponent("%")));
        test("decodeQueryComponent rejects an escape with one digit",
                () -> assertStatus(400, () -> decodeQueryComponent("a%2")));
        test("decodeQueryComponent rejects non-hex digits",
                () -> assertStatus(400, () -> decodeQueryComponent("%zz")));
        test("decodeQueryComponent rejects a sign inside an escape",
                () -> assertStatus(400, () -> decodeQueryComponent("%+1")));
    }

    private static void decodePathSegmentTests() {
        test("decodePathSegment keeps a plus",
                () -> assertEquals("a+b", decodePathSegment("a+b")));
        test("decodePathSegment decodes an escaped plus to a plus",
                () -> assertEquals("a+b", decodePathSegment("a%2Bb")));
        test("decodePathSegment decodes an escaped space",
                () -> assertEquals("a b", decodePathSegment("a%20b")));
        test("decodePathSegment rejects a bad escape",
                () -> assertStatus(400, () -> decodePathSegment("%zz")));
    }
}
