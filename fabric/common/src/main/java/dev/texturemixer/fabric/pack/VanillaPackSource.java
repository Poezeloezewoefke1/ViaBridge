package dev.texturemixer.fabric.pack;

import dev.texturemixer.core.TextureMixerConstants;
import dev.texturemixer.core.pack.PackKind;
import dev.texturemixer.core.pack.PackMetadata;
import dev.texturemixer.core.pack.PackSource;
import dev.texturemixer.fabric.compat.VersionCompat;
import net.minecraft.resource.InputSupplier;
import net.minecraft.resource.ResourcePack;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * Minecraft's own textures, read through the game's built-in resource pack. Offered as
 * the "Minecraft (default)" choice so a texture can be forced back to vanilla even
 * when another enabled pack changes it.
 */
public final class VanillaPackSource implements PackSource {
    private final ResourcePack pack;

    public VanillaPackSource(ResourcePack pack) {
        this.pack = pack;
    }

    @Override
    public String id() {
        return TextureMixerConstants.VANILLA_SOURCE_ID;
    }

    @Override
    public String displayName() {
        return "Minecraft";
    }

    @Override
    public PackKind kind() {
        return PackKind.BUILTIN;
    }

    @Override
    public PackMetadata metadata() {
        return PackMetadata.NONE;
    }

    @Override
    public void forEachFile(String prefix, FileVisitor visitor) throws IOException {
        if (!prefix.equals("assets/")) {
            return;
        }
        try {
            for (String namespace : pack.getNamespaces(ResourceType.CLIENT_RESOURCES)) {
                pack.findResources(ResourceType.CLIENT_RESOURCES, namespace, "textures", (id, supplier) -> {
                    try {
                        visitor.visit("assets/" + id.getNamespace() + "/" + id.getPath(), -1);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            }
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    @Override
    public InputStream open(String path) throws IOException {
        InputSupplier<InputStream> supplier = supplier(path);
        if (supplier == null) {
            throw new FileNotFoundException(path + " in Minecraft");
        }
        return supplier.get();
    }

    @Override
    public boolean exists(String path) {
        return supplier(path) != null;
    }

    private InputSupplier<InputStream> supplier(String path) {
        if (!path.startsWith("assets/")) {
            return null;
        }
        int slash = path.indexOf('/', "assets/".length());
        if (slash < 0) {
            return null;
        }
        Identifier id;
        try {
            id = VersionCompat.id(path.substring("assets/".length(), slash), path.substring(slash + 1));
        } catch (RuntimeException e) {
            return null;
        }
        return pack.open(ResourceType.CLIENT_RESOURCES, id);
    }

    @Override
    public String fingerprint() {
        return "vanilla";
    }
}
