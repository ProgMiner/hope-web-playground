package ru.hopec.ru.hopec.renamer

import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import ru.hopec.core.CompilationContext
import ru.hopec.parser.TreeSitterRepresentation
import ru.hopec.parser.treesitter.Language
import ru.hopec.parser.treesitter.Parser
import ru.hopec.parser.treesitter.Tree
import ru.hopec.renamer.AstNode
import ru.hopec.renamer.RenamedRepresentation
import ru.hopec.renamer.RenamerPass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RenamerPassTest {
    private suspend fun runRename(source: String): RenamedRepresentation? {
        val context = CompilationContext()
        val location = "kotlin/tree-sitter-hope.wasm"

        Parser.init().await<JsAny?>()
        val parser = Parser()
        val loaded = Language.load(location).await<Language>()
        parser.setLanguage(loaded)
        val tree: Tree = parser.parse(source.toJsString(), oldTree = null, options = null)

        val treeSitterRep = TreeSitterRepresentation(tree)
        val pass = RenamerPass()

        return pass.run(treeSitterRep, context)
    }

    @Test
    fun test_function_equation() =
        runTest {
                val code = "--- ( x ) <= 42"
                val res = runRename(code) ?: return@runTest
                assertTrue(res.topLevelNodes.first() is AstNode.FunctionEquation, "Should be parsed as function equation");
                val equation = res.topLevelNodes.first() as AstNode.FunctionEquation
                assertTrue(equation.pattern is AstNode.IdentPattern, "Should be parsed as ident pattern")
                assertTrue(equation.body is AstNode.Ident)
                println(equation.body.name)
            }
}
