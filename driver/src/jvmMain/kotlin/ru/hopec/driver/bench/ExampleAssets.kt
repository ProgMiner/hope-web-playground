package ru.hopec.driver.bench

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

object ExampleAssets {
    private val assetRoots: List<Path> =
        listOf(
            Path.of("hope-web/src/lib/assets/examples"),
            Path.of("../hope-web/src/lib/assets/examples"),
        )

    fun load(name: String): String {
        val json =
            assetRoots
                .firstOrNull { it.resolve("$name.json").exists() }
                ?.resolve("$name.json")
                ?.readText()
                ?: error("example asset not found: $name.json (cwd=${Path.of("").toAbsolutePath()})")

        val contents =
            Regex(""""contents"\s*:\s*"([^"]+)"""")
                .find(json)
                ?.groupValues
                ?.get(1)
                ?: error("no contents field in $name.json")

        return String(
            java.util.Base64
                .getDecoder()
                .decode(contents),
        )
    }
}
