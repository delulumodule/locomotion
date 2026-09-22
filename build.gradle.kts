import net.fabricmc.loom.task.prod.ClientProductionRunTask

plugins {
    id("net.fabricmc.fabric-loom") version "1.17.21"
}

version = "${property("mod.version")}+26.2"
group = property("mod.group")!!
base.archivesName.set("locomotion-fabric")

repositories {
    maven("https://maven.terraformersmc.com/")
    maven("https://maven.isxander.dev/releases")
}

dependencies {
    minecraft("com.mojang:minecraft:26.2")
    implementation("net.fabricmc:fabric-loader:0.19.3")
    implementation("net.fabricmc.fabric-api:fabric-api:0.154.2+26.2")
    compileOnly("dev.isxander:yet-another-config-lib:3.9.5+26.2-fabric")
    compileOnly("com.terraformersmc:modmenu:20.0.1")
}

sourceSets.main {
    java.srcDir("fabric/src/main/java")
    resources.srcDir("fabric/src/main/resources")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    withSourcesJar()
}

tasks.processResources {
    val props = mapOf(
        "mod_id" to project.property("mod.id"),
        "mod_version" to project.version,
        "mod_name" to project.property("mod.name"),
        "mod_description" to project.property("mod.description"),
        "fabric_loader_version" to "0.19.3",
        "minecraft_version" to "26.2"
    )
    inputs.properties(props)
    filesMatching("fabric.mod.json") { expand(props) }
}

fabricApi {
    configureTests {
        createSourceSet = true
        modId = "locomotion-test"
        enableGameTests = false
        enableClientGameTests = true
    }
}

val gametestJar by tasks.registering(Jar::class) {
    from(sourceSets["gametest"].output)
    archiveClassifier.set("gametest")
}

dependencies {
    "productionRuntimeMods"("net.fabricmc.fabric-api:fabric-api:0.154.2+26.2")
    "productionRuntimeMods"(fabricApi.module("fabric-client-gametest-api-v1", "0.154.2+26.2"))
}

tasks.register<ClientProductionRunTask>("runProductionClientGameTest") {
    mods.from(gametestJar)
    jvmArgs.add("-Dfabric.client.gametest")
    jvmArgs.add("-Dfabric.client.gametest.disableNetworkSynchronizer=true")
    runDir.set(layout.buildDirectory.dir("run/productionClientGameTest"))
    useXVFB.set(false)

    providers.gradleProperty("testModpack").orNull?.let { pack ->
        mods.from(fileTree("$pack/mods") {
            include("*.jar")
            exclude("fabric-api-*.jar", "locomotion-*.jar")
        })
        doFirst {
            copy {
                from("$pack/config")
                into(runDir.dir("config"))
            }
        }
    }
}
