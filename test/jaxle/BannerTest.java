package jaxle;

import static jaxle.TestRunner.assertEquals;
import static jaxle.TestRunner.test;

import java.util.List;

final class BannerTest {
    private BannerTest() {

    }

    static void run() {
        infoTests();
        routeTableTests();
        lineTests();
    }

    private static List<String> lines(List<Router.Route> routes) {
        return Banner.render("0.1.0", "21.0.4", routes, 8081, 12).lines().toList();
    }

    private static void infoTests() {
        test("render shows the version and the Java version",
                () -> assertEquals(true, lines(List.of()).contains("  v0.1.0 | zero dependencies | Java 21.0.4")));
        test("render shows the address and the startup time",
                () -> assertEquals(true, lines(List.of()).contains("  Listening on http://localhost:8081 (started in 12 ms)")));
        test("render ends with an empty line",
                () -> assertEquals("", lines(List.of()).getLast()));
    }

    private static void routeTableTests() {
        test("render leaves out the route table without routes",
                () -> {
                    var lines = lines(List.of());
                    int info = lines.indexOf("  v0.1.0 | zero dependencies | Java 21.0.4");
                    assertEquals("", lines.get(info + 1));
                    assertEquals("  Listening on http://localhost:8081 (started in 12 ms)", lines.get(info + 2));
                });
        test("render aligns paths after the longest method",
                () -> {
                    var lines = lines(List.of(
                            new Router.Route(Method.GET, "/"),
                            new Router.Route(Method.DELETE, "/users/{id}")));
                    assertEquals(true, lines.contains("  GET     /"));
                    assertEquals(true, lines.contains("  DELETE  /users/{id}"));
                });
        test("render lists routes in the given order",
                () -> {
                    var lines = lines(List.of(
                            new Router.Route(Method.POST, "/b"),
                            new Router.Route(Method.GET, "/a")));
                    assertEquals(true, lines.indexOf("  POST  /b") < lines.indexOf("  GET   /a"));
                });
    }

    private static void lineTests() {
        test("line fits the version, the address and the startup time into one line",
                () -> assertEquals("jaxle v0.1.0 on http://localhost:8081 (started in 12 ms)\n",
                        Banner.line("0.1.0", 8081, 12)));
    }
}
