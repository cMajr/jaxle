package jaxle;

import static jaxle.TestRunner.assertEquals;
import static jaxle.TestRunner.assertStatus;
import static jaxle.TestRunner.test;

import java.util.List;
import java.util.Map;
import java.util.Set;

final class RouterTest {
    private static final Handler FIRST = request -> Response.ok("first");
    private static final Handler SECOND = request -> Response.ok("second");

    private RouterTest() {

    }

    static void run() {
        staticRouteTests();
        patternRouteTests();
        precedenceTests();
        decodingTests();
        allowedMethodsTests();
        routeListTests();
    }

    private static Router router(Method method, String path, Handler handler) {
        var router = new Router();
        router.add(method, path, handler);
        return router;
    }

    private static Handler getHandler(Router router, String path) {
        return router.findRoute(path).handlers().get(Method.GET);
    }

    private static String allowed(Method... methods) {
        var router = new Router();
        for (Method method : methods) {
            router.add(method, "/r", FIRST);
        }
        return Router.allowedMethods(router.findRoute("/r"));
    }

    private static void staticRouteTests() {
        test("findRoute finds a static route",
                () -> {
                    var match = router(Method.GET, "/users", FIRST).findRoute("/users");
                    assertEquals(FIRST, match.handlers().get(Method.GET));
                    assertEquals(Map.of(), match.params());
                });
        test("findRoute finds the root path",
                () -> assertEquals(FIRST, getHandler(router(Method.GET, "/", FIRST), "/")));
        test("findRoute finds the root path with a trailing slash",
                () -> assertEquals(FIRST, getHandler(router(Method.GET, "/", FIRST), "//")));
        test("findRoute gives null for an unknown path",
                () -> assertEquals(null, router(Method.GET, "/users", FIRST).findRoute("/posts")));
        test("findRoute finds a static route with an empty segment",
                () -> assertEquals(FIRST, getHandler(router(Method.GET, "/users//posts", FIRST), "/users//posts")));
        test("findRoute gives null for a second trailing slash",
                () -> assertEquals(null, router(Method.GET, "/users", FIRST).findRoute("/users//")));
        test("findRoute gives null for a longer path",
                () -> assertEquals(null, router(Method.GET, "/users", FIRST).findRoute("/users/1")));
        test("findRoute compares paths case-sensitively",
                () -> assertEquals(null, router(Method.GET, "/users", FIRST).findRoute("/Users")));
        test("findRoute ignores a trailing slash in the request",
                () -> assertEquals(FIRST, getHandler(router(Method.GET, "/users", FIRST), "/users/")));
        test("findRoute ignores a trailing slash in the route",
                () -> assertEquals(FIRST, getHandler(router(Method.GET, "/users/", FIRST), "/users")));
        test("findRoute keeps every method of a path in one match",
                () -> {
                    var router = router(Method.GET, "/users", FIRST);
                    router.add(Method.POST, "/users", SECOND);
                    var handlers = router.findRoute("/users").handlers();
                    assertEquals(Set.of(Method.GET, Method.POST), handlers.keySet());
                    assertEquals(FIRST, handlers.get(Method.GET));
                    assertEquals(SECOND, handlers.get(Method.POST));
                });
        test("add replaces the handler of the same method and path",
                () -> {
                    var router = router(Method.GET, "/users", FIRST);
                    router.add(Method.GET, "/users", SECOND);
                    assertEquals(SECOND, getHandler(router, "/users"));
                });
        test("add treats a path with and without a trailing slash as one route",
                () -> {
                    var router = router(Method.GET, "/users", FIRST);
                    router.add(Method.GET, "/users/", SECOND);
                    assertEquals(SECOND, getHandler(router, "/users"));
                });
    }

