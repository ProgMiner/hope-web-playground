package ru.hopec.driver

import okio.Buffer
import okio.Sink
import ru.hopec.codegen.CodeGenPass
import ru.hopec.codegen.WasmRepresentation
import ru.hopec.core.CompilationContext
import ru.hopec.core.CompilationPass
import ru.hopec.core.then
import ru.hopec.desugarer.DesugarerPass
import ru.hopec.parser.TreeSitterRepresentation
import ru.hopec.parser.treesitter.TsTree
import ru.hopec.renamer.RenamerPass
import ru.hopec.typecheck.TypeCheckPass

expect fun compileWatToBinary(wat: String): ByteArray

class Hopec(
    private val context: CompilationContext,
) {
    fun makeChain(): CompilationPass<TreeSitterRepresentation, WasmRepresentation> =
        RenamerPass.then(DesugarerPass).then(TypeCheckPass()).then(CodeGenPass())

    fun run(
        input: TsTree,
        output: Sink,
    ): Int {
        val watCode =
            try {
                makeChain().run(TreeSitterRepresentation(input), context)?.wat
            } catch (_: NotImplementedError) {
                null
            } ?: return 1

        val wasmBinary = compileWatToBinary(watCode)
        output.write(Buffer().write(wasmBinary), wasmBinary.size.toLong())
        return 0
    }
}
