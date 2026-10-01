package example;

import jaxle.Request;
import jaxle.Response;

import static jaxle.Json.json;
import static jaxle.Response.badRequest;
import static jaxle.Response.conflict;
import static jaxle.Response.created;
import static jaxle.Response.notFound;
import static jaxle.Response.ok;

import java.util.HashMap;
import java.util.Map;

// Since the server may call handlers from several threads at once, both
// methods are synchronized to let only one request at a time touch the map
// and the counter.
public class Users {
    private final Map<Integer, String> users = new HashMap<>();
    private int nextId = 0;

    synchronized Response register(Request request) {
        String username = request.json(NewUser.class).name();

        if (username == null || username.isBlank()) {
            return badRequest(json(new ApiError("Username must not be empty")));
        }

        username = username.strip();

        if (users.containsValue(username)) {
            return conflict(json(new ApiError("Username " + username + " is already taken")));
        }

        nextId++;
        users.put(nextId, username);

        User user = new User(nextId, username);
        return created("/users/" + nextId, json(user));
    }

    synchronized Response findById(Request request) {
        int id = request.param("id", Integer::parseInt);
        String username = users.get(id);

        if (username == null) {
            return notFound(json(new ApiError("User with id " + id + " not found")));
        }

        User user = new User(id, username);
        return ok(json(user));
    }
}
