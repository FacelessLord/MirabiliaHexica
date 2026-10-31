plugins {
    kotlin("jvm")
    id("architectury-plugin")
    id("dev.architectury.loom")
    id("com.github.johnrengelman.shadow")
    id("com.modrinth.minotaur")
}

val modId: String by project

base.archivesName = "$modId-forge"

architectury {
    platformSetupLoomIde()
    forge()
}

loom {
    silentMojangMappingsLicense()
    accessWidenerPath = project(":common").loom.accessWidenerPath
    mixin { useLegacyMixinAp = false }

    forge {
        convertAccessWideners = true
        extraAccessWideners.add(loom.accessWidenerPath.get().asFile.name)
        mixinConfig("mirabilia.mixins.json")
    }
}

val common: Configuration by configurations.creating
val shadowCommon: Configuration by configurations.creating

configurations {
    compileClasspath { extendsFrom(common) }
    runtimeClasspath { extendsFrom(common) }
    named("developmentForge") { extendsFrom(common) }
}

dependencies {
    minecraft(libs.minecraft)
    mappings(loom.layered {
        officialMojangMappings()
        parchment(libs.parchment)
    })

    forge(libs.forge)
    modApi(libs.architectury.forge)
    implementation(libs.kotlin.forge)

    modApi(libs.hexcasting.forge) { isTransitive = false }
    // Hex Casting's own dependencies: present in the dev client, not exposed to anyone depending on us
    modLocalRuntime(libs.paucal.forge)
    modLocalRuntime(libs.patchouli.forge)
    modLocalRuntime(libs.inline.forge) { isTransitive = false }
    modLocalRuntime(libs.caelus)
    // Optional: lets Hex's lenses (and ours) go in the Curios head slot. Dev runtime only, not a dependency
    modCompileOnly(libs.curios)
    modLocalRuntime(libs.curios)
    modLocalRuntime(libs.clothConfig.forge)
    localRuntime(libs.mixinExtras.forge)

    common(project(path = ":common", configuration = "namedElements")) { isTransitive = false }
    shadowCommon(project(path = ":common", configuration = "transformProductionForge")) { isTransitive = false }
}

sourceSets.main {
    resources.source(project(":common").sourceSets.main.get().resources)
}

tasks {
    processResources {
        // The license and third-party notices travel with the jar, as MIT asks
        from(rootProject.file("LICENSE"), rootProject.file("CREDITS.md"))
        val props = mapOf(
            "version" to project.version,
            "minecraft" to libs.versions.minecraft.get(),
            "kotlinForge" to libs.versions.kotlin.forge.get(),
            "architectury" to libs.versions.architectury.asProvider().get(),
            "hexcasting" to libs.versions.hexcasting.get(),
        )
        inputs.properties(props)
        filesMatching("META-INF/mods.toml") { expand(props) }
    }

    shadowJar {
        exclude("fabric.mod.json", "architectury.common.json")
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

// Publishing to Modrinth: `gradlew :forge:modrinth` (see PUBLISHING.md)
val modrinthProject: String by project
val modrinthVersionType: String by project

modrinth {
    token.set(providers.environmentVariable("MODRINTH_TOKEN").orElse(providers.gradleProperty("modrinthToken")))
    projectId.set(modrinthProject)
    versionNumber.set("${project.version}-forge")
    versionName.set("Mirabilia Hexica ${project.version} (Forge)")
    versionType.set(modrinthVersionType)
    uploadFile.set(tasks.remapJar)
    gameVersions.add(libs.versions.minecraft.get())
    loaders.add("forge")
    changelog.set(rootProject.file("CHANGELOG.md").readText())
    syncBodyFrom.set(rootProject.file("MODRINTH.md").readText())
    dependencies {
        required.project("hex-casting")
        required.project("architectury-api")
        required.project("kotlin-for-forge")
        optional.project("curios")
    }
}
