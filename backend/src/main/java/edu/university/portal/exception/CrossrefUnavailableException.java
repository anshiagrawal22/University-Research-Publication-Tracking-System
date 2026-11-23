package edu.university.portal.exception;

public class CrossrefUnavailableException extends RuntimeException {

    private final int status;

    public CrossrefUnavailableException(String message, int status) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
