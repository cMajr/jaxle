# Jaxle

A lightweight Java web framework with no dependencies.

Jaxle reads HTTP straight from the socket, with no Jetty or Netty
underneath. Routes are registered with `server.get`, `server.post` and
similar methods, where a segment in braces such as `/users/{id}` becomes
a path parameter.

Jaxle requires Java 17 or later.

```java
import java.io.IOException;

import jaxle.Request;
import jaxle.Response;
import jaxle.Server;

import static jaxle.Response.ok;

public class Hello {
    public static void main(String[] args) throws IOException {
        // new Server() listens on 8080
        Server server = new Server(8081);
        server.get("/greeting", Hello::greeting);
        server.start();
    }

    static Response greeting(Request request) {
        // 200 with a text/plain body
        return ok("Hello from jaxle");
    }
}
```

```
$ curl localhost:8081/greeting
Hello from jaxle
```

## Example

`example/` holds a small user registration service.

```
javac -d out jaxle/*.java example/*.java && java -cp out example.Main
curl -i -d alice localhost:8081/register
curl localhost:8081/users/1
```

There is no build file yet. To use jaxle in your own project, compile
the `jaxle/` sources together with your code.

## Limits

- One thread serves all connections, one at a time.
- One request per connection, with no keep-alive.
- No read timeouts. A client that stops sending blocks the whole server.
- No limits on the length of the request line, the headers or the body.
- The body is read by `content-length` only. Chunked transfer coding is
  not supported.
- A `HEAD` handler sends its body, which RFC 9110 forbids.
- No middleware.
- No JSON parsing or serialization. `Response.json` sends a string as is.
