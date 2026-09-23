# Jaxle

**A compact, dependency-free Java web framework with its own HTTP server.**

A handler is a function that takes a `Request` and returns a `Response`,
building the whole response at once rather than assembling it step by
step on a mutable object as handlers in Javalin and Spark do. The
compiler ensures that every branch produces a response, and since the
handler is an ordinary function, you can test it without a server by
passing in a `Request` and inspecting the `Response` it returns.

## Quick start

```java
import jaxle.Server;

public class Main {
    public static void main(String[] args) {
        // Pass any port here, or call new Server() to listen on 8080
        Server server = new Server(8081);
        Users users = new Users();
        server.get("/users/{id}", users::findById);
        server.start();
    }
}
```

```java
import java.util.Map;

import jaxle.Request;
import jaxle.Response;

import static jaxle.Response.notFound;
import static jaxle.Response.ok;

public class Users {
    private final Map<Integer, String> users = Map.of(1, "alice", 2, "bob");

    Response findById(Request request) {
        // A non-numeric id gets 400
        int id = request.param("id", Integer::parseInt);
        String name = users.get(id);

        if (name == null) {
            return notFound("No user with id " + id);
        }

        return ok(name);
    }
}
```

Try it with curl.

```
$ curl localhost:8081/users/1        # 200
alice
$ curl localhost:8081/users/3        # 404
No user with id 3
$ curl localhost:8081/users/abc      # 400
bad path parameter id
```

A fuller version with registration is in [`example/`](example).

```
javac -d out jaxle/*.java example/*.java && java -cp out example.Main
```

## Features

- Routes for GET, POST, PUT, PATCH and DELETE, or any `Method` via `addRoute`
- Static paths before patterns, patterns in the order you add them
- Typed path parameters, `request.param("id", UUID::fromString)`
- Percent-decoded query parameters, `request.query("page")`
- Request body as bytes or UTF-8 text, `body()` and `text()`
- Factories for common responses, `ok`, `created`, `notFound` and more
- `HttpException` to end a request with any status from anywhere
- HEAD, OPTIONS and 405 answered automatically
- `new Server(0)` for a free port in tests

## Usage

Jaxle requires Java 21 or later. Until the first release, build a jar
in a clone of this repository, then compile your code against it.

```
javac -d out jaxle/*.java && jar cf jaxle.jar -C out .
javac -cp jaxle.jar -d app Main.java Users.java
java -cp jaxle.jar:app Main
```

## Scope

Jaxle speaks HTTP/1.1 and is meant to run behind a reverse proxy such as
nginx, which terminates TLS and HTTP/2.

- Request bodies need `content-length`. Chunked requests are not supported.
- Limits are fixed at 20 s per read, 8 KiB per line, 100 headers and
  500 KiB per body.
- Handlers run concurrently in virtual threads and must be thread-safe.

## Not yet

- Keep-alive. Each connection serves one request.
- Configurable limits.
- Middleware.
- JSON mapping. `Response.json` takes a string you build yourself.

## License

Apache License 2.0, see [LICENSE](LICENSE).
