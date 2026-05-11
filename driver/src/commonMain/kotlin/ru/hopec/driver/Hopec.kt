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
    @Suppress("UNUSED_PARAMETER") private val context: CompilationContext,
) {
    fun makeChain(): CompilationPass<TreeSitterRepresentation, RenamedRepresentation> = RenamerPass

<<<<<<< HEAD
    fun makeChain(): CompilationPass<TreeSitterRepresentation, WasmRepresentation> =
        RenamerPass.then(TypeCheckPass()).then(CodeGenPass())

    fun run(@Suppress("UNUSED_PARAMETER") input: TsTree, output: Sink): Int {
=======
    fun run(
        @Suppress("UNUSED_PARAMETER") input: TsTree,
        output: Sink,
    ): Int {
>>>>>>> master
        val context = CompilationContext()

        // TypeCheckPass is pending full implementation (DesugaredRepresentation.fromRenamed is TODO).
        // Fall back to the stub WAT when the pipeline is not yet complete.
        val watCode = try {
            val res = makeChain().run(TreeSitterRepresentation(input), context)
            println("Debug: $res")
            res?.wat
        } catch (_: NotImplementedError) {
            null
        } ?: STUB_WAT

<<<<<<< HEAD
        val wasmBinary = compileWatToBinary(watCode)
        output.write(Buffer().write(wasmBinary), wasmBinary.size.toLong())
        return 0
    }

    private companion object {
        val STUB_WAT = """
=======
        val watCode =
            """
>>>>>>> master
            (module
              (func (export "add") (param i32 i32) (result i32)
                (i32.add (local.get 0) (local.get 1))
              )
              (func (export "main") (result i32)
                (call 0 (i32.const 2) (i32.const 3))
              )
            )
<<<<<<< HEAD
        """.trimIndent()
=======
            """.trimIndent()

        val wasmBinary = compileWatToBinary(watCode)
        output.write(Buffer().write(wasmBinary), wasmBinary.size.toLong())
        return 0
>>>>>>> master
    }
}
