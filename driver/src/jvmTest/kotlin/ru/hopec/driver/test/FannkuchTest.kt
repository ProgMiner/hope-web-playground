package ru.hopec.driver.test

import com.dylibso.chicory.wasm.Parser
import kotlinx.coroutines.runBlocking
import ru.hopec.core.GlobalCompilationContext
import ru.hopec.core.topography.Resource
import ru.hopec.driver.HopecInput
import ru.hopec.driver.MultiFilePipeline
import ru.hopec.driver.bench.FannkuchBenchmark
import ru.hopec.driver.bench.WasmJvmRunner
import ru.hopec.parser.treesitter.parseHope
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class FannkuchTest {
    @Test
    fun `printNum from std runs`() {
        val std =
            ru.hopec.driver.bench.ExampleAssets
                .load("std")
        val result =
            WasmJvmRunner.compileAndRun(
                listOf(
                    "std.hope" to std,
                    "main.hope" to
                        """
                        uses std
                        dec main : num
                        --- main <= let _ == printNum(0) in 0
                        """.trimIndent(),
                ),
            )
        assertEquals(0, result.returnValue)
        assertEquals("0", result.printedOutput.trim())
    }

    @Test
    fun `fannkuch n equals 6 matches reference`() {
        val result = WasmJvmRunner.compileAndRun(FannkuchBenchmark.sources(n = 6))
        assertEquals(
            "49",
            result.printedOutput
                .lines()
                .first()
                .trim(),
        )
        assertContains(result.printedOutput, "Pfannkuchen(6) = 10")
    }

    @Test
    fun `fannkuch n equals 7 matches reference`() {
        val result = WasmJvmRunner.compileAndRun(FannkuchBenchmark.sources(n = 7))
        assertEquals(
            "228",
            result.printedOutput
                .lines()
                .first()
                .trim(),
        )
        assertContains(result.printedOutput, "Pfannkuchen(7) = 16")
    }

    @Test
    fun `fannkuch redux compiles to valid wasm`() {
        val binary =
            WasmJvmRunner
                .compile(FannkuchBenchmark.sources(n = 4))
                .first
        Parser.parse(binary)
    }

    @Test
    fun `fannkuch wat includes growing malloc`() {
        val resources =
            FannkuchBenchmark.sources(n = 4).map { (path, source) ->
                Resource(path) to runBlocking { parseHope(source) }
            }
        val context = GlobalCompilationContext()
        HopecInput(resources).populate(context)
        val wat = MultiFilePipeline(context).compile() ?: error("no wat")
        assertContains(wat, "memory.grow")
    }
}

class FannkuchBenchmarkTest {
    @Test
    fun `fannkuch redux benchmark`() {
        if (System.getProperty("fannkuch.bench") != "true") {
            println("skipped (set -Dfannkuch.bench=true to run WASM benchmark)")
            return
        }
        val n = System.getProperty("fannkuch.n", "5").toInt()
        val result = FannkuchBenchmark.run(n)
        println(result.report())
        assertEquals(0, result.wasm.returnValue)
        assertFalse(result.wasm.printedOutput.isBlank(), "expected benchmark output")
    }
}
