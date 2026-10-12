package dev.texturemixer.core.generate;

import java.util.List;

/**
 * Outcome of generating a pack.
 *
 * @param written  textures written
 * @param skipped  textures left out, each with the reason (shown to the player)
 * @param target   where the pack was written
 */
public record GenerationResult(int written, List<String> skipped, String target) {
}
