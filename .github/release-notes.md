## Texture Mixer

Mix individual textures from all your installed resource packs into one custom pack,
from **Options → Resource Packs → Mix Textures**.

### New in 0.2.0

- **Use \<pack\> for all listed**: pick one pack for a whole category or search result in
  one step, then override single textures.
- Keyboard control: arrow keys, 1-9 to pick a version, Delete to reset, Ctrl+S to apply.
- Leaving with unapplied picks asks whether to apply them first.
- Hover cards with a large preview; animated textures play in the version list.
- Packs added to the folder while the screen is open appear by themselves.
- Programmer Art, High Contrast and the textures inside mods are sources too.
- Much faster rescans (only changed packs are read again), packs are read in parallel,
  and previews use far less video memory.
- Translations: Dutch, German, French, Spanish.

### Which file do I download?

Pick the jar whose Minecraft range contains your version, and install it in
`.minecraft/mods` together with [Fabric API](https://modrinth.com/mod/fabric-api).

| Your Minecraft      | Download                                  | Tested in game in CI |
|---------------------|-------------------------------------------|----------------------|
| 1.21.11             | `texture-mixer-fabric-*+mc1.21.11.jar`    | yes |
| 1.21.9 - 1.21.10    | `texture-mixer-fabric-*+mc1.21.10.jar`    | yes (on 1.21.10) |
| 1.21.6 - 1.21.8     | `texture-mixer-fabric-*+mc1.21.8.jar`     | yes (on 1.21.8) |
| 1.21.5              | `texture-mixer-fabric-*+mc1.21.5.jar`     | yes |
| 1.21.4              | `texture-mixer-fabric-*+mc1.21.4.jar`     | yes |
| 1.21.2 - 1.21.3     | `texture-mixer-fabric-*+mc1.21.3.jar`     | no (compiled only) |
| 1.21 - 1.21.1       | `texture-mixer-fabric-*+mc1.21.1.jar`     | no (compiled only) |
| 1.20.5 - 1.20.6     | `texture-mixer-fabric-*+mc1.20.6.jar`     | no (compiled only) |
| 1.20.2 - 1.20.4     | `texture-mixer-fabric-*+mc1.20.4.jar`     | no (compiled only) |
| 1.20 - 1.20.1       | `texture-mixer-fabric-*+mc1.20.1.jar`     | no (compiled only) |

Fabric's in-game test API only exists from 1.21.4 on, so the older jars were compiled
against their Minecraft version but have not been started in a real game yet. Please
report problems.

### Known limitations

- Only textures are mixed; models, blockstates, sounds and fonts are not copied.
- OptiFine / Continuity features (CTM, CIT, emissive) are ignored.
- Pre-1.13 packs are listed but their textures can't be picked.

See the README for details.
