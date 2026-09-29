package jaxle;

import static jaxle.PercentEncoding.decode;
import static jaxle.TestRunner.assertEquals;
import static jaxle.TestRunner.assertStatus;
import static jaxle.TestRunner.test;

final class PercentEncodingTest {
    private PercentEncodingTest() {

    }

    static void run() {
        test("decode keeps a string without escapes",
                () -> assertEquals("users", decode("users")));
        test("decode gives an empty string for an empty string",
                () -> assertEquals("", decode("")));
        test("decode decodes an ASCII escape",
                () -> assertEquals("a b", decode("a%20b")));
        test("decode accepts lowercase hex digits",
                () -> assertEquals("/", decode("%2f")));
        test("decode decodes a UTF-8 sequence",
                () -> assertEquals("café", decode("caf%C3%A9")));
        test("decode turns a plus into a space",
                () -> assertEquals("a b", decode("a+b")));
        test("decode decodes an escaped plus to a plus",
                () -> assertEquals("a+b", decode("a%2Bb")));
        test("decode decodes an escaped percent sign once",
                () -> assertEquals("%41", decode("%2541")));
        test("decode replaces invalid UTF-8 with U+FFFD",
                () -> assertEquals("�", decode("%FF")));
        test("decode replaces a cut UTF-8 sequence with U+FFFD",
                () -> assertEquals("�", decode("%C3")));
        test("decode rejects a lone percent sign",
                () -> assertStatus(400, () -> decode("%")));
        test("decode rejects an escape with one digit",
                () -> assertStatus(400, () -> decode("a%2")));
        test("decode rejects non-hex digits",
                () -> assertStatus(400, () -> decode("%zz")));
        test("decode rejects a sign inside an escape",
                () -> assertStatus(400, () -> decode("%+1")));
    }
}
