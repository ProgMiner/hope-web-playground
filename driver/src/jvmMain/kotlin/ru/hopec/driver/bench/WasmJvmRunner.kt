package ru.hopec.driver.bench

import com.dylibso.chicory.runtime.ImportFunction
import com.dylibso.chicory.runtime.ImportValues
import com.dylibso.chicory.runtime.Instance
import com.dylibso.chicory.runtime.WasmFunctionHandle
import com.dylibso.chicory.wasm.Parser
import com.dylibso.chicory.wasm.types.ValType
import kotlinx.coroutines.runBlocking
import okio.Buffer
import ru.hopec.core.GlobalCompilationContext
import ru.hopec.core.MultiStatus
import ru.hopec.core.isError
import ru.hopec.core.topography.Resource
import ru.hopec.driver.Hopec
import ru.hopec.driver.HopecInput
import ru.hopec.parser.treesitter.parseHope

data class WasmRunTimings(
    val compileNanos: Long,
    val runNanos: Long,
)

data class WasmRunResult(
    val returnValue: Long,
    val timings: WasmRunTimings,
    val wasmBytes: Int,
    val printedOutput: String,
)

object WasmJvmRunner {
    fun compile(sources: List<Pair<String, String>>): Pair<ByteArray, Long> {
        val resources =
            sources.map { (path, source) ->
                Resource(path) to runBlocking { parseHope(source) }
            }
        val context = GlobalCompilationContext()
        val buffer = Buffer()
        val compileStart = System.nanoTime()
        val status = Hopec(context).run(HopecInput(resources), buffer)
        val compileNanos = System.nanoTime() - compileStart
        check(!status.isError()) { formatCompilationErrors(context.result()) }
        val binary = buffer.readByteArray()
        return binary to compileNanos
    }

    fun run(
        binary: ByteArray,
        io: CapturingWasmIo = CapturingWasmIo(),
    ): WasmRunResult {
        val instance =
            Instance
                .builder(Parser.parse(binary))
                .withImportValues(io.imports())
                .build()

        runCatching { instance.export("rt.reset").apply() }

        val runStart = System.nanoTime()
        val returnValue = instance.export("main").apply(0)[0]
        val runNanos = System.nanoTime() - runStart

        return WasmRunResult(
            returnValue = returnValue,
            timings = WasmRunTimings(compileNanos = 0, runNanos = runNanos),
            wasmBytes = binary.size,
            printedOutput = io.output.toString(),
        )
    }

    fun compileAndRun(sources: List<Pair<String, String>>): WasmRunResult {
        val (binary, compileNanos) = compile(sources)
        val result = run(binary)
        return result.copy(
            timings = result.timings.copy(compileNanos = compileNanos),
            wasmBytes = binary.size,
        )
    }

    private fun formatCompilationErrors(status: ru.hopec.core.CompilationStatus): String {
        fun walk(s: ru.hopec.core.CompilationStatus): List<String> =
            if (s is MultiStatus) {
                s.children().flatMap { walk(it) }
            } else if (s.severity == ru.hopec.core.StatusSeverity.ERROR) {
                listOf(s.message)
            } else {
                emptyList()
            }
        val errors = walk(status)
        return if (errors.isEmpty()) status.message else errors.joinToString("\n")
    }
}

class CapturingWasmIo {
    val output = StringBuilder()

    fun imports(): ImportValues =
        ImportValues
            .builder()
            .addFunction(
                ImportFunction(
                    "env",
                    "print",
                    listOf<ValType>(ValType.I32),
                    listOf<ValType>(),
                    WasmFunctionHandle { instance, params ->
                        output.append(decodeHopeString(instance, params[0].toInt()))
                        longArrayOf()
                    },
                ),
                ImportFunction(
                    "env",
                    "getChar",
                    listOf<ValType>(),
                    listOf<ValType>(ValType.I32),
                    WasmFunctionHandle { _, _ -> longArrayOf(-1) },
                ),
            ).build()

    private fun decodeHopeString(
        instance: Instance,
        listPtr: Int,
    ): String {
        if (listPtr == 0) return ""
        val memory = instance.memory()
        val result = StringBuilder()
        var cur = listPtr
        while (cur != 0) {
            val tuplePtr = memory.readInt(cur)
            val charCode = memory.readInt(tuplePtr)
            val restPtr = memory.readInt(tuplePtr + 4)
            result.append(charCode.toChar())
            cur = restPtr
        }
        return result.toString()
    }
}
