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
    }

    sourceSets {
        commonMain.dependencies {
            implementation("com.squareup.okio:okio:3.16.2")
            implementation(project(":core"))
            implementation(project(":parser"))
            implementation(project(":typecheck"))
        }

        wasmJsMain.configure {
            compilerOptions {
                freeCompilerArgs.add("-Xwasm-use-new-exception-proposal")
            }
        }
    }
}

val grammarBuilt = rootProject.file("tree-sitter-hope/tree-sitter-hope.wasm").exists()
afterEvaluate {
    if (!grammarBuilt) {
        listOf("jvmTest", "wasmJsNodeTest", "wasmJsBrowserTest").forEach { name ->
            tasks.findByName(name)?.enabled = false
        }
    }
}
