# Jaxle

[Русский](README.ru.md)

**A compact, dependency-free Java web framework with its own HTTP server.**

In Jaxle, a handler is a function that takes a `Request` and returns a
`Response`. Frameworks such as Javalin instead put headers, status, and
body into a mutable context, where a second call to set the result
overwrites the first. The compiler won't let a handler return anything
other than a `Response` object, checking all of its branches. To test a
handler, it's enough to call it with a `Request` and check the `Response`
it returns, without starting a server.

## Quick start

```java
import jaxle.Server;

public class Main {
    public static void main(String[] args) {
        // Pass any port here, or call new Server() to listen on 5295
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

An example with user registration is in [`examples/example/`](examples/example).

```
javac -d out src/jaxle/*.java examples/example/*.java && java -cp out example.Main
```

## Features

- Routes for GET, POST, PUT, PATCH and DELETE, or any `Method` via `addRoute`
- Typed path parameters `request.param("id", UUID::fromString)`
- Percent-decoded paths and query parameters, `request.query("page")`
- Request body as bytes or UTF-8 text, `body()` and `text()`
- Factories for common responses, `ok`, `created`, `notFound`, `json` and more
- `HttpException` to end a request with an error status from anywhere
- HEAD, OPTIONS and 405 answered automatically
- `new Server(0)` for a free port, read back with `port()`
- `close()` to stop the server and return from `start()`

## Usage

Jaxle requires Java 21 or later. Until the first release, build a jar
in a clone of this repository, then compile your code against it.

```
javac -d out src/jaxle/*.java && jar cf jaxle.jar -C out .
javac -cp jaxle.jar -d app Main.java Users.java
java -cp jaxle.jar:app Main
```

## Scope

Jaxle speaks HTTP/1.1 and is meant to run behind a reverse proxy such as
nginx, which terminates TLS and HTTP/2.

- Chunked requests are not supported.
- A request must fit into 30 seconds, a line into 8 KiB, the headers
  into 100 fields and 32 KiB, the body into 500 KiB. No more than 500
  connections are open at once.
- Handlers run concurrently in virtual threads and must be thread-safe.

## Not yet

- Keep-alive.
- Configurable limits.
- Write timeout.
- Middleware.
- JSON mapping.

## License

Apache License 2.0, see [LICENSE](LICENSE).
