package jaxle;

public class InvalidJsonException extends RuntimeException {
    public InvalidJsonException(String message) {
        this(message, null);
    }

    public InvalidJsonException(String message, Throwable cause) {
        super(message, cause);
    }
}
