package jaxle;

class BadRequestException extends RuntimeException {
    BadRequestException(String message)  {
        super(message);
    }
}
