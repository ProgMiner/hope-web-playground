package ru.hopec.driver.test

import ru.hopec.driver.bench.WasmJvmRunner
import kotlin.test.Test
import kotlin.test.assertEquals

class WasmJvmRunnerTest {
    @Test
    fun `measures compile and run of trivial program`() {
        val result =
            WasmJvmRunner.compileAndRun(
                listOf(
                    "main.hope" to
                        """
                        dec main : num
                        --- main <= 42
                        """.trimIndent(),
                ),
            )
        println(
            buildString {
                appendLine("trivial benchmark")
                appendLine("  compile: ${result.timings.compileNanos / 1_000_000.0} ms")
                appendLine("  run:     ${result.timings.runNanos / 1_000_000.0} ms")
            },
        )
        assertEquals(42, result.returnValue)
    }
}
