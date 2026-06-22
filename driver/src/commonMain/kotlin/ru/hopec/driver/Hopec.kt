package ru.hopec.driver

import okio.Buffer
import okio.Sink
import ru.hopec.codegen.CodeGenPass
import ru.hopec.codegen.WasmRepresentation
import ru.hopec.core.CompilationPass
import ru.hopec.core.CompilationStatus
import ru.hopec.core.GlobalCompilationContext
import ru.hopec.core.errorStatus
import ru.hopec.core.then
import ru.hopec.core.topography.Range
import ru.hopec.desugarer.DesugarerPass
import ru.hopec.parser.TreeSitterRepresentation
import ru.hopec.parser.treesitter.TsTree
import ru.hopec.renamer.RenamerPass
import ru.hopec.typecheck.TypeCheckPass

expect fun compileWatToBinary(wat: String): ByteArray

class Hopec(
    private val context: GlobalCompilationContext,
) {
    fun makeChain(): CompilationPass<TreeSitterRepresentation, WasmRepresentation> =
        RenamerPass.then(DesugarerPass).then(TypeCheckPass()).then(CodeGenPass())

    fun run(
        tree: TsTree,
        output: Sink,
    ): CompilationStatus = run(HopecInput(tree), output)

    fun run(
        input: HopecInput,
        output: Sink,
    ): CompilationStatus {
        input.populate(context)
        if (context.resolveMain() == null) return noMain()

        val watCode =
            try {
                MultiFilePipeline(context).compile()
            } catch (_: NotImplementedError) {
                context.report(
                    errorStatus(
                        "Compilation failed: unsupported language feature encountered",
                        Range(),
                    ),
                )
                null
            } ?: return context.result()

        val wasmBinary = compileWatToBinary(watCode)
        output.write(Buffer().write(wasmBinary), wasmBinary.size.toLong())
        return context.result()
    }

    private fun noMain(): CompilationStatus =
        errorStatus("No 'main' module found. Define a module named 'main' as the entry point.", Range())
}
