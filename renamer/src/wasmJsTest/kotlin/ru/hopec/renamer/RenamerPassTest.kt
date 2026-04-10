package ru.hopec.ru.hopec.renamer

import kotlinx.browser.window
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.w3c.fetch.RequestInit
import org.w3c.fetch.Response
import ru.hopec.core.CompilationContext
import ru.hopec.parser.TreeSitterRepresentation
import ru.hopec.parser.treesitter.Language
import ru.hopec.parser.treesitter.Parser
import ru.hopec.parser.treesitter.Tree
import ru.hopec.renamer.AstNode
import ru.hopec.renamer.RenamedRepresentation
import ru.hopec.renamer.RenamerPass
import kotlin.test.Test

class RenamerPassTest {
    private suspend fun runRename(source: String): RenamedRepresentation? {
        val context = CompilationContext()
        val location = "/tree-sitter-hope.wasm"

        Parser.init().await<JsAny?>()
        val parser = Parser()
        val loaded = Language.load(location).await<Language>()
        parser.setLanguage(loaded)
        val tree: Tree = parser.parse(source.toJsString(), oldTree = null, options = null)

        val treeSitterRep = TreeSitterRepresentation(tree)

        println(treeSitterRep.tree.rootNode.type)
        val pass = RenamerPass()

        return pass.run(treeSitterRep, context)
    }

    @Test
    fun testRename() {
        test()
    }

    @Test
    fun `test function equation`() =
        runTest {
            val job = launch {
                val code = "--- x <= 42"
                val res = runRename(code)
            }
            job.join()
//        assertNotNull(res)
//        val script = res.result as? CompilationResult.ScriptResult
//        assertNotNull(script, "Should be parsed as a script")
//
//        val equation = script.statements.first() as AstNode.FunctionEquation
//        assertEquals("x", equation.pattern)
//        assertTrue(equation.body is AstNode.IntLiteral)
        }
}

fun test() {
    js("console.log('Доступные файлы:', Object.keys(window.__karma__.files))")
}