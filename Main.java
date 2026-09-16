import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {
        Server server = new Server();

        server.addRoute(Method.GET, "/hello", req -> "Hello");
        server.addRoute(Method.POST, "/echo", req -> req.text());

        System.out.println("Server started on http://localhost:8080");

        server.start();
    }
}