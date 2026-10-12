# Texture Mixer

A client-side **Fabric** mod for Minecraft Java Edition. It lets you pick individual
textures from any of your installed resource packs and combine them into one custom
pack, from the normal **Options → Resource Packs** screen. You don't edit files by hand
or use outside tools.

```
Grass block   -> Resource Pack A
Stone         -> Resource Pack B
Diamond sword -> Resource Pack C
Zombie        -> Resource Pack D
Dirt          -> Minecraft (default)
```

The source packs do **not** need to be enabled. Texture Mixer only reads them; it never
changes or overwrites them.

## Supported versions

| Minecraft      | Folder            | Status |
|----------------|-------------------|--------|
| 1.21.11        | `fabric/1.21.11`  | Built, and tested in game in CI |
| 1.21.4         | `fabric/1.21.4`   | Built, and tested in game in CI |
| 1.21 - 1.21.1  | `fabric/1.21.1`   | Built in CI; not started in CI (see below) |
| 1.20.1         | `fabric/1.20.1`   | Built in CI; not started in CI (see below) |

Each Minecraft version gets its own jar. Fabric's in-game test API only exists from
1.21.4 on, so CI can't start the 1.21.1 and 1.20.1 builds. Those two compile against
their own Minecraft version and run the same shared code that is tested on 1.21.4 and
1.21.11, but nobody has started them in a real game yet. Versions in between (1.20.2 -
1.20.6, 1.21.2 - 1.21.3, 1.21.5 - 1.21.10) and 26.1 have no build yet. See "Porting to
another version"; most of them only need a new `VersionCompat`.

