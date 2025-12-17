import org.gradle.api.component.AdhocComponentWithVariants
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.ModuleDependency

plugins {
    id("com.gradleup.shadow") version "8.3.6"
    id("org.jetbrains.kotlin.jvm")
}

architectury {
    platformSetupLoomIde()
    fabric()
}

configurations {
    create("common")
    create("shadowCommon")

    named("compileClasspath") {
        extendsFrom(getByName("common"))
    }
    named("runtimeClasspath") {
        extendsFrom(getByName("common"))
    }
    named("developmentFabric") {
        extendsFrom(getByName("common"))
    }
}

repositories {
    maven(url = "https://api.modrinth.com/maven")
    maven(url = "https://maven.shedaniel.me/") {
        name = "Shedaniel Maven"
    }
}

dependencies {
    implementation(project(":common"))

    val commonNamed = project(mapOf("path" to ":common", "configuration" to "namedElements")) as ProjectDependency
    commonNamed.isTransitive = false
    add("common", commonNamed)

    val commonShadow = project(mapOf("path" to ":common", "configuration" to "transformProductionFabric")) as ProjectDependency
    commonShadow.isTransitive = false
    add("shadowCommon", commonShadow)

    // Kotlin stdlib - Fabric 运行时不会自动提供，需内嵌以确保 Kotlin 类可被加载
    implementation("org.jetbrains.kotlin:kotlin-stdlib:${property("kotlin_version")}")
    include("org.jetbrains.kotlin:kotlin-stdlib:${property("kotlin_version")}")

    // Fabric Loader
    modImplementation("net.fabricmc:fabric-loader:${property("loader_version")}")

    // Fabric API
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_version")}")

    // Architectury API - 内嵌到模组中
    modImplementation("dev.architectury:architectury-fabric:${property("architectury_version")}") {
        exclude(group = "net.fabricmc.fabric-api")
    }
    include("dev.architectury:architectury-fabric:${property("architectury_version")}")

    // Cloth Config API for Fabric
    modApi("me.shedaniel.cloth:cloth-config-fabric:${property("cloth_config_version")}") {
        exclude(group = "net.fabricmc.fabric-api")
    }
    include("me.shedaniel.cloth:cloth-config-fabric:${property("cloth_config_version")}")

    modRuntimeOnly("maven.modrinth:VSNURh3q:${property("c2me_version")}")

    modCompileOnly("maven.modrinth:mOgUt4GM:${property("modmenu_version")}")
    modRuntimeOnly("maven.modrinth:mOgUt4GM:${property("modmenu_version")}")
    modCompileOnly("maven.modrinth:eXts2L7r:${property("placeholder_api_version")}")
    modRuntimeOnly("maven.modrinth:eXts2L7r:${property("placeholder_api_version")}")

    // SQLite JDBC - 道路数据持久化（内嵌到模组）
    implementation("org.xerial:sqlite-jdbc:3.45.1.0")
    include("org.xerial:sqlite-jdbc:3.45.1.0")
}

tasks.named<ProcessResources>("processResources") {
    inputs.property("version", project.version)

    filesMatching("fabric.mod.json") {
        expand(mapOf("version" to project.version))
    }
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    exclude("architectury.common.json")
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
}

tasks.named<Jar>("sourcesJar") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    val commonSources = project(":common").tasks.named("sourcesJar")
    dependsOn(commonSources)
    from(commonSources.map { zipTree((it as Jar).archiveFile.get().asFile) })
}

// Gradle/插件版本差异会导致 AdhocComponentWithVariants 的 variants/skip DSL 在 IDE 同步期不可用。
// 这里改为直接禁止 shadowRuntimeElements 对外发布，避免 shadow 相关变体被发布到 Maven。
configurations.named("shadowRuntimeElements") {
    isCanBeConsumed = false
    outgoing.artifacts.clear()
}

sourceSets {
    named("main") {
        resources {
            exclude("assets/roadweaver/lang/*.json")
            exclude("pack.mcmeta")
        }
    }
}
