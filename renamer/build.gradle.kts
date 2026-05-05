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
            implementation(project(":core"))
            implementation(project(":parser"))
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("com.goncalossilva:resources:0.15.0")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.0")
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
