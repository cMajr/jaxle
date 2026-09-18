package example;

import jaxle.Request;
import jaxle.Response;
import jaxle.Server;

import static jaxle.Response.badRequest;
import static jaxle.Response.conflict;
import static jaxle.Response.created;
import static jaxle.Response.notFound;
import static jaxle.Response.ok;

import java.util.HashMap;
import java.util.Map;

public class Users {
    private final Map<Integer, String> users = new HashMap<>();
    private int nextId = 0;

    void addRoutes(Server server) {
        server.post("/register", this::register);
        server.get("/users/{id}", this::findById);
    }

    Response register(Request request) {
        String username = request.text().strip();

        if (username.isEmpty()) {
            return badRequest("Username must not be empty");
        }

        if (users.containsValue(username)) {
            return conflict("Username " + username + " is already taken");
        }

        nextId++;
        users.put(nextId, username);
        var res = users.get(nextId);
        return created("/users/" + nextId, res);
    }

    Response findById(Request request) {
        int id = request.param("id", Integer::parseInt);
        var res = users.get(id);

        if (res == null) {
            return notFound("User with id " + id + " not found");
        }

        return ok(res);
    }
}
