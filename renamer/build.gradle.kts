import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

version = "0.0.1"

plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

repositories {
    mavenCentral()
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        wasmJsMain.dependencies {
            implementation(project(":core"))
            implementation(project(":parser"))
        }

        wasmJsMain.configure {
            compilerOptions {
                freeCompilerArgs.add("-Xwasm-use-new-exception-proposal")
            }
        }
    }
}