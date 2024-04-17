package org.sydlabz.lib.cache;

public class NullKeyException extends RuntimeException {
    public NullKeyException() {
        super();
    }

    public NullKeyException(final String message) {
        super(message);
    }
}
