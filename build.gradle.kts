import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaToolchainService

// 根项目配置 - 多模块项目
plugins {
    id("architectury-plugin") version "3.4-SNAPSHOT"
    id("dev.architectury.loom") version "1.7-SNAPSHOT" apply false
    id("org.jetbrains.kotlin.jvm") version "2.0.21" apply false
}

architectury {
    minecraft = "1.21.1"
}

allprojects {
    apply(plugin = "java")
    apply(plugin = "architectury-plugin")

    group = "net.shiroha233.roadweaver"
    version = "2.0.9-1.21.1"

    repositories {
        maven("https://maven.architectury.dev/")
        maven("https://maven.fabricmc.net/")
        maven("https://maven.minecraftforge.net/")
        maven("https://maven.shedaniel.me/")
        maven("https://maven.terraformersmc.com/")
        mavenCentral()
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(21)
    }

    extensions.configure<JavaPluginExtension>("java") {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
        withSourcesJar()
    }
}

subprojects {
    apply(plugin = "dev.architectury.loom")

    extensions.configure<BasePluginExtension>("base") {
        archivesName.set("${rootProject.name}-${project.name}")
    }

    // Loom Kotlin DSL accessors may not be on the buildscript classpath at IDE sync time,
    // so we invoke the extension method reflectively to keep behavior identical.
    val loomExt = extensions.getByName("loom")
    loomExt.javaClass.methods
        .firstOrNull { it.name == "silentMojangMappingsLicense" && it.parameterCount == 0 }
        ?.invoke(loomExt)

    dependencies {
        "minecraft"("com.mojang:minecraft:1.21.1")
        val mappingsProvider = loomExt.javaClass.methods
            .firstOrNull { it.name == "officialMojangMappings" && it.parameterCount == 0 }
            ?.invoke(loomExt)
        if (mappingsProvider != null) {
            add("mappings", mappingsProvider)
        }
    }

    tasks.withType<JavaExec>().configureEach {
        javaLauncher.set(
            project.extensions.getByType<JavaToolchainService>().launcherFor {
                languageVersion.set(JavaLanguageVersion.of(21))
            }
        )
    }
}
