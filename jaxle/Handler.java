package jaxle;

public interface Handler {
    /**
     * Handles a request that matched the route of this handler.
     *
     * <p>If this method throws an exception, the server logs it and answers
     * with status 500 before it moves on to the next connection.
     *
     * @param request the incoming request
     * @return the response to send back, never {@code null}
     */
    Response handle(Request request);
}
