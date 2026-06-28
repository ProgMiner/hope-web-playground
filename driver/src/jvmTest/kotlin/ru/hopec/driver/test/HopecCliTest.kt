package ru.hopec.driver.test

import com.github.ajalt.clikt.testing.test
import ru.hopec.driver.HopecCompile
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HopecCliTest {
    @Test
    fun `unsupported program reports compilation failure`() {
        assertEquals("compilation failed\n", compile().stderr)
    }

    @Test
    fun `--output writes wasm binary without executing`() {
        val source = createTempFile(prefix = "hope", suffix = ".hope")
        val out = createTempFile(prefix = "hope", suffix = ".wasm")
        try {
            source.writeText(
                """
                dec main : num
                --- main <= 42
                """.trimIndent(),
            )
            val result = compile(file = source.toString(), args = listOf("-o", out.toString()))
            assertTrue(out.exists(), "output file should be created")
            val bytes = out.readBytes()
            assertTrue(bytes.isNotEmpty(), "output wasm binary should be non-empty")
            // wasm magic number: 0x00 0x61 0x73 0x6d
            assertEquals(0x00.toByte(), bytes[0])
            assertEquals(0x61.toByte(), bytes[1])
            assertEquals(0x73.toByte(), bytes[2])
            assertEquals(0x6d.toByte(), bytes[3])
            assertTrue(result.stdout.contains("wasm written to"), "should report wasm written")
        } finally {
            source.deleteIfExists()
            out.deleteIfExists()
        }
    }

    private fun compile(
        file: String = helloFile(),
        args: List<String> = emptyList(),
    ) = HopecCompile().test(listOf(file) + args)

    private fun helloFile() = "../parser/src/commonTest/resources/hello.hope"
}
