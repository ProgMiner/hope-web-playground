import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

version = "0.0.1"

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    id("com.goncalossilva.resources") version "0.15.0"
}

repositories {
    mavenCentral()
}

tasks.withType<ProcessResources> {
    copy {
        from("../tree-sitter-hope")
        into(project.layout.projectDirectory.file("src/wasmJsTest/resources/lib"))
        include("*.wasm")
    }
}

kotlin {
    jvm()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        nodejs()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core"))
            implementation(kotlin("stdlib-common"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
        }

        jvmMain.dependencies {
            implementation("io.github.bonede:tree-sitter:0.26.6")
        }

        wasmJsMain.dependencies {
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
            implementation("com.goncalossilva:resources:0.15.0")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.0")
        }
    }
}
