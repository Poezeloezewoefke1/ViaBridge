# ViaBridge

A client-side Fabric mod for **Minecraft 1.21.4** that lets you join **1.21.11** servers.
Nothing is installed on the server, and no separate ViaVersion plugin is needed — the
translation layer is bundled inside the mod jar.

```
1.21.4 client  (protocol 769)
      |
      |  ViaBridge splices Via's packet translators into the connection
      v
1.21.11 server (protocol 774)
```

## What this actually does

The client is told to introduce itself as a 1.21.11 client, and every packet in both
directions is rewritten between 774 and 769 on the fly.

The rewriting itself is done by **ViaBackwards**, which is the library that handles the
older-client-to-newer-server direction (ViaVersion core handles the opposite direction).
Both libraries are bundled into the output jar via jar-in-jar, so the built mod is
self-contained.

What ViaBridge supplies is the part that does not otherwise exist for 1.21.4: a host
platform for those libraries, wired into the client's networking.

| Piece | File |
|---|---|
| Mod entrypoint, Via manager bootstrap | `ViaBridge.java` |
| Via's view of the host | `platform/BridgePlatform.java` |
| Reported translatable version range | `platform/BridgeInjector.java` |
| Per-connection target version choice | `platform/BridgeVersionProvider.java` |
| Netty pipeline splicing | `net/PipelineInjector.java` |
| Compression reordering | `net/BridgeDecodeHandler.java` |
| Mapping-free server version probe | `net/StatusPinger.java` |
| The one hook into the game | `mixin/MixinConnection.java` |

Two design choices are worth calling out, both aimed at not breaking on the next patch
release:

- **The mixin anchors on Netty, not Minecraft.** `Connection.channelActive` is inherited
  from Netty's `ChannelInboundHandlerAdapter`, so its descriptor is library-owned and is
  not affected by Minecraft remapping. Minecraft's own networking methods change signature
  across the 1.21.x line, which makes them a poor anchor.
- **The version probe uses no Minecraft classes.** `StatusPinger` speaks the Server List
  Ping protocol from raw bytes. That handshake has been stable since 1.7, so one
  implementation covers every server version and cannot break on a mapping change.

## Building

Requires JDK 21 and network access to `maven.fabricmc.net`, `repo.viaversion.com`,
`libraries.minecraft.net` and `piston-meta.mojang.com`.

```bash
./gradlew build
```

The jar lands in `build/libs/viabridge-1.0.0.jar`. Install it alongside **Fabric API**.

To run the client from source: `./gradlew runClient`.

## Configuration

`config/viabridge/viabridge.properties`, created on first launch:

| Key | Default | Meaning |
|---|---|---|
| `enabled` | `true` | Master switch. When off, connections are untouched. |
| `target-version` | `1.21.11` | Version assumed when auto-detection is off or fails. Accepts a name or a raw protocol number (`774`). |
| `auto-detect` | `true` | Ping the server for its real version before connecting. Lets one install handle a mixed server list. |
| `detect-timeout-ms` | `3000` | How long connecting waits for that ping before falling back to `target-version`. |

Via's own tunables live separately in `config/viabridge/viaversion.yml`.

## Status and limitations

**Builds clean; not yet run in-game.** CI compiles the mod against Minecraft 1.21.4 and
asserts that the produced jar bundles both Via libraries, that the metadata provides
`viafabric`, and that the mod's own classes are present. That covers compilation, mixin
target resolution and packaging.

What it does not cover is a real session against a live 1.21.11 server. **The first launch
should still be treated as unverified** — start the client, check the log for the
`ViaBridge ready` line, and join a server.

Beyond that, some limits are inherent to cross-version play rather than bugs here:

- Content that exists only in the newer version cannot be shown by an older client.
  Blocks, items, entities and particles added after 1.21.4 will be approximated or
  substituted, because 1.21.4 has no way to render them.
- 1.21.9 introduced the atlas text component and reworked HUD elements; these degrade
  rather than translate exactly.
- Anti-cheat systems may object to the client's movement or timing after translation.
- Servers that require the client to be genuinely 1.21.11 (custom plugin channels,
  resource-pack gating, some login plugins) may still reject the connection.

Keep `viaversion_version` and `viabackwards_version` in `gradle.properties` on the same
release line, and bump both when Via publishes support for a newer server version. Note
that `5.11.1` exists only as a rolling `-SNAPSHOT`, never as a release — pin releases.

## Licensing

ViaVersion and ViaBackwards are GPL-3.0. Because this mod bundles and links against them,
it is distributed under GPL-3.0-or-later as well.
