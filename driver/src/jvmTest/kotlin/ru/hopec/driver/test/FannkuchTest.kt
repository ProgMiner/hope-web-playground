package ru.hopec.driver.test

import com.dylibso.chicory.wasm.Parser
import kotlinx.coroutines.runBlocking
import okio.Buffer
import ru.hopec.core.GlobalCompilationContext
import ru.hopec.core.isError
import ru.hopec.core.topography.Resource
import ru.hopec.driver.Hopec
import ru.hopec.driver.HopecInput
import ru.hopec.parser.treesitter.parseHope
import java.util.Base64
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertFalse

class FannkuchTest {
    private fun loadAsset(name: String): String {
        val json =
            java.nio.file.Paths
                .get("../hope-web/src/lib/assets/examples/$name.json")
                .readText()
        val contents =
            Regex(""""contents"\s*:\s*"([^"]+)"""")
                .find(json)
                ?.groupValues
                ?.get(1)
                ?: error("no contents in $name")
        return String(Base64.getDecoder().decode(contents))
    }

    @Test
    fun `fannkuch redux compiles to wasm with growing heap`() {
        val std = loadAsset("std")
        val main = loadAsset("fannkuch-redux")
        val resources =
            listOf(
                Resource("std.hope") to runBlocking { parseHope(std) },
                Resource("main.hope") to runBlocking { parseHope(main) },
            )
        val context = GlobalCompilationContext()
        val buffer = Buffer()
        val status = Hopec(context).run(HopecInput(resources), buffer)
        assertFalse(status.isError(), context.result().message)

        val binary = buffer.readByteArray()
        Parser.parse(binary)
    }
}
