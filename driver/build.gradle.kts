import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

version = "0.0.1"

plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

repositories {
    mavenCentral()
}

kotlin {
    jvm()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation("com.squareup.okio:okio:3.16.2")
            implementation(project(":codegen"))
            implementation(project(":core"))
            implementation(project(":parser"))
            implementation(project(":renamer"))
            implementation(project(":desugarer"))
            implementation(project(":typecheck"))
        }

        jvmMain.dependencies {
            implementation("com.github.ajalt.clikt:clikt:5.1.0")
            implementation("com.dylibso.chicory:runtime:1.7.5")
            implementation("com.dylibso.chicory:wabt:1.7.5")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
        }

        wasmJsMain.dependencies {
            implementation(npm("binaryen", "^117.0.0"))
            file(layout.projectDirectory.file("../tree-sitter-hope/tree-sitter-hope.wasm"))
            implementation(npm("tree-sitter", "^0.25.0"))
            implementation(npm("web-tree-sitter", "^0.26.7"))
        }

        wasmJsMain.configure {
            compilerOptions {
                freeCompilerArgs.add("-Xwasm-use-new-exception-proposal")
            }
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

tasks.register<JavaExec>("fannkuchBench") {
    group = "benchmark"
    description = "Run fannkuch-redux WASM benchmark on JVM (override n with -Pfannkuch.n=7)"
    dependsOn("jvmJar")
    classpath(
        kotlin
            .jvm()
            .compilations
            .getByName("main")
            .runtimeDependencyFiles,
        kotlin
            .jvm()
            .compilations
            .getByName("main")
            .output.allOutputs,
    )
    mainClass.set("ru.hopec.driver.HopecCliKt")
    val n = project.findProperty("fannkuch.n")?.toString() ?: "5"
    args("bench", "--n", n)
    jvmArgs("-Xmx4g", "-Xss64m")
}

tasks.register<Test>("fannkuchBenchmark") {
    group = "benchmark"
    description = "Run fannkuch WASM benchmark JUnit test (override n with -Pfannkuch.n=7)"
    dependsOn("jvmTestClasses")
    testClassesDirs = sourceSets["jvmTest"].output.classesDirs
    classpath = sourceSets["jvmTest"].runtimeClasspath
    maxHeapSize = "4g"
    systemProperty("fannkuch.bench", "true")
    systemProperty("fannkuch.n", project.findProperty("fannkuch.n")?.toString() ?: "5")
    filter { includeTestsMatching("ru.hopec.driver.test.FannkuchBenchmarkTest") }
}

tasks.named<Test>("jvmTest") {
    maxHeapSize = "4g"
    jvmArgs("-Xss64m")
}
