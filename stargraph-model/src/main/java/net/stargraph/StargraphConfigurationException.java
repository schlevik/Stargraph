package net.stargraph;

public class StargraphConfigurationException extends StarGraphException {
    public StargraphConfigurationException(Exception cause) {
        super(cause);
    }

    public StargraphConfigurationException(String message) {
        super(message);
    }

    public StargraphConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }

}
