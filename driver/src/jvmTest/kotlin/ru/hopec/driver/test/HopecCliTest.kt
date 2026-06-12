package ru.hopec.driver.test

import com.github.ajalt.clikt.testing.test
import ru.hopec.driver.HopecCompile
import kotlin.test.Test
import kotlin.test.assertEquals

class HopecCliTest {
    @Test
    fun `unsupported program reports compilation failure`() {
        // hello.hope использует `write`, которого нет в core —
        // компиляция должна честно завершиться ошибкой, а не заглушкой.
        assertEquals("compilation failed\n", compile().stderr)
    }

    private fun compile(file: String = helloFile()) = HopecCompile().test(file)

    private fun helloFile() = "../parser/src/commonTest/resources/hello.hope"
}
