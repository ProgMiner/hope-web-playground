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
        wasmJsMain.configure {
            compilerOptions {
                freeCompilerArgs.add("-Xwasm-use-new-exception-proposal")
            }
        }
    }
}