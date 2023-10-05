package cbor.exceptions;

public class InvalidMajorTypeException extends RuntimeException {
    public InvalidMajorTypeException(String message) {
        super(message);
    }
}
