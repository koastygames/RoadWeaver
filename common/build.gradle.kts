plugins {
    id("org.jetbrains.kotlin.jvm")
}

architectury {
    common("fabric", "neoforge")
}

dependencies {
    // Kotlin 标准库
    implementation("org.jetbrains.kotlin:kotlin-stdlib:${property("kotlin_version")}")

    // Architectury API（作为 mod 依赖以启用 Loom 重映射）
    modImplementation("dev.architectury:architectury:${property("architectury_version")}")

    compileOnly("net.fabricmc:fabric-loader:${property("loader_version")}")

    // SQLite JDBC - 道路数据持久化
    implementation("org.xerial:sqlite-jdbc:3.45.1.0")
}

sourceSets {
    named("main") {
        kotlin {
            exclude("net/shiroha233/roadweaver/client/gui/**")
        }
        resources {
            exclude("pack.mcmeta")
        }
    }
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        freeCompilerArgs.addAll(listOf("-Xjvm-default=all"))
    }
}

// 确保 Java 编译在 Kotlin 编译之后，并将 Kotlin 输出加入 Java classpath
tasks.named<JavaCompile>("compileJava") {
    dependsOn(tasks.named("compileKotlin"))
}

tasks.withType<JavaCompile>().configureEach {
    classpath += files(tasks.named("compileKotlin").flatMap { (it as org.jetbrains.kotlin.gradle.tasks.KotlinCompile).destinationDirectory })
}
