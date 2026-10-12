package dev.texturemixer.fabric.test;

import dev.texturemixer.core.TextureMixerConstants;
import dev.texturemixer.core.generate.GenerationResult;
import dev.texturemixer.core.pack.PackProblem;
import dev.texturemixer.core.session.MixerSession;
import dev.texturemixer.core.texture.TextureEntry;
import dev.texturemixer.core.texture.TextureId;
import dev.texturemixer.core.texture.TextureIndex;
import dev.texturemixer.fabric.TextureMixerManager;
import dev.texturemixer.fabric.compat.VersionCompat;
import dev.texturemixer.fabric.gui.TextureMixerScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.pack.PackScreen;
import net.minecraft.resource.PackVersion;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceType;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

/**
 * Drives the real game: installs test packs, opens the vanilla resource pack screen,
 * presses "Mix Textures", picks textures, presses Apply and checks that Minecraft
 * really loads the picked textures from the generated pack. Then checks undo, profiles,
 * export, and that closing the pack screen keeps the generated pack enabled.
 */
public class TextureMixerClientGameTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("TextureMixer-GameTest");

    private static final String RED = "file/TM Test Red";
    private static final String BLUE = "file/TM Test Blue.zip";
    private static final TextureId STONE = TextureId.parse("minecraft:block/stone");
    private static final TextureId SWORD = TextureId.parse("minecraft:item/diamond_sword");
    private static final TextureId WATER = TextureId.parse("minecraft:block/water_still");
    private static final TextureId DIRT = TextureId.parse("minecraft:block/dirt");
    private static final TextureId ZOMBIE = TextureId.parse("minecraft:entity/zombie/zombie");

    @Override
    public void runTest(ClientGameTestContext context) {
        PackVersion gameVersion = context.computeOnClient(client ->
                SharedConstants.getGameVersion().packVersion(ResourceType.CLIENT_RESOURCES));
        check(PackVersion.of(VersionCompat.packFormat()).equals(gameVersion),
                "VersionCompat.packFormat() is " + VersionCompat.packFormat() + " but the game uses " + gameVersion);
        int gameFormat = VersionCompat.packFormat();

        Path packDir = context.computeOnClient(MinecraftClient::getResourcePackDir);
        try {
            TestPacks.write(packDir, gameFormat);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        // 1. The vanilla resource pack screen, opened like the Options screen opens it.
        context.runOnClient(client -> client.setScreen(new PackScreen(client.getResourcePackManager(), manager -> {
            client.options.refreshResourcePacks(manager);
            client.setScreen(null);
        }, client.getResourcePackDir(), Text.translatable("resourcePack.title"))));
        context.waitForScreen(PackScreen.class);
        context.waitTicks(5);
        context.takeScreenshot("texturemixer-1-pack-screen");

        // 2. Our button opens the mixer, which scans every installed pack (none are enabled).
        context.clickScreenButton("texturemixer.button.mix");
        context.waitForScreen(TextureMixerScreen.class);
        context.waitFor(client -> TextureMixerManager.get().session().index() != null, 20 * 120);

        context.runOnClient(client -> {
            TextureIndex index = TextureMixerManager.get().currentIndex();
            LOG.info("Indexed {} textures from {} sources; problems: {}", index.size(), index.sources().size(), index.problems());
            check(index.source(RED) != null, "disabled folder pack was not detected");
            check(index.source(BLUE) != null, "disabled zip pack was not detected");
            TextureEntry stone = index.get(STONE);
            check(stone != null && stone.vanilla() != null && stone.variant(RED) != null && stone.variant(BLUE) != null,
                    "stone should be offered from Minecraft and both packs, got " + stone);
            check(index.get(WATER).variant(BLUE).animated(), "animated water from the zip pack was not recognised");
            check(index.get(ZOMBIE) != null, "entity texture missing");
            check(index.problems().stream().anyMatch(p -> p.packName().equals("TM Broken.zip") && p.severity() == PackProblem.Severity.ERROR),
                    "broken zip was not reported");
            check(index.size() > 1000, "vanilla textures were not indexed (" + index.size() + ")");

            MixerSession session = TextureMixerManager.get().session();
            session.resetAll();
            session.choose(STONE, BLUE);
            session.choose(SWORD, RED);
            session.choose(WATER, BLUE);
            session.choose(ZOMBIE, BLUE);
            session.choose(DIRT, TextureMixerConstants.VANILLA_SOURCE_ID);
            session.focus(STONE);
        });
        context.waitTicks(20);
        context.takeScreenshot("texturemixer-2-mixer-screen");

        // 3. Apply: pack written, enabled on top, resources reloaded.
        context.clickScreenButton("texturemixer.apply");
        context.waitFor(client -> client.getResourcePackManager().getEnabledIds().contains(TextureMixerConstants.GENERATED_PACK_ID), 20 * 60);
        context.waitFor(client -> client.getOverlay() == null, 20 * 180);
        context.waitTicks(5);

        context.runOnClient(client -> {
            for (TextureId id : new TextureId[]{STONE, SWORD, WATER, ZOMBIE, DIRT}) {
                Resource resource = client.getResourceManager()
                        .getResource(VersionCompat.id(id.namespace(), "textures/" + id.path() + ".png")).orElseThrow();
                check(resource.getPackId().equals(TextureMixerConstants.GENERATED_PACK_ID),
                        id + " is loaded from " + resource.getPackId() + " instead of the generated pack");
            }
            check(client.options.resourcePacks.contains(TextureMixerConstants.GENERATED_PACK_ID), "generated pack not saved in options");
            Path generated = packDir.resolve(TextureMixerConstants.GENERATED_PACK_NAME);
            try {
                check(Arrays.equals(Files.readAllBytes(generated.resolve(STONE.assetPath())), TestPacks.BLUE_32), "stone is not the blue pack's texture");
                check(Arrays.equals(Files.readAllBytes(generated.resolve(SWORD.assetPath())), TestPacks.RED_16), "sword is not the red pack's texture");
                check(Files.exists(generated.resolve(WATER.metadataPath())), "water animation metadata was not copied");
                check(Files.exists(generated.resolve(DIRT.assetPath())), "vanilla dirt was not copied");
                check(Arrays.equals(Files.readAllBytes(packDir.resolve("TM Test Red").resolve(STONE.assetPath())), TestPacks.RED_16),
                        "source pack was modified");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        context.takeScreenshot("texturemixer-3-after-apply");

        // 4. Undo/redo, profiles and export.
        context.runOnClient(client -> {
            MixerSession session = TextureMixerManager.get().session();
            session.undo();
            check(session.choice(DIRT) == null, "undo did not remove the last pick");
            session.redo();
            check(TextureMixerConstants.VANILLA_SOURCE_ID.equals(session.choice(DIRT)), "redo did not restore the pick");
            check(session.createProfile("GameTest Copy", true) == null, "could not create profile");
            check(session.selections().size() == 5, "duplicated profile lost picks");
            session.switchProfile("Default");
        });
        CompletableFuture<GenerationResult> export = context.computeOnClient(client -> TextureMixerManager.get().export());
        GenerationResult exported = export.join();
        check(Files.exists(Path.of(exported.target())) && exported.written() == 5, "export failed: " + exported);
        LOG.info("Exported {}", exported.target());

        // 5. Back to the pack screen and confirm it: the generated pack must stay enabled.
        context.clickScreenButton("gui.done");
        context.waitForScreen(PackScreen.class);
        context.waitTicks(5);
        context.takeScreenshot("texturemixer-4-pack-screen-after-apply");
        context.clickScreenButton("gui.done");
        context.waitForScreen(TitleScreen.class);
        context.waitFor(client -> client.getOverlay() == null, 20 * 180);
        context.runOnClient(client -> check(client.getResourcePackManager().getEnabledIds().contains(TextureMixerConstants.GENERATED_PACK_ID),
                "closing the pack screen disabled the generated pack"));

        Path profiles = context.computeOnClient(client -> TextureMixerManager.get().session().profiles().file());
        context.waitFor(client -> Files.exists(profiles), 20 * 10);
        LOG.info("All Texture Mixer game test checks passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
