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
        browser{
            testTask {
                useKarma {
                    useFirefox()
                }
            }
        }
    }

    sourceSets {
        wasmJsMain.dependencies {
            implementation(npm("tree-sitter", "^0.25.0"))
            implementation(npm("web-tree-sitter", "^0.26.7"))
            implementation(project(":core"))
            implementation(kotlin("test"))
        }

        wasmJsMain.configure {
            compilerOptions {
                freeCompilerArgs.add("-Xwasm-use-new-exception-proposal")
            }
        }
    }
}