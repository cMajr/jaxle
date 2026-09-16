import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import static java.lang.System.Logger.Level.ERROR;
import static java.lang.System.Logger.Level.valueOf;

public class Server {
    private static final System.Logger log = System.getLogger("jaxle.Server");

    ServerSocket socket;

    // Path is resolved first to distinguish 404 from 405.
    Map<String, Map<Method, Handler>> routes = new HashMap<>();

    public Server() throws IOException {
        this.socket = new ServerSocket(8080);
    }

    public void start() throws IOException {
        while (true) {
            Socket client = socket.accept();
            try (client) {
                try {
                    var in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                    String requestLine = in.readLine();

                    // Client closed the connection without sending any data.
                    if (requestLine == null) {
                        continue;
                    }

                    String[] parts = requestLine.split(" ");

                    if (parts.length != 3) {
                        client.getOutputStream().write(response(400, "Bad Request", "Bad Request").getBytes(StandardCharsets.UTF_8));
                        continue;
                    }

                    Method method;

                    try { 
                        method = Method.valueOf(parts[0]);
                    } catch (IllegalArgumentException e) {
                        client.getOutputStream().write(response(501, "Not Implemented", "Not Implemented").getBytes(StandardCharsets.UTF_8));
                        continue;
                    }

                    String path = parts[1];

                    var out = client.getOutputStream();

                    Map<Method, Handler> inner = routes.get(path);

                    if (inner == null) {
                        out.write(response(404, "Not Found", "Not Found").getBytes(StandardCharsets.UTF_8));
                    } else {
                        Handler handler = inner.get(method);
                        if (handler == null) {
                            out.write(response(405, "Method Not Allowed", "Method Not Allowed").getBytes(StandardCharsets.UTF_8));
                        } else {
                            out.write(response(200, "OK", handler.handle()).getBytes(StandardCharsets.UTF_8));
                        }
                    }
                } catch (Exception e) {
                    log.log(ERROR, "Request failed", e);
                    try {
                        var out = client.getOutputStream();
                        out.write(response(500, "Internal Server Error", "Internal Server Error").getBytes(StandardCharsets.UTF_8));
                    } catch (Exception suppressed) {
                        // Connection is already broken, the real cause is logged above.
                    }
                }
            }
        }
    }

    String response(int status, String text, String body) {
        return "HTTP/1.1 " + status + " " + text + "\r\n" + "Content-Length: " + body.getBytes(StandardCharsets.UTF_8).length + "\r\n\r\n" + body;
    }

    void addRoute(Method method, String path, Handler handler) {
        Map<Method, Handler> inner = this.routes.get(path);

        if (inner == null) {
            inner = new HashMap<>();
            // First method for this path.
            this.routes.put(path, inner);
        }

        // Map from routes is modified in place.
        inner.put(method, handler);
    }
}