    private static void patternRouteTests() {
        test("findRoute captures a parameter",
                () -> {
                    var match = router(Method.GET, "/users/{id}", FIRST).findRoute("/users/42");
                    assertEquals(FIRST, match.handlers().get(Method.GET));
                    assertEquals(Map.of("id", "42"), match.params());
                });
        test("findRoute captures several parameters",
                () -> assertEquals(Map.of("userId", "1", "postId", "2"),
                        router(Method.GET, "/users/{userId}/posts/{postId}", FIRST)
                                .findRoute("/users/1/posts/2").params()));
        test("findRoute captures a parameter at the root",
                () -> assertEquals(Map.of("name", "about"),
                        router(Method.GET, "/{name}", FIRST).findRoute("/about").params()));
        test("findRoute captures a parameter before a trailing slash",
                () -> assertEquals(Map.of("id", "42"),
                        router(Method.GET, "/users/{id}", FIRST).findRoute("/users/42/").params()));
        test("findRoute gives null when a pattern has more segments",
                () -> assertEquals(null, router(Method.GET, "/users/{id}", FIRST).findRoute("/users")));
        test("findRoute gives null when a pattern has fewer segments",
                () -> assertEquals(null, router(Method.GET, "/users/{id}", FIRST).findRoute("/users/1/posts")));
        test("findRoute gives null when a literal segment differs",
                () -> assertEquals(null, router(Method.GET, "/users/{id}/posts", FIRST).findRoute("/users/1/likes")));
        test("findRoute gives null for a second trailing slash after a parameter",
                () -> assertEquals(null, router(Method.GET, "/users/{id}", FIRST).findRoute("/users/1//")));
        test("findRoute gives null for an empty parameter",
                () -> assertEquals(null, router(Method.GET, "/users/{id}/posts", FIRST).findRoute("/users//posts")));
        test("findRoute gives null for an empty last parameter",
                () -> assertEquals(null, router(Method.GET, "/users/{id}/{tab}", FIRST).findRoute("/users/1//")));
        test("findRoute gives null for an empty parameter at the root",
                () -> assertEquals(null, router(Method.GET, "/{name}", FIRST).findRoute("/")));
        test("findRoute matches an empty literal segment in a pattern",
                () -> assertEquals(Map.of("id", "1"),
                        router(Method.GET, "/users/{id}//posts", FIRST).findRoute("/users/1//posts").params()));
        test("findRoute treats a partly braced segment as a literal",
                () -> {
                    var router = router(Method.GET, "/files/{name}.txt", FIRST);
                    assertEquals(null, router.findRoute("/files/a.txt"));
                    assertEquals(Map.of(), router.findRoute("/files/{name}.txt").params());
                });
    }

    private static void precedenceTests() {
        test("findRoute prefers a static route over a pattern",
                () -> {
                    var router = router(Method.GET, "/users/{id}", FIRST);
                    router.add(Method.GET, "/users/me", SECOND);
                    var match = router.findRoute("/users/me");
                    assertEquals(SECOND, match.handlers().get(Method.GET));
                    assertEquals(Map.of(), match.params());
                });
        test("findRoute prefers a static route over a pattern even without the method",
                () -> {
                    var router = router(Method.GET, "/users/{id}", FIRST);
                    router.add(Method.POST, "/users/me", SECOND);
                    assertEquals(Set.of(Method.POST), router.findRoute("/users/me").handlers().keySet());
                });
        test("findRoute prefers the pattern registered first",
                () -> {
                    var router = router(Method.GET, "/users/{id}", FIRST);
                    router.add(Method.GET, "/{section}/{name}", SECOND);
                    var match = router.findRoute("/users/1");
                    assertEquals(FIRST, match.handlers().get(Method.GET));
                    assertEquals(Map.of("id", "1"), match.params());
                });
        test("findRoute falls through to a later pattern",
                () -> {
                    var router = router(Method.GET, "/users/{id}", FIRST);
                    router.add(Method.GET, "/{section}/{name}", SECOND);
                    assertEquals(Map.of("section", "posts", "name", "1"), router.findRoute("/posts/1").params());
                });
    }

