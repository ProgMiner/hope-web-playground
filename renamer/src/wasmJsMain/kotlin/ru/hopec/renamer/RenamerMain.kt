package ru.hopec.renamer

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import ru.hopec.core.CompilationContext
import ru.hopec.parser.TreeSitterRepresentation
import ru.hopec.parser.treesitter.Language
import ru.hopec.parser.treesitter.Parser
import ru.hopec.parser.treesitter.Tree


fun main() {
    GlobalScope.launch {
        val source = "--- x <= 42"
        val context = CompilationContext()
        val location = "/home/arseniy/Work/projects/hope-web-playground/renamer/src/wasmJsTest/kotlin/ru/hopec/renamer/tree-sitter-hope.wasm"

        Parser.init().await<JsAny?>()
        val parser = Parser()
        val loaded = Language.load(location).await<Language>()
        parser.setLanguage(loaded)
        val tree: Tree = parser.parse(source.toJsString(), oldTree = null, options = null)

        val treeSitterRep = TreeSitterRepresentation(tree)


        println(treeSitterRep.tree.rootNode.type)
        val pass = RenamerPass()

        pass.run(treeSitterRep, context)
//        assertNotNull(res)
//        val script = res.result as? CompilationResult.ScriptResult
//        assertNotNull(script, "Should be parsed as a script")
//
//        val equation = script.statements.first() as AstNode.FunctionEquation
//        assertEquals("x", equation.pattern)
//        assertTrue(equation.body is AstNode.IntLiteral)
    }
}