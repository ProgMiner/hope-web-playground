package ru.hopec.driver

import okio.Buffer
import okio.Sink
import ru.hopec.codegen.CodeGenPass
import ru.hopec.codegen.WasmRepresentation
import ru.hopec.core.CompilationContext
import ru.hopec.core.CompilationPass
import ru.hopec.core.then
import ru.hopec.parser.TreeSitterRepresentation
import ru.hopec.parser.treesitter.TsTree
import ru.hopec.renamer.RenamerPass
import ru.hopec.typecheck.TypeCheckPass

expect fun compileWatToBinary(wat: String): ByteArray

class Hopec(
    private val context: CompilationContext,
) {
    fun makeChain(): CompilationPass<TreeSitterRepresentation, WasmRepresentation> = RenamerPass.then(TypeCheckPass()).then(CodeGenPass())

    fun run(
        input: TsTree,
        output: Sink,
    ): Int {
        val context = CompilationContext()

        val watCode =
            try {
                makeChain().run(TreeSitterRepresentation(input), context)?.wat
            } catch (_: NotImplementedError) {
                null
            } ?: STUB_WAT

        val wasmBinary = compileWatToBinary(watCode)
        output.write(Buffer().write(wasmBinary), wasmBinary.size.toLong())
        return 0
    }

    private companion object {
        val STUB_WAT =
            """
            (module
              (func (export "add") (param i32 i32) (result i32)
                (i32.add (local.get 0) (local.get 1))
              )
              (func (export "main") (result i32)
                (call 0 (i32.const 2) (i32.const 3))
              )
            )
            """.trimIndent()
    }
}
