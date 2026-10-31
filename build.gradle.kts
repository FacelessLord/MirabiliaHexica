import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    alias(libs.plugins.architectury)
    alias(libs.plugins.architectury.loom) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.shadow) apply false
    alias(libs.plugins.minotaur) apply false
}

val mavenGroup: String by project
val modVersion: String by project
val javaVersion = libs.versions.java.get().toInt()
val minecraftVersion = libs.versions.minecraft.get()

architectury {
    minecraft = minecraftVersion
}

// Settings shared by common, fabric and forge. Loader-specific setup lives in each subproject.
subprojects {
    group = mavenGroup
    version = "$modVersion+$minecraftVersion"

    repositories {
        mavenCentral()
        maven("https://maven.blamejared.com") // Hex Casting, PAUCAL, Patchouli, Inline
        maven("https://maven.parchmentmc.org")
        maven("https://maven.architectury.dev/")
        maven("https://maven.fabricmc.net/")
        maven("https://maven.minecraftforge.net/")
        maven("https://maven.shedaniel.me") // Cloth Config
        maven("https://maven.ladysnake.org/releases") // Cardinal Components
        maven("https://maven.terraformersmc.com/releases") // Trinkets
        maven("https://maven.theillusivec4.top") // Caelus
        maven("https://maven.hexxy.media") // Serialization Hooks
        maven("https://thedarkcolour.github.io/KotlinForForge") {
            content { includeGroup("thedarkcolour") }
        }
        exclusiveContent {
            forRepository { maven("https://api.modrinth.com/maven") }
            filter { includeGroup("maven.modrinth") }
        }
    }

    plugins.withId("java") {
        extensions.configure<JavaPluginExtension> {
            toolchain.languageVersion = JavaLanguageVersion.of(javaVersion)
            withSourcesJar()
        }
        tasks.withType<JavaCompile>().configureEach {
            options.encoding = "UTF-8"
            options.release = javaVersion
        }
        tasks.withType<Jar>().configureEach {
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        }
    }

    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<KotlinJvmProjectExtension> {
            jvmToolchain(javaVersion)
        }
    }
}
