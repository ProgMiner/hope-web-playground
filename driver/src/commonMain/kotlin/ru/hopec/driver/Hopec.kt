package ru.hopec.driver

import okio.Buffer
import okio.Sink
import ru.hopec.core.CompilationContext
import ru.hopec.parser.treesitter.TsTree

expect fun compileWatToBinary(wat: String): ByteArray

class Hopec(@Suppress("UNUSED_PARAMETER") private val context: CompilationContext) {

    fun run(@Suppress("UNUSED_PARAMETER") input: TsTree, output: Sink): Int {
        val watCode = """
            (module
              (func (export "add") (param i32 i32) (result i32)
                local.get 0
                local.get 1
                i32.add
              )
              (memory (export "memory") 1)
              (table (export "table") 1 funcref)
            )
        """.trimIndent()

        val wasmBinary = compileWatToBinary(watCode)
        output.write(Buffer().write(wasmBinary), wasmBinary.size.toLong())
        return 0
    }
}