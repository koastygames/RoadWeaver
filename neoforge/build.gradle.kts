import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.component.AdhocComponentWithVariants
import org.gradle.api.plugins.BasePluginExtension

plugins {
    id("com.gradleup.shadow") version "8.3.6"
    id("org.jetbrains.kotlin.jvm")
}

architectury {
    platformSetupLoomIde()
    neoForge()
}

// Loom Kotlin DSL accessors may not be available during IDE sync.
// Keep the block minimal; NeoForge-specific settings are optional here.

configurations {
    create("common")
    create("shadowCommon")

    named("compileClasspath") {
        extendsFrom(getByName("common"))
    }
    named("runtimeClasspath") {
        extendsFrom(getByName("common"))
    }
    named("developmentNeoForge") {
        extendsFrom(getByName("common"))
    }
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    exclude("architectury.common.json")
    exclude("fabric.mod.json")
    configurations = listOf(project.configurations.getByName("shadowCommon"))
    archiveClassifier.set("dev-shadow")
}

tasks.named("remapJar") {
    dependsOn(tasks.named("shadowJar"))
}

tasks.named<Jar>("jar") {
    archiveClassifier.set("dev")
    from("${rootProject.projectDir}/LICENSE") {
        rename { "${it}_${project.extensions.getByType<BasePluginExtension>().archivesName.get()}" }
    }

    manifest {
        attributes(
            mapOf(
                "Specification-Title" to "RoadWeaver",
                "Specification-Vendor" to "shiroha233",
                "Specification-Version" to "1",
                "Implementation-Title" to project.name,
                "Implementation-Version" to project.version,
                "Implementation-Vendor" to "shiroha233"
            )
        )
    }
}

repositories {
    mavenCentral()
    maven(url = "https://maven.shedaniel.me/") { name = "Shedaniel Maven" }
    maven(url = "https://api.modrinth.com/maven") { name = "Modrinth Maven" }
    maven(url = "https://bmclapi2.bangbang93.com/maven/") { name = "NeoForge Mirror" }
    maven(url = "https://maven.neoforged.net/releases/") { name = "NeoForge" }
    maven(url = "https://maven.architectury.dev/") { name = "Architectury" }
}

tasks.named<ProcessResources>("processResources") {
    val neoForgeVersion = rootProject.property("neoforge_version").toString()
    val replaceProperties = mapOf(
        "minecraft_version" to "1.21.1",
        "minecraft_version_range" to "[1.21.1,1.22)",
        "neoforge_version" to neoForgeVersion,
        "neoforge_version_range" to "[21.1,)",
        "loader_version_range" to "[4,)",
        "mod_id" to "roadweaver",
        "mod_name" to "RoadWeaver",
        "mod_version" to project.version,
        "mod_authors" to "shiroha233",
        "mod_description" to "Automatically generates roads between structures"
    )

    inputs.properties(replaceProperties)

    filesMatching("META-INF/neoforge.mods.toml") {
        expand(replaceProperties)
    }
}

dependencies {
    val commonNamed = project(mapOf("path" to ":common", "configuration" to "namedElements")) as ProjectDependency
    commonNamed.isTransitive = false
    add("common", commonNamed)

    val commonShadow = project(mapOf("path" to ":common", "configuration" to "transformProductionNeoForge")) as ProjectDependency
    commonShadow.isTransitive = false
    add("shadowCommon", commonShadow)

    // Kotlin stdlib - NeoForge 运行时不会自动提供，需内嵌以确保 Kotlin 类可被 ModLauncher 加载
    add("developmentNeoForge", "org.jetbrains.kotlin:kotlin-stdlib:${property("kotlin_version")}")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:${property("kotlin_version")}")
    include("org.jetbrains.kotlin:kotlin-stdlib:${property("kotlin_version")}")

    // NeoForge 平台
    neoForge("net.neoforged:neoforge:${rootProject.property("neoforge_version")}")

    // Architectury API - 使用 Jar-in-Jar 内嵌
    modApi("dev.architectury:architectury-neoforge:${property("architectury_version")}")
    include("dev.architectury:architectury-neoforge:${property("architectury_version")}")

    // Cloth Config API for NeoForge - Jar-in-Jar include
    modApi("me.shedaniel.cloth:cloth-config-neoforge:${property("cloth_config_version")}")
    include("me.shedaniel.cloth:cloth-config-neoforge:${property("cloth_config_version")}")

    // DynamicTrees - 可选依赖，用于 Mixin 兼容
    modCompileOnly("maven.modrinth:vdjF5PL5:${property("dynamictrees_version")}")
    modRuntimeOnly("maven.modrinth:vdjF5PL5:${property("dynamictrees_version")}")

    // Touhou Little Maid (车万女仆)
    modRuntimeOnly("maven.modrinth:R0bDWFAW:${property("touhou_little_maid_neoforge_version")}")

    // SQLite JDBC - 道路数据持久化（内嵌到模组）
    add("developmentNeoForge", "org.xerial:sqlite-jdbc:3.45.1.0") {
        (this as ModuleDependency).isTransitive = false
    }
    implementation("org.xerial:sqlite-jdbc:3.45.1.0") {
        isTransitive = false
    }
    include("org.xerial:sqlite-jdbc:3.45.1.0") {
        isTransitive = false
    }
}

tasks.named<Jar>("sourcesJar") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    val commonSources = project(":common").tasks.named("sourcesJar")
    dependsOn(commonSources)
    from(commonSources.map { zipTree((it as Jar).archiveFile.get().asFile) })
}

components.withType<AdhocComponentWithVariants>().configureEach {
    withVariantsFromConfiguration(project.configurations.getByName("shadowRuntimeElements")) {
        skip()
    }
}

sourceSets {
    named("main") {
        resources {
            exclude("assets/roadweaver/lang/*.json")
        }
    }
}
