package dev.texturemixer.core.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Single logger for the mod, so every message is prefixed the same way in latest.log. */
public final class Log {
    public static final Logger LOGGER = LoggerFactory.getLogger("TextureMixer");

    private Log() {
    }
}
