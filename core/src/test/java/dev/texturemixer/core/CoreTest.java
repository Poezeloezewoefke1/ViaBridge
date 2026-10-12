package dev.texturemixer.core;

import dev.texturemixer.core.generate.DirectoryPackWriter;
import dev.texturemixer.core.generate.GenerationOptions;
import dev.texturemixer.core.generate.GenerationResult;
import dev.texturemixer.core.generate.PackGenerator;
import dev.texturemixer.core.generate.ZipPackWriter;
import dev.texturemixer.core.pack.PackMetadata;
import dev.texturemixer.core.pack.PackProblem;
import dev.texturemixer.core.pack.PackScanner;
import dev.texturemixer.core.pack.SafePaths;
import dev.texturemixer.core.pack.ScanResult;
import dev.texturemixer.core.profile.ProfileStore;
import dev.texturemixer.core.selection.Selections;
import dev.texturemixer.core.session.MixerSession;
import dev.texturemixer.core.texture.PngInfo;
import dev.texturemixer.core.texture.TextureCategory;
import dev.texturemixer.core.texture.TextureEntry;
import dev.texturemixer.core.texture.TextureId;
import dev.texturemixer.core.texture.TextureIndex;
import dev.texturemixer.core.texture.TextureIndexer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreTest {
    private static final int FORMAT = 46;
    private static final int RED = 0xFF0000FF;
    private static final int GREEN = 0x00FF00FF;
    private static final int BLUE = 0x0000FFFF;

    @TempDir
    Path temp;

    private Path packs() throws IOException {
        Path dir = temp.resolve("resourcepacks");
        TestPacks.pack()
                .file("pack.mcmeta", TestPacks.mcmeta(FORMAT))
                .texture("minecraft:block/stone", 16, RED)
                .texture("minecraft:item/diamond_sword", 16, RED)
                .texture("minecraft:block/water_still", 16, RED)
                .file("assets/minecraft/textures/block/water_still.png", TestPacks.png(16, 64, RED))
                .file("assets/minecraft/textures/block/water_still.png.mcmeta", "{\"animation\":{\"frametime\":2}}")
                .file("assets/minecraft/textures/block/Bad Name.png", TestPacks.png(16, 16, RED))
                .writeFolder(dir.resolve("Red Pack"));
        TestPacks.pack()
                .file("pack.mcmeta", "{\"pack\":{\"pack_format\":34,\"supported_formats\":[34,50],\"description\":{\"text\":\"Green\"}},"
                        + "\"overlays\":{\"entries\":[{\"formats\":[40,50],\"directory\":\"new\"},{\"formats\":[1,10],\"directory\":\"old\"}]}}")
                .texture("minecraft:block/stone", 32, GREEN)
                .texture("minecraft:entity/zombie/zombie", 64, GREEN)
                .file("new/assets/minecraft/textures/block/grass_block_top.png", TestPacks.png(32, 32, GREEN))
                .file("old/assets/minecraft/textures/block/dirt.png", TestPacks.png(32, 32, GREEN))
                .file("assets/minecraft/textures/block/broken.png", "not a png")
                .writeZip(dir.resolve("Green Pack.zip"));
        TestPacks.pack()
                .file("pack.mcmeta", TestPacks.mcmeta(3))
                .texture("minecraft:blocks/stone", 16, BLUE)
                .writeFolder(dir.resolve("Old Pack"));
        // Not packs, or broken ones: must be reported, not crash the scan.
        TestPacks.pack().file("Nested/pack.mcmeta", TestPacks.mcmeta(FORMAT)).writeZip(dir.resolve("Nested.zip"));
        Files.writeString(dir.resolve("Garbage.zip"), "this is not a zip");
        Files.createDirectories(dir.resolve("Empty Folder"));
        // A previously generated pack must never become a source.
        TestPacks.pack()
                .file("pack.mcmeta", "{\"pack\":{\"pack_format\":46,\"description\":\"x\"},\"texturemixer\":{\"generated\":true}}")
                .texture("minecraft:block/stone", 16, BLUE)
                .writeFolder(dir.resolve(TextureMixerConstants.GENERATED_PACK_NAME));
        return dir;
    }

    private TextureIndex index(Path dir) {
        ScanResult scan = PackScanner.scan(dir);
        return new TextureIndexer(FORMAT, (done, total, name) -> { }, () -> false)
                .build(scan.packs(), scan.problems(), PackScanner.fingerprint(dir));
    }

    @Test
    void scansFoldersAndZipsAndReportsBrokenPacks() throws IOException {
        Path dir = packs();
        try (TextureIndex index = index(dir)) {
            assertEquals(List.of("file/Green Pack.zip", "file/Old Pack", "file/Red Pack"),
                    index.sources().stream().map(s -> s.id()).toList());

            TextureEntry stone = index.get(TextureId.parse("minecraft:block/stone"));
            assertNotNull(stone);
            assertEquals(2, stone.packCount());
            assertEquals(32, stone.variant("file/Green Pack.zip").width());
            assertEquals(TextureCategory.BLOCKS, stone.category());

            TextureEntry water = index.get(TextureId.parse("minecraft:block/water_still"));
            assertTrue(water.variant("file/Red Pack").animated());
            assertEquals(16, water.variant("file/Red Pack").frameHeight());

            assertEquals(TextureCategory.ENTITIES, index.get(TextureId.parse("minecraft:entity/zombie/zombie")).category());
            assertEquals(TextureCategory.ITEMS, index.get(TextureId.parse("minecraft:item/diamond_sword")).category());

            // Active overlay applies, inactive one does not.
            assertEquals("new/assets/minecraft/textures/block/grass_block_top.png",
                    index.get(TextureId.parse("minecraft:block/grass_block_top")).variant("file/Green Pack.zip").filePath());
            assertNull(index.get(TextureId.parse("minecraft:block/dirt")));

            assertTrue(index.get(TextureId.parse("minecraft:blocks/stone")).variant("file/Old Pack").legacyPath());
            assertNull(index.get(TextureId.parse("minecraft:block/broken")));

            String problems = index.problems().toString();
            assertTrue(problems.contains("Nested.zip") && problems.contains("inside the folder"), problems);
            assertTrue(problems.contains("Garbage.zip"), problems);
            assertTrue(problems.contains("Empty Folder"), problems);
            assertTrue(problems.contains("not valid PNG"), problems);
            assertTrue(problems.contains("names Minecraft does not accept"), problems);
            assertTrue(problems.contains("pre-1.13"), problems);
            assertTrue(index.problems().stream().anyMatch(p -> p.packName().equals("Old Pack") && p.severity() == PackProblem.Severity.WARNING));
        }
    }

    @Test
    void zipEntriesEscapingThePackAreIgnored() throws IOException {
        Path dir = temp.resolve("resourcepacks");
        Files.createDirectories(dir);
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(dir.resolve("Evil.zip")))) {
            out.putNextEntry(new ZipEntry("pack.mcmeta"));
            out.write(TestPacks.mcmeta(FORMAT).getBytes(StandardCharsets.UTF_8));
            out.putNextEntry(new ZipEntry("../../assets/minecraft/textures/block/stone.png"));
            out.write(TestPacks.png(16, 16, RED));
            out.putNextEntry(new ZipEntry("assets/minecraft/textures/block/dirt.png"));
            out.write(TestPacks.png(16, 16, RED));
        }
        try (TextureIndex index = index(dir)) {
            assertNull(index.get(TextureId.parse("minecraft:block/stone")));
            assertNotNull(index.get(TextureId.parse("minecraft:block/dirt")));
            assertTrue(index.problems().toString().contains("unsafe path"));
        }
    }

    @Test
    void safePaths() {
        assertNull(SafePaths.normalize("../x"));
        assertNull(SafePaths.normalize("/etc/passwd"));
        assertNull(SafePaths.normalize("C:\\x"));
        assertNull(SafePaths.normalize("a/../../b"));
        assertEquals("a/b/c.png", SafePaths.normalize("a\\b/./c.png"));
        assertFalse(SafePaths.isValidResourcePath("Block/stone"));
        assertTrue(SafePaths.isValidResourcePath("block/stone_1.png"));
    }

    @Test
    void pngHeader() {
        PngInfo info = PngInfo.parse(TestPacks.png(16, 48, RED));
        assertEquals(new PngInfo(16, 48), info);
        assertTrue(info.looksLikeAnimationStrip());
        assertNull(PngInfo.parse("GIF89a............................".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    void metadataFormats() {
        PackMetadata modern = PackMetadata.parse("{\"pack\":{\"min_format\":[65,0],\"max_format\":70,\"description\":[\"a\",{\"text\":\"b\"}]}}");
        assertTrue(modern.supports(66));
        assertFalse(modern.supports(64));
        assertEquals("ab", modern.description());
        PackMetadata ranged = PackMetadata.parse("{\"pack\":{\"pack_format\":15,\"supported_formats\":{\"min_inclusive\":15,\"max_inclusive\":46}}}");
        assertTrue(ranged.supports(46));
        assertNotNull(PackMetadata.parse("{oops").parseProblem());
        assertNotNull(PackMetadata.parse("{}").parseProblem());
    }

    @Test
    void generatesFolderPackWithMetadataAndSkipsMissingSources() throws IOException {
        Path dir = packs();
        try (TextureIndex index = index(dir)) {
            Selections selections = Selections.EMPTY
                    .with(TextureId.parse("minecraft:block/stone"), "file/Green Pack.zip")
                    .with(TextureId.parse("minecraft:block/water_still"), "file/Red Pack")
                    .with(TextureId.parse("minecraft:block/grass_block_top"), "file/Green Pack.zip")
                    .with(TextureId.parse("minecraft:item/diamond_sword"), "file/Removed Pack.zip");
            Path target = dir.resolve(TextureMixerConstants.GENERATED_PACK_NAME);
            GenerationResult result;
            try (DirectoryPackWriter writer = new DirectoryPackWriter(target, temp.resolve("work"))) {
                result = PackGenerator.generate(selections, index, writer, new GenerationOptions(FORMAT, "Default", "1.21.4", null));
            }
            assertEquals(3, result.written());
            assertEquals(1, result.skipped().size());
            assertTrue(result.skipped().get(0).contains("Removed Pack.zip"));

            assertArrayEquals(TestPacks.png(32, 32, GREEN), Files.readAllBytes(target.resolve("assets/minecraft/textures/block/stone.png")));
            // Overlay texture is written to the normal location.
            assertArrayEquals(TestPacks.png(32, 32, GREEN), Files.readAllBytes(target.resolve("assets/minecraft/textures/block/grass_block_top.png")));
            assertTrue(Files.exists(target.resolve("assets/minecraft/textures/block/water_still.png.mcmeta")));
            assertFalse(Files.exists(target.resolve("assets/minecraft/textures/item/diamond_sword.png")));
            String mcmeta = Files.readString(target.resolve("pack.mcmeta"));
            assertTrue(mcmeta.contains("\"pack_format\": 46") && mcmeta.contains("texturemixer"), mcmeta);
            assertTrue(DirectoryPackWriter.isGeneratedPack(target));

            // Source packs are untouched.
            assertArrayEquals(TestPacks.png(16, 16, RED), Files.readAllBytes(dir.resolve("Red Pack/assets/minecraft/textures/block/stone.png")));

            // Regenerating replaces the old pack completely.
            try (DirectoryPackWriter writer = new DirectoryPackWriter(target, temp.resolve("work"))) {
                PackGenerator.generate(Selections.EMPTY.with(TextureId.parse("minecraft:item/diamond_sword"), "file/Red Pack"),
                        index, writer, new GenerationOptions(FORMAT, "Other", "1.21.4", null));
            }
            assertFalse(Files.exists(target.resolve("assets/minecraft/textures/block/stone.png")));
            assertTrue(Files.exists(target.resolve("assets/minecraft/textures/item/diamond_sword.png")));
            assertFalse(Files.exists(temp.resolve("work/staging")));
        }
    }

    @Test
    void refusesToReplaceAPackItDidNotCreate() throws IOException {
        Path dir = temp.resolve("resourcepacks");
        TestPacks.pack().file("pack.mcmeta", TestPacks.mcmeta(FORMAT))
                .writeFolder(dir.resolve(TextureMixerConstants.GENERATED_PACK_NAME));
        IOException error = assertThrows(IOException.class,
                () -> new DirectoryPackWriter(dir.resolve(TextureMixerConstants.GENERATED_PACK_NAME), temp.resolve("work")));
        assertTrue(error.getMessage().contains("not created by Texture Mixer"));
    }

    @Test
    void exportsZip() throws IOException {
        Path dir = packs();
        try (TextureIndex index = index(dir)) {
            Path zip = temp.resolve("exports/out.zip");
            try (ZipPackWriter writer = new ZipPackWriter(zip)) {
                PackGenerator.generate(Selections.EMPTY.with(TextureId.parse("minecraft:block/stone"), "file/Red Pack"),
                        index, writer, new GenerationOptions(FORMAT, "Default", "1.21.4", TestPacks.png(4, 4, RED)));
            }
            try (ZipFile file = new ZipFile(zip.toFile())) {
                assertNotNull(file.getEntry("pack.mcmeta"));
                assertNotNull(file.getEntry("pack.png"));
                assertNotNull(file.getEntry("assets/minecraft/textures/block/stone.png"));
            }
            assertFalse(Files.exists(temp.resolve("exports/out.zip.partial")));
        }
    }

    @Test
    void profilesRoundTripAndSurviveCorruption() throws IOException {
        Path file = temp.resolve("config/profiles.json");
        ProfileStore store = ProfileStore.load(file);
        assertEquals(List.of("Default"), store.names());
        store.put("Default", Selections.EMPTY.with(TextureId.parse("minecraft:block/stone"), "file/A.zip"));
        assertNull(store.create("Night", Selections.EMPTY.with(TextureId.parse("minecraft:block/dirt"), "vanilla")));
        assertNotNull(store.create("night", Selections.EMPTY));
        assertNotNull(store.create(" ", Selections.EMPTY));
        store.setActive("Night");
        store.save();

        ProfileStore loaded = ProfileStore.load(file);
        assertEquals(List.of("Default", "Night"), loaded.names());
        assertEquals("Night", loaded.active());
        assertEquals("vanilla", loaded.activeSelections().get(TextureId.parse("minecraft:block/dirt")));
        assertEquals("file/A.zip", loaded.get("Default").get(TextureId.parse("minecraft:block/stone")));

        assertNull(loaded.rename("Night", "Day"));
        assertEquals("Day", loaded.active());
        assertNull(loaded.delete("Day"));
        assertNotNull(loaded.delete("Default"));

        Files.writeString(file, "{ broken");
        ProfileStore recovered = ProfileStore.load(file);
        assertEquals(List.of("Default"), recovered.names());
        assertNotNull(recovered.loadProblem());
        try (var files = Files.list(file.getParent())) {
            assertTrue(files.anyMatch(p -> p.getFileName().toString().startsWith("profiles.json.corrupt-")));
        }
    }

    @Test
    void sessionUndoRedoFilterRandomize() throws IOException {
        Path dir = packs();
        try (TextureIndex index = index(dir)) {
            MixerSession session = new MixerSession(ProfileStore.load(temp.resolve("p.json")), new Random(1));
            session.setIndex(index);
            session.setEnabledPackIds(List.of("file/Green Pack.zip", "file/Red Pack", TextureMixerConstants.GENERATED_PACK_ID));

            TextureId stone = TextureId.parse("minecraft:block/stone");
            TextureEntry stoneEntry = index.get(stone);
            assertEquals("file/Red Pack", session.currentSource(stoneEntry));

            session.choose(stone, "file/Green Pack.zip");
            assertEquals("file/Green Pack.zip", session.effectiveSource(stoneEntry));
            session.choose(stone, null);
            session.undo();
            assertEquals("file/Green Pack.zip", session.choice(stone));
            session.redo();
            assertNull(session.choice(stone));
            session.undo();

            session.setCategory(TextureCategory.ITEMS);
            assertEquals(List.of(TextureId.parse("minecraft:item/diamond_sword")), session.visible().stream().map(TextureEntry::id).toList());
            session.setCategory(null);
            session.setQuery("green zombie");
            assertEquals(1, session.visible().size());
            session.setQuery("");
            session.setOnlyChanged(true);
            assertEquals(1, session.visible().size());
            session.setOnlyChanged(false);

            session.randomizeVisible();
            assertTrue(session.canUndo());
            session.resetAll();
            assertTrue(session.selections().isEmpty());
            session.undo();
            assertFalse(session.selections().isEmpty());

            assertNull(session.createProfile("Second", false));
            assertEquals("Second", session.activeProfile());
            assertTrue(session.selections().isEmpty());
            session.switchProfile("Default");
            assertFalse(session.selections().isEmpty());
        }
    }

    @Test
    void textureIds() {
        TextureId id = TextureId.fromAssetPath("assets/minecraft/textures/block/stone.png");
        assertEquals(TextureId.parse("minecraft:block/stone"), id);
        assertEquals("assets/minecraft/textures/block/stone.png", id.assetPath());
        assertNull(TextureId.fromAssetPath("assets/minecraft/models/block/stone.json"));
        assertNull(TextureId.fromAssetPath("assets/minecraft/textures/block/Stone.png"));
        assertEquals(TextureCategory.ARMOR, TextureCategory.classify(TextureId.parse("minecraft:entity/equipment/humanoid/diamond")));
        assertEquals(TextureCategory.ARMOR, TextureCategory.classify(TextureId.parse("minecraft:models/armor/diamond_layer_1")));
        assertEquals(TextureCategory.GUI, TextureCategory.classify(TextureId.parse("minecraft:gui/sprites/hud/heart/full")));
        assertEquals(TextureCategory.PARTICLES, TextureCategory.classify(TextureId.parse("minecraft:particle/flame")));
        assertEquals(TextureCategory.OTHER, TextureCategory.classify(TextureId.parse("minecraft:environment/sun")));
        assertTrue(Arrays.asList(TextureCategory.values()).contains(TextureCategory.OTHER));
    }
}
