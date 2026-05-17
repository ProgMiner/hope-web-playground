package ru.hopec.desugarer

import ru.hopec.core.CompilationContext
import ru.hopec.renamer.RenamedRepresentation
import ru.hopec.renamer.RenamerPass

class DesugarerTest {
    private suspend fun startDesugarer(input: String) : RenamedRepresentation? {
        val parsed = parseHope(input)
        val treeSitterRep = TreeSitterRepresentation(parsed)
        val context = CompilationContext()
        return RenamerPass.run(treeSitterRep, context)
    }
}
