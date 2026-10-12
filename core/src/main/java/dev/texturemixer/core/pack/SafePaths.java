package dev.texturemixer.core.pack;

import java.util.Locale;

/**
 * Validation for paths inside packs. Every path used to read from or write to a pack
 * goes through here, so a malformed or hostile ZIP cannot make Texture Mixer read or
 * write outside the pack (no {@code ..}, no absolute paths, no drive letters).
 */
public final class SafePaths {
    private SafePaths() {
    }

    /**
     * Normalizes a ZIP entry or relative file name to forward slashes and checks that it
     * stays inside the pack. Returns {@code null} if the name is unsafe.
     */
    public static String normalize(String raw) {
        if (raw == null || raw.isEmpty() || raw.indexOf('\0') >= 0) {
            return null;
        }
        String path = raw.replace('\\', '/');
        if (path.startsWith("/") || (path.length() > 1 && path.charAt(1) == ':')) {
            return null;
        }
        StringBuilder out = new StringBuilder(path.length());
        for (String part : path.split("/")) {
            if (part.isEmpty() || part.equals(".")) {
                continue;
            }
            if (part.equals("..")) {
                return null;
            }
            if (out.length() > 0) {
                out.append('/');
            }
            out.append(part);
        }
        return out.length() == 0 ? null : out.toString();
    }

    /** Minecraft's rule for namespaces: {@code [a-z0-9_.-]+}. */
    public static boolean isValidNamespace(String namespace) {
        if (namespace.isEmpty()) {
            return false;
        }
        for (int i = 0; i < namespace.length(); i++) {
            char c = namespace.charAt(i);
            if (!(c == '_' || c == '-' || c == '.' || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9'))) {
                return false;
            }
        }
        return true;
    }

    /** Minecraft's rule for resource paths: {@code [a-z0-9_.-/]+}. */
    public static boolean isValidResourcePath(String path) {
        if (path.isEmpty() || path.startsWith("/") || path.endsWith("/") || path.contains("//")) {
            return false;
        }
        for (String part : path.split("/")) {
            if (part.equals(".") || part.equals("..")) {
                return false;
            }
        }
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (!(c == '_' || c == '-' || c == '.' || c == '/' || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9'))) {
                return false;
            }
        }
        return true;
    }

    public static boolean hasExtension(String path, String extension) {
        return path.toLowerCase(Locale.ROOT).endsWith(extension);
    }
}