    private static void decodingTests() {
        test("findRoute decodes a parameter",
                () -> assertEquals(Map.of("name", "a b"),
                        router(Method.GET, "/users/{name}", FIRST).findRoute("/users/a%20b").params()));
        test("findRoute keeps a plus in a parameter",
                () -> assertEquals(Map.of("name", "a+b"),
                        router(Method.GET, "/users/{name}", FIRST).findRoute("/users/a+b").params()));
        test("findRoute decodes UTF-8 in a parameter",
                () -> assertEquals(Map.of("name", "café"),
                        router(Method.GET, "/users/{name}", FIRST).findRoute("/users/caf%C3%A9").params()));
        test("findRoute decodes the path before a static match",
                () -> assertEquals(FIRST, getHandler(router(Method.GET, "/café", FIRST), "/caf%C3%A9")));
        test("findRoute decodes an escaped brace as a literal character",
                () -> assertEquals(Map.of("id", "{x}"),
                        router(Method.GET, "/users/{id}", FIRST).findRoute("/users/%7Bx%7D").params()));
        test("findRoute decodes an escaped percent sign before 2F once",
                () -> assertEquals(Map.of("name", "a%2Fb"),
                        router(Method.GET, "/{name}", FIRST).findRoute("/a%252Fb").params()));
        test("findRoute rejects an encoded slash",
                () -> assertStatus(400, () -> router(Method.GET, "/{name}", FIRST).findRoute("/a%2Fb")));
        test("findRoute rejects a lowercase encoded slash",
                () -> assertStatus(400, () -> router(Method.GET, "/{name}", FIRST).findRoute("/a%2fb")));
        test("findRoute rejects an encoded slash on an unknown path",
                () -> assertStatus(400, () -> new Router().findRoute("/a%2Fb")));
        test("findRoute rejects a bad escape",
                () -> assertStatus(400, () -> router(Method.GET, "/{name}", FIRST).findRoute("/a%zz")));
        test("findRoute rejects a cut escape",
                () -> assertStatus(400, () -> router(Method.GET, "/{name}", FIRST).findRoute("/a%4")));
    }

    private static void allowedMethodsTests() {
        test("allowedMethods adds HEAD and OPTIONS to GET",
                () -> assertEquals("GET, HEAD, OPTIONS", allowed(Method.GET)));
        test("allowedMethods adds only OPTIONS without GET",
                () -> assertEquals("POST, OPTIONS", allowed(Method.POST)));
        test("allowedMethods keeps HEAD without GET",
                () -> assertEquals("HEAD, OPTIONS", allowed(Method.HEAD)));
        test("allowedMethods lists OPTIONS once when it has a handler",
                () -> assertEquals("OPTIONS", allowed(Method.OPTIONS)));
        test("allowedMethods lists HEAD once when it has a handler",
                () -> assertEquals("GET, HEAD, OPTIONS", allowed(Method.HEAD, Method.GET)));
        test("allowedMethods lists methods in declaration order",
                () -> assertEquals("GET, HEAD, DELETE, OPTIONS, PATCH",
                        allowed(Method.PATCH, Method.DELETE, Method.GET)));
    }

    private static void routeListTests() {
        test("routes is empty without registered routes",
                () -> assertEquals(List.of(), new Router().routes()));
        test("routes sorts by path, then by method",
                () -> {
                    var router = new Router();
                    router.add(Method.DELETE, "/users/{id}", FIRST);
                    router.add(Method.POST, "/users", FIRST);
                    router.add(Method.GET, "/users/{id}", FIRST);
                    router.add(Method.GET, "/", FIRST);
                    assertEquals(List.of(
                            new Router.Route(Method.GET, "/"),
                            new Router.Route(Method.POST, "/users"),
                            new Router.Route(Method.GET, "/users/{id}"),
                            new Router.Route(Method.DELETE, "/users/{id}")),
                            router.routes());
                });
        test("routes lists a path with a trailing slash once without it",
                () -> {
                    var router = new Router();
                    router.add(Method.GET, "/users/", FIRST);
                    router.add(Method.GET, "/users", SECOND);
                    assertEquals(List.of(new Router.Route(Method.GET, "/users")), router.routes());
                });
    }
}
