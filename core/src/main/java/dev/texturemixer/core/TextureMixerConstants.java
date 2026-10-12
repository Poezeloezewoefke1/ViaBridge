package dev.texturemixer.core;

/**
 * Names and limits shared by every part of Texture Mixer.
 */
public final class TextureMixerConstants {
    /** Folder name of the generated pack inside Minecraft's {@code resourcepacks} directory. */
    public static final String GENERATED_PACK_NAME = "Texture Mixer - Custom Pack";

    /** Minecraft's pack id for the generated folder pack ({@code file/} + folder name). */
    public static final String GENERATED_PACK_ID = "file/" + GENERATED_PACK_NAME;

    /**
     * Key written into the generated pack's {@code pack.mcmeta}. Packs carrying it are never
     * offered as texture sources and are the only packs Texture Mixer will ever replace.
     */
    public static final String MARKER_KEY = "texturemixer";

    /** Source id used for Minecraft's built-in textures. Matches Minecraft's own pack id. */
    public static final String VANILLA_SOURCE_ID = "vanilla";

    /** Largest single PNG/mcmeta file that will be read from a pack. */
    public static final long MAX_ASSET_BYTES = 64L * 1024 * 1024;

    /** Largest number of entries accepted in one ZIP before it is treated as malformed. */
    public static final int MAX_ZIP_ENTRIES = 500_000;

    private TextureMixerConstants() {
    }
}
