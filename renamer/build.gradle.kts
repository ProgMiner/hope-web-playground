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


        browser {
            testTask {
                useKarma {
                    useFirefoxHeadless()
                }
            }
        }
    }

    sourceSets {
        val wasmJsMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-browser:0.5.0")
                implementation(npm("tree-sitter", "^0.25.0"))
                implementation(npm("web-tree-sitter", "^0.26.7"))
                implementation(project(":core"))
                implementation(project(":parser"))
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
            }
                resources.srcDirs("src/wasmJsMain/resources", "src/wasmJsTest/resources")
                compilerOptions {
                    freeCompilerArgs.add("-Xwasm-use-new-exception-proposal")
                }

        }
    }
}