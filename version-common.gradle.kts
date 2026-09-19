// Applied by every version subproject via `apply(from = "../version-common.gradle.kts")`.
// Expects the subproject to have already applied the fabric-loom plugin and to
// carry minecraft_version / loader_version / fabric_version in its gradle.properties.
val mcVersion = project.property("minecraft_version") as String
val srcRoot = (project.findProperty("src_root") as String?) ?: "common"
val nameSuffix = (project.findProperty("name_suffix") as String?) ?: ""
val loaderVersion = project.property("loader_version") as String

version = "${rootProject.property("mod_version")}+$mcVersion"
group = rootProject.property("maven_group") as String

configure<BasePluginExtension> {
    archivesName.set("${rootProject.property("archives_base_name")}$nameSuffix-$mcVersion")
}

repositories {
    mavenCentral()
}

configure<SourceSetContainer> {
    named("main") {
        java.srcDir(rootProject.file("$srcRoot/src/main/java"))
        resources.srcDir(rootProject.file("$srcRoot/src/main/resources"))
    }
}

configure<JavaPluginExtension> {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.named<ProcessResources>("processResources") {
    val props = mapOf(
        "version" to project.version.toString(),
        "minecraft_version" to mcVersion,
        "loader_version" to loaderVersion
    )
    inputs.properties(props)
    filesMatching("fabric.mod.json") { expand(props) }
}

// `./gradlew :v26_2:runClient -Pquickplay=localhost:25599` joins a server straight away (test convenience).
project.findProperty("quickplay")?.let { target ->
    afterEvaluate {
        extensions.findByName("loom")?.let { loom ->
            @Suppress("UNCHECKED_CAST")
            val runs = (loom as org.gradle.api.plugins.ExtensionAware).let { loom.javaClass.getMethod("getRuns").invoke(loom) as NamedDomainObjectContainer<Any> }
            runs.named("client") {
                javaClass.getMethod("programArgs", Array<String>::class.java).invoke(this, arrayOf("--quickPlayMultiplayer", target.toString()))
            }
        }
    }
}
