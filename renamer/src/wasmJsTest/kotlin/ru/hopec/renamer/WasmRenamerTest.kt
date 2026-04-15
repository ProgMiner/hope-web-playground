package ru.hopec.ru.hopec.renamer

import ru.hopec.core.CompilationContext
import ru.hopec.parser.TreeSitterRepresentation
import ru.hopec.parser.treesitter.parseHope
import ru.hopec.renamer.AstNode
import ru.hopec.renamer.RenamerPass
import kotlin.test.Test
import kotlin.test.assertTrue

class WasmRenamerTest {

    //TODO: эти тесты пока не работают, смотреть в jvmTest
    @Test
    fun `test function equation`() {
        val code = "--- ( x ) <= 42"
        val parsed = parseHope("../tree-sitter-hope/tree-sitter-hope.wasm", code)
        val treeSitterRep = TreeSitterRepresentation(parsed)
        val pass = RenamerPass()


        val context = CompilationContext()
        val res = pass.run(treeSitterRep, context) ?: error("failed to run")

        assertTrue(res.topLevelNodes.first() is AstNode.FunctionEquation, "Should be parsed as function equation");
        val equation = res.topLevelNodes.first() as AstNode.FunctionEquation
        assertTrue(equation.pattern is AstNode.IdentPattern, "Should be parsed as ident pattern")
        assertTrue(equation.body is AstNode.Ident)
        println(equation.body.name)
    }
}
