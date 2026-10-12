package dev.texturemixer.core.pack;

/**
 * Something that went wrong while reading a pack, kept so the GUI can show the player
 * which pack is affected and why, instead of failing silently.
 */
public record PackProblem(String packName, Severity severity, String message) {
    public enum Severity {
        /** The whole pack could not be used. */
        ERROR,
        /** The pack is used, but something in it was skipped or may not look right. */
        WARNING
    }

    public static PackProblem error(String packName, String message) {
        return new PackProblem(packName, Severity.ERROR, message);
    }

    public static PackProblem warning(String packName, String message) {
        return new PackProblem(packName, Severity.WARNING, message);
    }

    @Override
    public String toString() {
        return "[" + severity + "] " + packName + ": " + message;
    }
}
