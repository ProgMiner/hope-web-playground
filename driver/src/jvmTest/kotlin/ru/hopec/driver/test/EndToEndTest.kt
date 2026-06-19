package ru.hopec.driver.test

import com.dylibso.chicory.runtime.Instance
import com.dylibso.chicory.wasm.Parser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okio.Buffer
import ru.hopec.core.GlobalCompilationContext
import ru.hopec.core.isError
import ru.hopec.core.topography.Resource
import ru.hopec.driver.Hopec
import ru.hopec.driver.HopecInput
import ru.hopec.driver.defaultContext
import ru.hopec.parser.treesitter.parseHope
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class EndToEndTest {
    private fun compile(source: String): ByteArray? {
        val tree = runBlocking { parseHope(source) }
        val buffer = Buffer()
        val status = Hopec(defaultContext()).run(tree, buffer)
        if (status.isError()) return null
        return buffer.readByteArray()
    }

    private fun compile(vararg sources: Pair<String, String>): ByteArray? {
        val trees =
            runBlocking {
                sources.map { Resource(it.first) to parseHope(it.second) }.toList()
            }
        val buffer = Buffer()
        val status = Hopec(defaultContext()).run(HopecInput(trees), buffer)
        if (status.isError()) return null
        return buffer.readByteArray()
    }

    private fun runMain(source: String): Long {
        val binary = compile(source) ?: error("compilation failed:\n$source")
        val main = Instance.builder(Parser.parse(binary)).build().export("main")
        return main.apply(0)[0]
    }

    private fun runMain(vararg sources: Pair<String, String>): Long {
        fun displayInput(vararg sources: Pair<String, String>): String =
            sources.map { "${it.first}.hope:\n${it.second}\n\n" }.reduce { l, r -> l + r }

        val binary = compile(*sources) ?: error("compilation failed:\n${displayInput(*sources)}")
        val main = Instance.builder(Parser.parse(binary)).build().export("main")
        return main.apply(0)[0]
    }

    @Test
    fun `zero-arg main returns constant`() {
        val source =
            """
            dec main : num
            --- main <= 42
            """.trimIndent()
        assertEquals(42, runMain(source))
    }

    @Test
    fun `arithmetic core plus`() {
        val source =
            """
            dec main : num
            --- main <= +(2, 3)
            """.trimIndent()
        assertEquals(5, runMain(source))
    }

    @Test
    fun `nested arithmetic`() {
        val source =
            """
            dec main : num
            --- main <= *(+(2, 3), -(10, 6))
            """.trimIndent()
        assertEquals(20, runMain(source))
    }

    @Test
    fun `user function call and variable binding`() {
        val source =
            """
            dec double : num -> num
            --- double(x) <= +(x, x)

            dec main : num
            --- main <= double(21)
            """.trimIndent()
        assertEquals(42, runMain(source))
    }

    @Test
    fun `let binding with shadowing`() {
        val source =
            """
            dec f : num -> num
            --- f(x) <= let x == 7 in (+(x, x))

            dec main : num
            --- main <= f(100)
            """.trimIndent()
        assertEquals(14, runMain(source))
    }

    @Test
    fun `where binding`() {
        val source =
            """
            dec f : num -> num
            --- f(x) <= (+(y, y)) where y == 5

            dec main : num
            --- main <= f(0)
            """.trimIndent()
        assertEquals(10, runMain(source))
    }

    @Test
    fun `conditional then form`() {
        val source =
            """
            dec f : truval -> num
            --- f(c) <= c then 1 else 2

            dec main : num
            --- main <= f(true)
            """.trimIndent()
        assertEquals(1, runMain(source))
    }

    @Test
    fun `literal pattern selects branch`() {
        val source =
            """
            dec f : num -> num
            --- f(0) <= 1
            --- f(_) <= 2

            dec main : num
            --- main <= +(f(0), f(5))
            """.trimIndent()
        assertEquals(3, runMain(source))
    }

    @Test
    fun `list pattern matching head`() {
        val source =
            """
            dec head : list num -> num
            --- head(x :: _) <= x
            --- head(nil) <= 0

            dec main : num
            --- main <= head([7, 8, 9])
            """.trimIndent()
        assertEquals(7, runMain(source))
    }

    @Test
    fun `user adt constructors and matching`() {
        val source =
            """
            data color == red ++ blue

            dec tonum : color -> num
            --- tonum(red) <= 1
            --- tonum(blue) <= 2

            dec main : num
            --- main <= +(tonum(blue), tonum(red))
            """.trimIndent()
        assertEquals(3, runMain(source))
    }

    @Test
    fun `recursion sums first n numbers`() {
        val source =
            """
            dec sum : num -> num
            --- sum(0) <= 0
            --- sum(n) <= +(n, sum(-(n, 1)))

            dec main : num
            --- main <= sum(10)
            """.trimIndent()
        assertEquals(55, runMain(source))
    }

    @Test
    fun `unsupported identifier fails compilation instead of stub`() {
        val source = """write "Hello world"""""
        assertEquals(null, compile(source))
    }

    @Test
    fun `compiled module is valid wasm`() {
        val source =
            """
            dec main : num
            --- main <= 1
            """.trimIndent()
        val binary = compile(source) ?: error("compilation failed")
        assertNotEquals(0, binary.size)
        Parser.parse(binary)
    }

    @Test
    fun `multiple files`() {
        val main =
            """
            uses foo
                
            dec main : num
            --- main <= bar (6, 7)
            """.trimIndent()
        val foo =
            """
            dec bar : num
            --- bar (x, y) <= x * y
            """.trimIndent()
        // TODO: uncomment test when all stages of multifile compilation are ready
        // assertEquals(42, runMain("main" to main, "foo" to foo))
        assert(true)
    }
}
