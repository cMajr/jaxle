package jaxle;

import static jaxle.QueryParser.parseQuery;
import static jaxle.QueryParser.rawQuery;
import static jaxle.QueryParser.stripQuery;
import static jaxle.TestRunner.assertEquals;
import static jaxle.TestRunner.assertStatus;
import static jaxle.TestRunner.test;

import java.util.Map;

final class QueryParserTest {
    private QueryParserTest() {

    }

    static void run() {
        stripQueryTests();
        rawQueryTests();
        parseQueryTests();
    }

    private static void stripQueryTests() {
        test("stripQuery keeps a path without a query",
                () -> assertEquals("/users/1", stripQuery("/users/1")));
        test("stripQuery drops the query",
                () -> assertEquals("/users", stripQuery("/users?page=2")));
        test("stripQuery drops an empty query",
                () -> assertEquals("/users", stripQuery("/users?")));
        test("stripQuery cuts at the first question mark",
                () -> assertEquals("/a", stripQuery("/a?b?c")));
    }

    private static void rawQueryTests() {
        test("rawQuery gives an empty string without a query",
                () -> assertEquals("", rawQuery("/users/1")));
        test("rawQuery gives the text after the question mark",
                () -> assertEquals("page=2&size=10", rawQuery("/users?page=2&size=10")));
        test("rawQuery gives an empty string for an empty query",
                () -> assertEquals("", rawQuery("/users?")));
        test("rawQuery keeps later question marks",
                () -> assertEquals("b?c", rawQuery("/a?b?c")));
        test("rawQuery keeps escapes undecoded",
                () -> assertEquals("q=a%20b", rawQuery("/search?q=a%20b")));
    }

    private static void parseQueryTests() {
        test("parseQuery gives an empty map for an empty query",
                () -> assertEquals(Map.of(), parseQuery("")));
        test("parseQuery reads several pairs",
                () -> assertEquals(Map.of("page", "2", "size", "10"), parseQuery("page=2&size=10")));
        test("parseQuery gives an empty value for a name without an equals sign",
                () -> assertEquals(Map.of("debug", ""), parseQuery("debug")));
        test("parseQuery gives an empty value for a name followed by an equals sign",
                () -> assertEquals(Map.of("debug", ""), parseQuery("debug=")));
        test("parseQuery splits a pair at the first equals sign",
                () -> assertEquals(Map.of("expr", "a=b"), parseQuery("expr=a=b")));
        test("parseQuery skips empty pairs",
                () -> assertEquals(Map.of("a", "1", "b", "2"), parseQuery("&a=1&&b=2&")));
        test("parseQuery keeps the first value of a repeated name",
                () -> assertEquals(Map.of("a", "1"), parseQuery("a=1&a=2")));
        test("parseQuery accepts an empty name",
                () -> assertEquals(Map.of("", "x"), parseQuery("=x")));
        test("parseQuery turns a plus into a space",
                () -> assertEquals(Map.of("q", "a b"), parseQuery("q=a+b")));
        test("parseQuery decodes an escaped plus to a plus",
                () -> assertEquals(Map.of("q", "a+b"), parseQuery("q=a%2Bb")));
        test("parseQuery decodes names and values",
                () -> assertEquals(Map.of("a=b", "c&d"), parseQuery("a%3Db=c%26d")));
        test("parseQuery decodes UTF-8",
                () -> assertEquals(Map.of("q", "café"), parseQuery("q=caf%C3%A9")));
        test("parseQuery rejects a bad escape in a value",
                () -> assertStatus(400, () -> parseQuery("q=%zz")));
        test("parseQuery rejects a bad escape in a name",
                () -> assertStatus(400, () -> parseQuery("%zz=1")));
    }
}
