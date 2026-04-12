package ru.hopec.parser.test

import com.goncalossilva.resources.Resource
import ru.hopec.core.CompilationContext
import ru.hopec.parser.TreeSitterRepresentation
import ru.hopec.parser.treesitter.TsTree
import ru.hopec.parser.treesitter.factory
import ru.hopec.parser.treesitter.findSharedLibrary
import ru.hopec.parser.treesitter.parseHope
import ru.hopec.renamer.AstNode
import ru.hopec.renamer.RenamerPass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JVMRenamerTest {
    @Test
    fun `test function equation`() {
        val code = "--- ( x ) <= 42"
        val parsed = parseHope(findSharedLibrary(), code)
        val treeSitterRep = TreeSitterRepresentation(parsed)
        val pass = RenamerPass()


        val context = CompilationContext()
        val res = pass.run(treeSitterRep, context) ?: error("failed to run")

        assertTrue(res.topLevelNodes.first() is AstNode.FunctionEquation, "Should be parsed as function equation");
        val equation = res.topLevelNodes.first() as AstNode.FunctionEquation
        assertTrue(equation.pattern is AstNode.IdentPattern, "Should be parsed as ident pattern")
        assertTrue(equation.body is AstNode.Ident)
        println(equation.body.name)
        assertNotNull(factory().loadLanguage(findSharedLibrary()))
    }
}