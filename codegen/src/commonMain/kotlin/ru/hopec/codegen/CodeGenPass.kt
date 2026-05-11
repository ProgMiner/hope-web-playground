package ru.hopec.codegen

import ru.hopec.core.CompilationContext
import ru.hopec.core.CompilationPass
import ru.hopec.typecheck.TypedRepresentation

class CodeGenPass : CompilationPass<TypedRepresentation, WasmRepresentation> {
<<<<<<< HEAD
    override fun run(from: TypedRepresentation, context: CompilationContext): WasmRepresentation =
        WasmRepresentation(WatGenerator(from).generate())
=======
    override fun run(
        from: TypedRepresentation,
        context: CompilationContext,
    ): WasmRepresentation? {
        TODO("Not yet implemented")
    }
>>>>>>> master
}
