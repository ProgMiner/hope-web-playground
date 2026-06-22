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
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IoEndToEndTest {
    private val ioSource =
        """
        pubconst print, getChar

        dec print : list char -> unit
        --- print(text) <= 0

        dec getChar : char
        --- getChar <= 0

        """.trimIndent()

    @Test
    fun `program using io print compiles to valid wasm`() {
        val main =
            """
            uses io

            dec main : num
            --- main <= let _ == print("Hi") in 42
            """.trimIndent()

        val resources =
            listOf(
                Resource("io.hope") to runBlocking { parseHope(ioSource) },
                Resource("main.hope") to runBlocking { parseHope(main) },
            )
        val buffer = Buffer()
        val status = Hopec(GlobalCompilationContext()).run(HopecInput(resources), buffer)
        assertFalse(status.isError(), status.toString())
        val binary = buffer.readByteArray()
        assertTrue(binary.isNotEmpty())
        Parser.parse(binary)
    }
}
