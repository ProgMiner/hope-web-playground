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
            implementation(project(":typecheck"))
        }

        jvmMain.dependencies {
            implementation("com.github.ajalt.clikt:clikt:5.1.0")
            implementation("com.dylibso.chicory:runtime:1.7.5")
            implementation("com.dylibso.chicory:wabt:1.7.5")
        }

        wasmJsMain.dependencies {
            implementation(npm("binaryen", "^117.0.0"))
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