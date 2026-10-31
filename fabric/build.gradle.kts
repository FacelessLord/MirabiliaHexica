plugins {
    kotlin("jvm")
    id("architectury-plugin")
    id("dev.architectury.loom")
    id("com.github.johnrengelman.shadow")
    id("com.modrinth.minotaur")
}

val modId: String by project

base.archivesName = "$modId-fabric"

architectury {
    platformSetupLoomIde()
    fabric()
}

loom {
    silentMojangMappingsLicense()
    accessWidenerPath = project(":common").loom.accessWidenerPath
    mixin { useLegacyMixinAp = false }
}

// In-game automated tests (Minecraft GameTest framework). Kept out of the shipped jar: run with
// `gradlew :fabric:runGametest`; the server starts, runs every test and exits with the result.
val gametest: SourceSet by sourceSets.creating {
    compileClasspath += sourceSets.main.get().compileClasspath + sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().runtimeClasspath + sourceSets.main.get().output
}

loom {
    mods {
        register("mirabilia_gametest") { sourceSet(gametest) }
    }
    runs {
        register("gametest") {
            server()
            name("Minecraft GameTest")
            source(gametest)
            vmArg("-Dfabric-api.gametest")
            vmArg("-Dfabric-api.gametest.report-file=${layout.buildDirectory.file("gametest/junit.xml").get().asFile}")
            runDir("build/gametest")
        }
    }
}

val common: Configuration by configurations.creating
val shadowCommon: Configuration by configurations.creating

configurations {
    compileClasspath { extendsFrom(common) }
    runtimeClasspath { extendsFrom(common) }
    named("developmentFabric") { extendsFrom(common) }
}

dependencies {
    minecraft(libs.minecraft)
    mappings(loom.layered {
        officialMojangMappings()
        parchment(libs.parchment)
    })

    modImplementation(libs.fabric.loader)
    modApi(libs.fabric.api)
    modImplementation(libs.kotlin.fabric)
    modApi(libs.architectury.fabric) {
        exclude(group = "net.fabricmc", module = "fabric-loader")
    }

    modApi(libs.hexcasting.fabric)
    // Hex Casting's own dependencies: present in the dev client, not exposed to anyone depending on us
    modLocalRuntime(libs.paucal.fabric)
    modLocalRuntime(libs.patchouli.fabric)
    modLocalRuntime(libs.inline.fabric) { isTransitive = false }
    modLocalRuntime(libs.cardinalComponents)
    modLocalRuntime(libs.serializationHooks)
    // Trinkets injects Cardinal Components interfaces into Entity, so compiling against it needs both
    modCompileOnly(libs.trinkets)
    modCompileOnly(libs.cardinalComponents)
    modLocalRuntime(libs.trinkets)
    modLocalRuntime(libs.clothConfig.fabric) {
        exclude(group = "net.fabricmc.fabric-api")
    }

    common(project(path = ":common", configuration = "namedElements")) { isTransitive = false }
    shadowCommon(project(path = ":common", configuration = "transformProductionFabric")) { isTransitive = false }
}

sourceSets.main {
    resources.source(project(":common").sourceSets.main.get().resources)
}

tasks {
    // Start every test run from a fresh world, so saved data (e.g. standing spells) can't leak between runs
    named("runGametest") {
        doFirst { delete(layout.buildDirectory.dir("gametest/world")) }
    }

    processResources {
        // The license and third-party notices travel with the jar, as MIT asks
        from(rootProject.file("LICENSE"), rootProject.file("CREDITS.md"))
        val props = mapOf(
            "version" to project.version,
            "minecraft" to libs.versions.minecraft.get(),
            "fabricLoader" to libs.versions.fabric.loader.get(),
            "fabricApi" to libs.versions.fabric.api.get(),
            "kotlinFabric" to libs.versions.kotlin.fabric.get(),
            "architectury" to libs.versions.architectury.asProvider().get(),
            "hexcasting" to libs.versions.hexcasting.get(),
        )
        inputs.properties(props)
        filesMatching("fabric.mod.json") { expand(props) }
    }

    shadowJar {
        exclude("architectury.common.json")
        configurations = listOf(shadowCommon)
        archiveClassifier = "dev-shadow"
    }

    remapJar {
        dependsOn(shadowJar)
        inputFile = shadowJar.flatMap { it.archiveFile }
        archiveClassifier = null as String?
    }

    jar {
        archiveClassifier = "dev"
    }
}

// Publishing to Modrinth: `gradlew :fabric:modrinth` (see PUBLISHING.md)
val modrinthProject: String by project
val modrinthVersionType: String by project

modrinth {
    token.set(providers.environmentVariable("MODRINTH_TOKEN").orElse(providers.gradleProperty("modrinthToken")))
    projectId.set(modrinthProject)
    versionNumber.set("${project.version}-fabric")
    versionName.set("Mirabilia Hexica ${project.version} (Fabric)")
    versionType.set(modrinthVersionType)
    uploadFile.set(tasks.remapJar)
    gameVersions.add(libs.versions.minecraft.get())
    loaders.add("fabric")
    changelog.set(rootProject.file("CHANGELOG.md").readText())
    syncBodyFrom.set(rootProject.file("MODRINTH.md").readText())
    dependencies {
        required.project("hex-casting")
        required.project("architectury-api")
        required.project("fabric-api")
        required.project("fabric-language-kotlin")
        optional.project("trinkets")
    }
}
