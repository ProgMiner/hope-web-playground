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
        nodejs()
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation("com.squareup.okio:okio:3.16.2")
            implementation(project(":codegen"))
            implementation(project(":core"))
            implementation(project(":parser"))
            implementation(project(":renamer"))
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
