package jaxle;

import java.util.Objects;

/**
 * Thrown to end the current request with the given status.
 *
 * <p>A handler normally answers by returning a {@link Response}. A method
 * the handler calls cannot do that when it returns something else, such
 * as the text of a note. It throws this exception instead, and the server
 * answers with the given status.
 *
 * <pre>{@code
 * String note = notes.get(id);
 * if (note == null) {
 *     throw new HttpException(404, "There is no note " + id);
 * }
 * return note;
 * }</pre>
 *
 * <p>Any other exception becomes 500. The message goes to the client as
 * the body when the status is below 500. For a server error it stays in
 * the log, and the body holds the standard reason phrase.
 */
public class HttpException extends RuntimeException {
    private final int status;

    /**
     * Creates an exception that ends the request with the given status.
     *
     * @param status the status code of the response
     * @param message the reason for the error
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
