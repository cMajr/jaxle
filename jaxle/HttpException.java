package jaxle;

import java.util.Objects;

/**
 * Thrown to end the current request with the given status.
 *
 * <p>A handler normally answers by returning a {@link Response}. A method
 * the handler calls cannot do that when it returns something else, such
 * as the name of a user. Throwing this exception instead makes the server
 * answer with the given status.
 *
 * <pre>{@code
 * String name = users.get(id);
 * if (name == null) {
 *     throw new HttpException(404, "User with id " + id + " not found");
 * }
 * return name;
 * }</pre>
 *
 * <p>When the status is below 500, the message is sent to the client as
 * the response body. For a status of 500 or above, it stays in the log
 * with the stack trace and never reaches the client.
 */
public class HttpException extends RuntimeException {
    private final int status;

    /**
     * Creates an exception that ends the request with the given status.
     *
     * @param status the status code of the response
     * @param message the detail message
     * @throws NullPointerException if the message is null
     */
    public HttpException(int status, String message) {
        super(Objects.requireNonNull(message, "message"));
        this.status = status;
    }

    /**
     * {@return the status code of the response}
     */
    public int status() {
        return this.status;
    }
}
