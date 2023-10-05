package cbor.exceptions;

public class InvalidDataItemException extends RuntimeException {
    public InvalidDataItemException(String message) {
        super(message);
    }

    public InvalidDataItemException(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidDataItemException(Throwable cause) {
        super(cause);
    }
}
