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
 * A pack that comes from the game rather than the resourcepacks folder: Minecraft's own
 * textures (offered as "Minecraft (default)", so a texture can be forced back to vanilla
 * even when another enabled pack changes it), the built-in optional packs such as
 * Programmer Art, and the resources inside installed mods.
 */
public final class GamePackSource implements PackSource {
    private final ResourcePack pack;
    private final String id;
    private final String name;
    private final boolean ownsPack;

    private GamePackSource(ResourcePack pack, String id, String name, boolean ownsPack) {
        this.pack = pack;
        this.id = id;
        this.name = name;
        this.ownsPack = ownsPack;
    }

    /** Minecraft's default textures. The pack belongs to the game and is never closed here. */
    public static GamePackSource vanilla(ResourcePack pack) {
        return new GamePackSource(pack, TextureMixerConstants.VANILLA_SOURCE_ID, "Minecraft", false);
    }

    /** A pack opened from a pack profile; closed together with the texture index. */
    public static GamePackSource opened(ResourcePack pack, String id, String name) {
        return new GamePackSource(pack, id, name, true);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String displayName() {
        return name;
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
            throw new FileNotFoundException(path + " in " + name);
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
        return "game:" + id;
    }

    @Override
    public void close() {
        if (ownsPack) {
            pack.close();
        }
    }
}
