import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class Server {
    ServerSocket socket;

    public Server() throws IOException {
        this.socket = new ServerSocket(8080);
    }

    public void start() throws IOException {
        while (true) {
            Socket client = socket.accept();
            System.out.println("OK");
            var in = new BufferedReader(new InputStreamReader(client.getInputStream()));
            String requestLine = in.readLine();
            String[] parts = requestLine.split(" ");
            System.out.println("method=" + parts[0] + " path=" + parts[1]);
            String path = parts[1];
            var out = client.getOutputStream();
            if (path.equals("/hello")) {
                out.write(response(200, "OK", "Hello").getBytes(StandardCharsets.UTF_8));
            } else {
                out.write(response(404, "Not Found", "not found").getBytes(StandardCharsets.UTF_8));
            }
            client.close();
        }
    }

    String response(int status, String text, String body) {
        return "HTTP/1.1 " + status + " " + text + "\r\n" + "Content-Length: " + body.getBytes(StandardCharsets.UTF_8).length + "\r\n\r\n" + body;
    }
}
