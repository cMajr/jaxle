package example;

import jaxle.Server;

public class Main {
    public static void main(String[] args) {
        Server server = new Server(8081);

        var users = new Users();

        server.post("/register", users::register);
        server.get("/users/{id}", users::findById);

        server.start();
    }
}
