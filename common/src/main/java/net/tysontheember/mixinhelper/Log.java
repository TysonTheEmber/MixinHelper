package net.tysontheember.mixinhelper;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Centralized logging for MixinHelper. Uses Log4j which is available
 * on the classpath during mixin bootstrap on all loaders.
 */
public final class Log {

    private static final Logger LOGGER = LogManager.getLogger(MixinHelperConstants.MOD_NAME);

    private Log() {}

    public static void info(String message) {
        LOGGER.info(MixinHelperConstants.LOG_PREFIX + " " + message);
    }

    public static void info(String message, Object... args) {
        LOGGER.info(MixinHelperConstants.LOG_PREFIX + " " + message, args);
    }

    public static void warn(String message) {
        LOGGER.warn(MixinHelperConstants.LOG_PREFIX + " " + message);
    }

    public static void warn(String message, Object... args) {
        LOGGER.warn(MixinHelperConstants.LOG_PREFIX + " " + message, args);
    }

    public static void error(String message) {
        LOGGER.error(MixinHelperConstants.LOG_PREFIX + " " + message);
    }

    public static void error(String message, Throwable throwable) {
        LOGGER.error(MixinHelperConstants.LOG_PREFIX + " " + message, throwable);
    }

    public static void debug(String message) {
        LOGGER.debug(MixinHelperConstants.LOG_PREFIX + " " + message);
    }

    public static void debug(String message, Object... args) {
        LOGGER.debug(MixinHelperConstants.LOG_PREFIX + " " + message, args);
    }
}
