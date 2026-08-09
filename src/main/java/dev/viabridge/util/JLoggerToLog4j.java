package dev.viabridge.util;

import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Via's API is written against {@code java.util.logging}; Minecraft logs through Log4j.
 * This forwards one to the other so Via's output lands in the game log like everything
 * else.
 */
public final class JLoggerToLog4j extends Logger {

    private final org.apache.logging.log4j.Logger delegate;

    public JLoggerToLog4j(final org.apache.logging.log4j.Logger delegate) {
        super(delegate.getName(), null);
        this.delegate = delegate;
        setLevel(Level.ALL);
    }

    @Override
    public void log(final LogRecord record) {
        final String message = formatted(record);
        final Throwable thrown = record.getThrown();
        final int level = record.getLevel().intValue();

        if (level >= Level.SEVERE.intValue()) {
            delegate.error(message, thrown);
        } else if (level >= Level.WARNING.intValue()) {
            delegate.warn(message, thrown);
        } else if (level >= Level.INFO.intValue()) {
            delegate.info(message, thrown);
        } else if (level >= Level.FINE.intValue()) {
            delegate.debug(message, thrown);
        } else {
            delegate.trace(message, thrown);
        }
    }

    /**
     * Applies {@link java.text.MessageFormat}-style parameter substitution the way JUL
     * would, so Via's parameterised log calls do not come out with literal {@code {0}}
     * placeholders.
     */
    private static String formatted(final LogRecord record) {
        final String message = record.getMessage();
        final Object[] parameters = record.getParameters();
        if (message == null || parameters == null || parameters.length == 0) {
            return message;
        }
        try {
            return java.text.MessageFormat.format(message, parameters);
        } catch (final IllegalArgumentException e) {
            return message;
        }
    }
}
