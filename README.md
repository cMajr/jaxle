# Jaxle

A lightweight Java web framework with no dependencies.

Jaxle reads HTTP straight from the socket, with no Jetty or Netty
underneath. Routes are registered with `server.get`, `server.post` and
similar methods, where a segment in braces such as `/users/{id}` becomes
a path parameter.

Jaxle requires Java 21 or later.

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

To use Jaxle in your own project, compile the `jaxle/` sources together
with your code.

## Limits

- Each connection serves one request.
- Timeouts and sizes are fixed (20 s per read, 8 KiB per line, 100 headers, 500 KiB body).
- Request bodies need `content-length`. Chunked encoding is not supported.
- No middleware.
- No JSON mapping. `Response.json` takes a string you build yourself.
