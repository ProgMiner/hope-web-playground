package ru.hopec.driver.test

import com.dylibso.chicory.runtime.Instance
import com.dylibso.chicory.wasm.Parser
import kotlinx.coroutines.runBlocking
import okio.Buffer
import ru.hopec.core.GlobalCompilationContext
import ru.hopec.core.isError
import ru.hopec.driver.Hopec
import ru.hopec.parser.treesitter.parseHope
import kotlin.test.Test
import kotlin.test.assertEquals

class ZeroArgCallTest {
    private fun runMain(source: String): Long {
        val tree = runBlocking { parseHope(source) }
        val buffer = Buffer()
        val status = Hopec(GlobalCompilationContext()).run(tree, buffer)
        if (status.isError()) error("compilation failed: $status\n$source")
        val main = Instance.builder(Parser.parse(buffer.readByteArray())).build().export("main")
        return main.apply(0)[0]
    }

    @Test
    fun `nullary function referenced by name returns its value`() {
        val source =
            """
            dec zero_arg : num
            --- zero_arg <= 42

            dec main : num
            --- main <= zero_arg
            """.trimIndent()
        assertEquals(42, runMain(source))
    }
}
