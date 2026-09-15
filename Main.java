import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {
        Server server = new Server();
        server.route("/hello", () -> "Hello");
        System.out.println("Server started on http://localhost:8080");
        server.start();
    }
}