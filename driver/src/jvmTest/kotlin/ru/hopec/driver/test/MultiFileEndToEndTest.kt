package ru.hopec.driver.test

import com.dylibso.chicory.runtime.Instance
import com.dylibso.chicory.wasm.Parser
import kotlinx.coroutines.runBlocking
import okio.Buffer
import ru.hopec.core.GlobalCompilationContext
import ru.hopec.core.isError
import ru.hopec.core.topography.Resource
import ru.hopec.driver.Hopec
import ru.hopec.driver.HopecInput
import ru.hopec.parser.treesitter.parseHope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class MultiFileEndToEndTest {
    private fun compile(vararg sources: Pair<String, String>): ByteArray? {
        val resources =
            sources.map { (path, source) ->
                Resource(path) to runBlocking { parseHope(source) }
            }
        val buffer = Buffer()
        val status = Hopec(GlobalCompilationContext()).run(HopecInput(resources), buffer)
        if (status.isError()) return null
        return buffer.readByteArray()
    }

    private fun runMain(vararg sources: Pair<String, String>): Long {
        val binary = compile(*sources) ?: error("compilation failed")
        val main = Instance.builder(Parser.parse(binary)).build().export("main")
        return main.apply(0)[0]
    }

    @Test
    fun `auto-wrapped file module is importable by filename`() {
        val helpers =
            """
            pubconst triple

            dec triple : num -> num
            --- triple(x) <= *(x, 3)
            """.trimIndent()

        val main =
            """
            uses helpers

            dec main : num
            --- main <= triple(14)
            """.trimIndent()

        assertEquals(42, runMain("helpers.hope" to helpers, "main.hope" to main))
    }

    @Test
    fun `file with internal module exports via top-level pubconst`() {
        val lib =
            """
            module internal
                pubconst double
                dec double : num -> num
                --- double(x) <= +(x, x)
            end

            uses internal
            pubconst double
            """.trimIndent()

        val main =
            """
            uses lib

            dec main : num
            --- main <= double(21)
            """.trimIndent()

        assertEquals(42, runMain("lib.hope" to lib, "main.hope" to main))
    }

    @Test
    fun `main uses two file-level imports from separate files`() {
        val fileA =
            """
            pubconst f

            dec f : num -> num
            --- f(x) <= +(x, 1)
            """.trimIndent()

        val fileB =
            """
            pubconst g

            dec g : num -> num
            --- g(x) <= *(x, 2)
            """.trimIndent()

        val main =
            """
            uses a, b

            dec main : num
            --- main <= g(f(20))
            """.trimIndent()

        assertEquals(42, runMain("a.hope" to fileA, "b.hope" to fileB, "main.hope" to main))
    }

    @Test
    fun `merged output is a single wat module with one memory export`() {
        val lib =
            """
            pubconst id

            dec id : num -> num
            --- id(x) <= x
            """.trimIndent()

        val main =
            """
            uses lib
            dec main : num
            --- main <= id(7)
            """.trimIndent()

        val binary = compile("lib.hope" to lib, "main.hope" to main) ?: error("compile failed")
        assertFalse(binary.isEmpty())
        Parser.parse(binary)
    }

    @Test
    fun `internal modules are not accessible from other files`() {
        val lib =
            """
            module secret
                pubconst hidden
                dec hidden : num -> num
                --- hidden(x) <= +(x, 1)
            end
            """.trimIndent()

        val main =
            """
            uses secret

            dec main : num
            --- main <= hidden(1)
            """.trimIndent()

        val result = compile("lib.hope" to lib, "main.hope" to main)
        assertNull(result, "Should not compile: internal module 'secret' should not be accessible from another file")
    }

    @Test
    fun `module uses within same file works`() {
        val lib =
            """
            module helpers
                pubconst inc
                dec inc : num -> num
                --- inc(x) <= +(x, 1)
            end

            module logic
                uses helpers
                pubconst double_inc
                dec double_inc : num -> num
                --- double_inc(x) <= inc(inc(x))
            end

            uses logic
            pubconst double_inc
            """.trimIndent()

        val main =
            """
            uses lib

            dec main : num
            --- main <= double_inc(40)
            """.trimIndent()

        assertEquals(42, runMain("lib.hope" to lib, "main.hope" to main))
    }
}
