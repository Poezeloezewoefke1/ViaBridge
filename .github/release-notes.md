## Texture Mixer

Mix individual textures from all your installed resource packs into one custom pack,
from **Options → Resource Packs → Mix Textures**.

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
