import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

version = "0.0.1"

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    id("com.goncalossilva.resources") version "0.15.0"
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
        }

        jvmMain.dependencies {
            implementation("io.github.bonede:tree-sitter:0.26.6")
        }

        wasmJsMain.dependencies {
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
            implementation("com.goncalossilva:resources:0.15.0")
        }
    }
}