Requires [Fabric Loader](https://fabricmc.net/use/) and [Fabric API](https://modrinth.com/mod/fabric-api).

## Using it

1. Put resource packs (folders or `.zip` files) in `.minecraft/resourcepacks` as usual.
   You don't need to enable them.
2. Open **Options → Resource Packs** and press **Mix Textures** (top right corner).
3. Texture Mixer scans every pack in the background. The game keeps running while it
   does.
4. Browse the grid. You can search by texture name or pack name, use the category tabs
   (Blocks, Items, Entities, Armor, GUI, Particles, Other), and switch **Show:**
   between textures packs change, your own picks, or every texture.
5. Click a texture. The right panel lists every version of it with a preview:
   - **Keep unchanged**: whatever your enabled packs (or Minecraft) show today.
   - **Minecraft (default)**: forces the vanilla texture, even over your enabled packs.
   - each pack that has the texture, with its resolution and an "animated" tag.

   Click one to pick it. Right-click a texture in the grid to set it back to
   "unchanged". A green corner mark shows a pack pick; a gold mark shows a Minecraft
   default pick.
6. Press **Apply**. Texture Mixer writes `Texture Mixer - Custom Pack` into the
   resourcepacks folder and enables it above your other packs. Then it reloads
   resources. Your other packs, and the order you put them in, stay as they are.

Other buttons:

- **Undo / Redo** (Ctrl+Z, Ctrl+Y or Ctrl+Shift+Z) for every change, including Reset and
  Randomize.
- **Reset All** clears every pick in the current profile.
- **Randomize** picks a random version for every texture currently listed. Use the
  search and tabs to limit what it touches.
- **Export** saves the current profile as a normal `.zip` resource pack in
  `.minecraft/texturemixer/exports/`.
- **Profile: …** opens the profile list, where you can switch, create, duplicate, rename
  or delete profiles. Each profile keeps its own picks. Picks are saved automatically to
  `config/texturemixer/profiles.json`.
- **N problem(s)** shows up when a pack could not be read, or may not look right. It
  says why for each pack (broken ZIP, `pack.mcmeta` in a subfolder, made for another
  version, files Minecraft rejects, ...).
- **F5** rescans the resourcepacks folder. It is also rescanned automatically whenever
  its contents change.

## What it handles

- Folder packs and ZIP packs, enabled or not.
- `.png.mcmeta` files: animations, blur and clamp settings are copied along with the
  texture.
- Pack overlays (1.20.2+): files from overlay folders that apply to the running version
  replace the base files, the same way Minecraft stacks them.
- `pack.mcmeta` in all its forms: `pack_format`, `supported_formats`, and
  `min_format`/`max_format`.
- Malformed packs: broken ZIPs, ZIP entries with `..` or absolute paths, names
  Minecraft rejects, non-PNG data, oversized files. These are skipped and listed as
  problems. They never crash the scan.
- Previews load on background threads, only for textures on screen, and each one is
  decoded once. GPU memory for previews is capped and released when the screen closes.
- Safety: the generated pack is the only thing Texture Mixer ever replaces. It carries
  a marker in its `pack.mcmeta`. If a pack with the same name exists without the marker,
  Apply refuses and says so. The new pack is first built in `.minecraft/texturemixer/`
  and then moved into place, so Minecraft never sees a half-written pack.

## Current limitations

These are not supported yet. The mod does not pretend otherwise:

- **Textures only.** Models, blockstates, sounds, fonts, shaders and language files are
  not copied. If a pack's custom *model* depends on its own extra texture files, picking
  only the texture gives you that texture on the **vanilla** model.
- **OptiFine / Continuity features** (CTM, CIT, emissive textures, `optifine/` folders)
  are ignored. Only files under `assets/<namespace>/textures/` are offered.
- **Pre-1.13 packs**: their `blocks/` and `items/` folders are listed but flagged as old
  names. They can't be picked because this Minecraft version would ignore them. No
  automatic renaming is done.
- **Previews show the first animation frame.** The animation itself plays in game.
- **Textures from other mods' built-in resources** aren't offered as sources yet. Only
  packs in the resourcepacks folder and Minecraft's own textures are.
- **Exported ZIPs** contain textures made by other people. Check each pack's license
  before sharing an export. A `texturemixer-sources.json` inside the pack records which
  pack every texture came from.

## Building

Requirements: JDK 21 and internet access to `maven.fabricmc.net`, Mojang's servers and
Maven Central. The Gradle wrapper (Gradle 8.14) and Fabric Loom 1.13 download everything
else.

```sh
./gradlew build                      # core + every Minecraft version
./gradlew build -Pmc=1.21.4          # core + one version
./gradlew -PcoreOnly :core:test      # only the version-independent core (no Minecraft download)
```

The mod jar ends up in `fabric/<version>/build/libs/texture-mixer-fabric-<mod version>+mc<version>.jar`
(ignore the `-sources` jar). Copy it into `.minecraft/mods` next to Fabric API.

To try it in a development client: `./gradlew :fabric-1.21.4:runClient`.

Every push builds the jars on GitHub Actions and attaches them to the run as artifacts.
Pushing a `v*` tag also publishes them as a GitHub release.

## Testing

- `core/src/test`: unit tests for scanning (folder, ZIP, broken, nested and hostile
  ZIPs, overlays), indexing, pack generation (folder and ZIP), refusing to overwrite
  foreign packs, profiles (including a corrupt file), undo/redo, filtering and
  randomize.
- `fabric/1.21.4/src/gametest` and `fabric/1.21.11/src/gametest`: a Fabric client game
  test that starts the real game under a virtual display. It installs test packs, opens the vanilla Resource Packs
  screen, presses **Mix Textures**, picks textures, presses **Apply**, and then checks
  that Minecraft actually loads those textures from the generated pack. It also covers
  undo/redo, profiles and export, and checks that closing the pack screen keeps the
  generated pack enabled. Screenshots of each step are uploaded as CI artifacts. Run
  locally with `./gradlew :fabric-1.21.4:runClientGameTest` (needs a display).

## Project layout

```
core/                       Plain Java 17, no Minecraft classes. Unit tested.
  pack/                     Pack sources (folder, ZIP), pack.mcmeta parsing, scanning, path safety
  texture/                  Texture ids, categories, PNG header reading, the texture index
  selection/                Immutable selections + undo/redo history
  profile/                  Named profiles, JSON persistence
  generate/                 Pack generation, folder and ZIP writers
  session/                  MixerSession: everything the GUI shows and edits
fabric/common/              Minecraft code shared by every version (compiled into each one)
  TextureMixerClient        Entry point; adds the button to the Resource Packs screen
  TextureMixerManager       Background scanning, Apply (activate + reload), Export
  PreviewCache              Lazy, bounded GPU textures for previews
  gui/                      Selection, profile and problem screens
  mixin/PackScreenAccessor  Read-only access to two fields of the vanilla pack screen
  pack/VanillaPackSource    Minecraft's built-in textures as a source
fabric/input-legacy/        InputScreen for 1.20 - 1.21.8 (raw mouse/key callbacks)
fabric/input-1.21.9/        InputScreen for 1.21.9+ (Click / KeyInput event objects)
fabric/<version>/           One Gradle project per Minecraft version
  gradle.properties         Minecraft, Yarn, Fabric API, Java versions
  .../compat/VersionCompat  The calls that differ between versions
  src/gametest/             In-game client test (1.21.4 and newer)
```

## Porting to another version

1. Copy the closest existing module (`fabric/1.21.4` for 1.21.2 - 1.21.8,
   `fabric/1.21.11` for 1.21.9+, `fabric/1.20.1` or `fabric/1.21.1` for older) to
   `fabric/<new version>`. `settings.gradle.kts` picks it up automatically.
2. Update its `gradle.properties` (Minecraft, Yarn, Fabric API, Java, dependency range).
   The versions that exist are listed in Fabric's Maven metadata.
3. Fix `VersionCompat` for that version: pack format, identifier creation, texture
   drawing, background rendering, enabled pack ids, dynamic textures. Point the module
   at the right input layer (`fabric/input-legacy` or `fabric/input-1.21.9`).
4. If shared code in `fabric/common` no longer compiles, move just that piece behind
   `VersionCompat` instead of forking the screen.
5. Add the version to the matrix in `.github/workflows/build.yml`.

## License

MIT, see [LICENSE](LICENSE).
