plugins { id("fabric-loom") }
apply(from = "../version-common.gradle.kts")

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_version")}")
}

tasks.withType<JavaCompile>().configureEach { options.release.set(21) }
