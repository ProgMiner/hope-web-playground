package ru.hopec.driver.bench

data class FannkuchBenchmarkResult(
    val n: Int,
    val wasm: WasmRunResult,
) {
    fun report(): String =
        buildString {
            appendLine("fannkuch-redux (n=$n)")
            appendLine("  wasm size: ${wasm.wasmBytes} bytes")
            appendLine("  compile: ${wasm.timings.compileNanos / 1_000_000.0} ms")
            appendLine("  run:     ${wasm.timings.runNanos / 1_000_000.0} ms")
            appendLine("  main returned: ${wasm.returnValue}")
            if (wasm.printedOutput.isNotEmpty()) {
                appendLine("  output:")
                wasm.printedOutput.lines().forEach { line ->
                    appendLine("    $line")
                }
            }
        }
}

object FannkuchBenchmark {
    fun sources(n: Int): List<Pair<String, String>> {
        val std = ExampleAssets.load("std")
        val main =
            ExampleAssets
                .load("fannkuch-redux")
                .replace(Regex("""let n == \d+ in"""), "let n == $n in")
        return listOf(
            "std.hope" to std,
            "main.hope" to main,
        )
    }

    fun run(n: Int): FannkuchBenchmarkResult {
        val wasm = WasmJvmRunner.compileAndRun(sources(n))
        return FannkuchBenchmarkResult(n = n, wasm = wasm)
    }
}
