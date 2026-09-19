plugins { id("fabric-loom") }
apply(from = "../version-common.gradle.kts")

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    // 26.x ships unobfuscated (fabric.loom.disableObfuscation=true in
    // gradle.properties): no mappings, plain implementation.
    implementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_version")}")
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}
tasks.withType<JavaCompile>().configureEach { options.release.set(25) }
