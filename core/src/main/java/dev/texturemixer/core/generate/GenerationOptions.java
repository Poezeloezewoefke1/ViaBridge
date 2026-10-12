package dev.texturemixer.core.generate;

/**
 * Per-version and per-run settings for {@link PackGenerator}.
 *
 * @param packFormat  resource pack format of the running Minecraft version
 * @param profileName profile the pack was built from (shown in the description)
 * @param gameVersion Minecraft version string, recorded in the pack for reference
 * @param icon        PNG bytes for {@code pack.png}, or {@code null} for none
 */
public record GenerationOptions(int packFormat, String profileName, String gameVersion, byte[] icon) {
    /** Pack format from which Minecraft (1.21.9+) expects {@code min_format}/{@code max_format}. */
    public static final int MIN_MAX_FORMAT_SINCE = 65;
}
