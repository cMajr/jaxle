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
            client.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 5\r\n\r\nhello".getBytes(StandardCharsets.UTF_8));
            client.close();
        }
    }
}
