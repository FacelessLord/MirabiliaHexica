# Publishing to Modrinth

Uploads use [Minotaur](https://github.com/modrinth/minotaur), Modrinth's Gradle plugin, set up in `fabric/` and
`forge/`. Each loader is its own Modrinth version of the same project.

## Once

1. Create the project on modrinth.com (Minotaur uploads versions, it can't create projects). Slug:
   `mirabilia` (or change `modrinthProject` in `gradle.properties`). Upload `icon_512.png` as its icon.
   Summary, for the field under the name:

   > A dream world from Ra, ambient media and its machines, imbued lenses, armor and tools, macros and more for Hex Casting. Almost everything is configurable.

2. Make a personal access token at modrinth.com → Settings → PATs, with the scopes **Create versions**,
   **Read projects** (to find the project while it's still a draft or under review) and **Write projects** (only
   for syncing the description). Nothing else. Give it an expiry date.
3. Give it to Gradle without putting it in the repository, either way:
   - environment variable: `MODRINTH_TOKEN=...`
   - or a line in your user-level `%USERPROFILE%\.gradle\gradle.properties`: `modrinthToken=...`

## Each release

1. Set `modVersion` in `gradle.properties` (and `modrinthVersionType`: `alpha`, `beta` or `release`).
2. Write what changed in `CHANGELOG.md`; it becomes both versions' changelog.
3. Run, with `JAVA_HOME` pointing at a JDK 21:

```
gradlew build
gradlew :fabric:modrinth :forge:modrinth
```

Versions come out as `<modVersion>+1.20.1-fabric` and `-forge`, with their dependencies (Hex Casting,
Architectury API, the loader's Kotlin and Fabric API; Trinkets / Curios optional) already filled in.

To update the project's description from `MODRINTH.md`: `gradlew :fabric:modrinthSyncBody`.
