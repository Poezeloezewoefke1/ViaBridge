package dev.texturemixer.core.pack;

public enum PackKind {
    /** A folder in the resourcepacks directory. */
    FOLDER,
    /** A .zip file in the resourcepacks directory. */
    ZIP,
    /** Minecraft's own textures, read through the game rather than from disk. */
    BUILTIN
}
