package ru.hopec.driver

import okio.Buffer
import okio.Sink
import ru.hopec.core.CompilationContext
import ru.hopec.core.CompilationPass
import ru.hopec.parser.TreeSitterRepresentation
import ru.hopec.parser.treesitter.TsTree
import ru.hopec.renamer.RenamedRepresentation
import ru.hopec.renamer.RenamerPass

expect fun compileWatToBinary(wat: String): ByteArray

class Hopec(
    @Suppress("UNUSED_PARAMETER") private val context: CompilationContext,
) {
    fun makeChain(): CompilationPass<TreeSitterRepresentation, RenamedRepresentation> = RenamerPass

    fun run(
        @Suppress("UNUSED_PARAMETER") input: TsTree,
        output: Sink,
    ): Int {
        val context = CompilationContext()
        val res = makeChain().run(TreeSitterRepresentation(input), context)

        println("Debug: $res")

        val watCode =
            """
            (module
              (func (export "add") (param i32 i32) (result i32)
                (i32.add (local.get 0) (local.get 1))
              )
              (memory (export "memory") 1)
            )
            """.trimIndent()

        val wasmBinary = compileWatToBinary(watCode)
        output.write(Buffer().write(wasmBinary), wasmBinary.size.toLong())
        return 0
    }
}
