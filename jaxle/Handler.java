package jaxle;

public interface Handler {
    /**
     * Handles a request that matched the route of this handler.
     *
     * <p>If this method throws {@link HttpException}, the client receives the
     * status it carries. If it throws any other exception or returns
     * {@code null}, the server answers with status 500. The exception is also
     * logged with its stack trace.
     *
     * <p>Keep in mind that the server calls this method from several threads
     * at once when requests arrive concurrently, even for the same route.
     *
     * @param request the incoming request
     * @return the response to send back
     */
    Response handle(Request request);
}
