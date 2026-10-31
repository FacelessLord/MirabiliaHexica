// Loader-independent code. Compiled against Hex Casting's common jar with Mojang mappings;
// it must not touch Fabric or Forge classes (except Fabric's @Environment annotation).

plugins {
    kotlin("jvm")
    id("architectury-plugin")
    id("dev.architectury.loom")
}

val modId: String by project

base.archivesName = "$modId-common"

architectury {
    common("fabric", "forge")
}

loom {
    silentMojangMappingsLicense()
    accessWidenerPath = file("src/main/resources/$modId.accesswidener")
    // Remap mixin targets inside the classes when building jars, instead of shipping a refmap
    mixin { useLegacyMixinAp = false }
}

dependencies {
    minecraft(libs.minecraft)
    mappings(loom.layered {
        officialMojangMappings()
        parchment(libs.parchment)
    })

    implementation(libs.kotlin.stdlib)

    // Only for the @Environment annotation and mixin classes; don't use other Fabric Loader classes here
    modImplementation(libs.fabric.loader)
    modApi(libs.architectury)

    modApi(libs.hexcasting.common)
    // Only for PatchouliAPI (book flags); Hex Casting brings Patchouli itself at runtime
    modCompileOnly(libs.patchouli.xplat)
}
