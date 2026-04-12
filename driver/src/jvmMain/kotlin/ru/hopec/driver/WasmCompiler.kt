package ru.hopec.driver

import java.nio.file.Files

actual fun compileWatToBinary(wat: String): ByteArray {
    val tempDir = Files.createTempDirectory("wabt-")
    val watFile = tempDir.resolve("temp.wat").toFile()
    val wasmFile = tempDir.resolve("temp.wasm").toFile()

    try {
        watFile.writeText(wat)

        val process = ProcessBuilder("wat2wasm", watFile.absolutePath, "-o", wasmFile.absolutePath)
            .redirectErrorStream(true)
            .start()

        val exitCode = process.waitFor()
        if (exitCode != 0) {
            val error = process.inputStream.bufferedReader().readText()
            throw RuntimeException("wat2wasm failed with exit code $exitCode: $error")
        }

        return wasmFile.readBytes()
    } finally {
        @Suppress("UNUSED_VARIABLE")
        val ignored1 = watFile.delete()
        @Suppress("UNUSED_VARIABLE")
        val ignored2 = wasmFile.delete()
        @Suppress("UNUSED_VARIABLE")
        val ignored3 = tempDir.toFile().delete()
    }
}



