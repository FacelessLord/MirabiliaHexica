# Mirabilia Hexica

An addon for [Hex Casting](https://github.com/FallingColors/HexMod) about the things magic leaves behind, heavily
inspired by *Ra* by qntm. Minecraft 1.20.1, Fabric and Forge. Made with heavy help from an AI coding assistant.

Player-facing description: [MODRINTH.md](MODRINTH.md). Design notes: [DESIGN.md](DESIGN.md). Releasing:
[PUBLISHING.md](PUBLISHING.md). MIT ([LICENSE](LICENSE)); adapted third-party code in [CREDITS.md](CREDITS.md).

## Project layout

| Folder    | What goes there                                                              |
|-----------|------------------------------------------------------------------------------|
| `common/` | Almost all code and assets: patterns, items, lang, book entries.             |
| `fabric/` | Fabric entrypoint and Fabric-only glue (`fabric.mod.json`).                  |
| `forge/`  | Forge entrypoint and Forge-only glue (`META-INF/mods.toml`).                 |

Dependency versions are all in `gradle/libs.versions.toml`.

## Opening in IntelliJ IDEA

1. Install the **Minecraft Development** plugin (optional, but it helps with mixins and access wideners).
2. **File → Open…**, select this folder (or `build.gradle.kts`), choose **Open as Project**.
3. Set **Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JVM** to a **JDK 21**
   (Architectury Loom 1.11 needs it to run). The mod itself is still compiled for Java 17, the version
   Minecraft 1.20.1 uses; Gradle picks up the installed JDK 17 for that automatically.
4. Wait for the first Gradle sync. It downloads and decompiles Minecraft, which takes several minutes.
5. Run configurations appear automatically: **Minecraft Client (:fabric)**, **Minecraft Client (:forge)**,
   and the matching servers. If they are missing, run the Gradle sync again.

## Command line

`JAVA_HOME` must point to a JDK 21.

```
gradlew build              # jars in fabric/build/libs and forge/build/libs
gradlew :fabric:runClient
gradlew :forge:runClient
gradlew :fabric:runGametest   # automated in-game tests; prints "All N required tests passed"
```

The GameTests (in `fabric/src/gametest`) cast the mod's real patterns through Hex Casting's VM on a headless
server and check the results. They are never included in the released jars.
