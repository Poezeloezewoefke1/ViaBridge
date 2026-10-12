package dev.texturemixer.fabric.mixin;

import net.minecraft.client.gui.screen.pack.PackScreen;
import net.minecraft.client.gui.screen.pack.ResourcePackOrganizer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.nio.file.Path;

/**
 * Read access to two private fields of the vanilla pack screen. Nothing about the
 * screen's behaviour is changed.
 */
@Mixin(PackScreen.class)
public interface PackScreenAccessor {
    /** The pending enable/disable/reorder state; updated after Apply so it is not lost. */
    @Accessor("organizer")
    ResourcePackOrganizer texturemixer$getOrganizer();

    /** Folder the screen manages; tells the resource pack screen apart from the data pack one. */
    @Accessor("file")
    Path texturemixer$getFile();
}
