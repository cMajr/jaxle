import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {
        Server server = new Server();

        server.addRoute(Method.GET, "/hello", req -> Response.ok("Hello"));
        server.addRoute(Method.POST, "/echo", req -> Response.ok(req.text()));

        System.out.println("Server started on http://localhost:8080");

        server.start();
    }
}
