package dev.texturemixer.core.texture;

import dev.texturemixer.core.pack.SafePaths;

/**
 * Identifies one texture independently of the pack it comes from: the namespace and
 * the path below {@code textures/}, without the {@code .png} extension.
 * {@code minecraft:block/stone} lives at {@code assets/minecraft/textures/block/stone.png}.
 */
public record TextureId(String namespace, String path) implements Comparable<TextureId> {
    private static final String TEXTURES = "/textures/";

    public TextureId {
        if (!SafePaths.isValidNamespace(namespace) || !SafePaths.isValidResourcePath(path)) {
            throw new IllegalArgumentException("Invalid texture id " + namespace + ":" + path);
        }
    }

    /**
     * Parses {@code assets/<namespace>/textures/<path>.png}. Returns {@code null} for any
     * other file, or for names Minecraft itself would reject (upper case, spaces, ...).
     */
    public static TextureId fromAssetPath(String assetPath) {
        if (!assetPath.startsWith("assets/") || !assetPath.endsWith(".png")) {
            return null;
        }
        int namespaceEnd = assetPath.indexOf('/', "assets/".length());
        if (namespaceEnd < 0 || !assetPath.startsWith(TEXTURES, namespaceEnd)) {
            return null;
        }
        String namespace = assetPath.substring("assets/".length(), namespaceEnd);
        String path = assetPath.substring(namespaceEnd + TEXTURES.length(), assetPath.length() - ".png".length());
        if (!SafePaths.isValidNamespace(namespace) || !SafePaths.isValidResourcePath(path)) {
            return null;
        }
        return new TextureId(namespace, path);
    }

    /** True if the path looks like a texture but has characters Minecraft rejects. */
    public static boolean looksLikeInvalidTexture(String assetPath) {
        return assetPath.startsWith("assets/") && assetPath.contains(TEXTURES)
                && assetPath.toLowerCase(java.util.Locale.ROOT).endsWith(".png") && fromAssetPath(assetPath) == null;
    }

    /** Parses {@code namespace:path}; a missing namespace means {@code minecraft}. */
    public static TextureId parse(String text) {
        int colon = text.indexOf(':');
        return colon < 0 ? new TextureId("minecraft", text) : new TextureId(text.substring(0, colon), text.substring(colon + 1));
    }

    public String assetPath() {
        return "assets/" + namespace + TEXTURES + path + ".png";
    }

    public String metadataPath() {
        return assetPath() + ".mcmeta";
    }

    /** Last path segment, e.g. {@code stone} for {@code block/stone}. */
    public String shortName() {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }

    @Override
    public int compareTo(TextureId other) {
        int byNamespace = namespace.compareTo(other.namespace);
        return byNamespace != 0 ? byNamespace : path.compareTo(other.path);
    }

    @Override
    public String toString() {
        return namespace + ":" + path;
    }
}
