package ru.hopec.parser.test

import ru.hopec.core.CompilationContext
import ru.hopec.parser.TreeSitterRepresentation
import ru.hopec.parser.treesitter.findSharedLibrary
import ru.hopec.parser.treesitter.parseHope
import ru.hopec.renamer.AstNode
import ru.hopec.renamer.RenamedRepresentation
import ru.hopec.renamer.RenamerPass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class JVMRenamerTest {

    fun startRenamer(input: String) : RenamedRepresentation {
        val parsed = parseHope(findSharedLibrary(), input)
        val treeSitterRep = TreeSitterRepresentation(parsed)
        val pass = RenamerPass()
        val context = CompilationContext()
        return pass.run(treeSitterRep, context) ?: error("failed to run")
    }

    @Test
    fun `test function equation`() {
        val code = "--- ( x ) <= 42"
        val res = startRenamer(code)

        assertTrue(res.topLevelNodes.first() is AstNode.FunctionEquation, "Should be parsed as function equation");
        val equation = res.topLevelNodes.first() as AstNode.FunctionEquation
        assertTrue(equation.pattern is AstNode.IdentPattern, "Should be parsed as ident pattern")
        assertTrue(equation.body is AstNode.Ident)
        println(equation.body.name)
    }

    @Test
    fun `test application`() {
        val code = "--- ( x ) <= f x y"
        val res = startRenamer(code)

        assertTrue(res.topLevelNodes.first() is AstNode.FunctionEquation, "Should be parsed as function equation");
        val equation = res.topLevelNodes.first() as AstNode.FunctionEquation
        assertTrue(equation.pattern is AstNode.IdentPattern, "Should be parsed as ident pattern")
        assertTrue(equation.body is AstNode.Application)
        val application = equation.body
        assertTrue(application.function is AstNode.Ident, "Should be parsed as ident")
        assertEquals(application.arguments.size, 2, "Should be 2 elements in arguments")
        assertTrue(application.arguments[0] is AstNode.Ident, "First element should be ident")
        assertTrue(application.arguments[1] is AstNode.Ident, "Second element should be ident")
    }

    @Test
    fun `test list pattern`() {
        val code = "--- { x, y } <= f x y"
        val res = startRenamer(code)
        // TODO: Проблема в грамматике с pattern? Во всех сложных паттернах, сваливается к expression, не используя array/list...
    }

    @Test
    fun `test tuple`() {
        val code = "--- x <= f (x, y, z)"
        val res = startRenamer(code)
        assertTrue(res.topLevelNodes.first() is AstNode.FunctionEquation, "Should be parsed as function equation");
        val equation = res.topLevelNodes.first() as AstNode.FunctionEquation
        assertTrue(equation.body is AstNode.Application)
        val application = equation.body
        assertTrue(application.function is AstNode.Ident, "Should be parsed as ident")
        assertEquals(application.arguments.size, 1, "Should be 1 element in arguments")
        assertIs<AstNode.Tuple>(application.arguments[0], "First element should be tuple")
        val tuple = application.arguments[0] as AstNode.Tuple
        assertEquals(tuple.elements.size, 3, "Tuple should have 3 elements")
    }

    @Test
    fun `test const`() {
        val code = "--- x <= (42, \"test\", \'c\')"
        val res = startRenamer(code)
        assertTrue(res.topLevelNodes.first() is AstNode.FunctionEquation, "Should be parsed as function equation");
        val equation = res.topLevelNodes.first() as AstNode.FunctionEquation
        assertTrue(equation.body is AstNode.Tuple)
        val tuple = equation.body
        assertEquals(tuple.elements.size, 3, "Tuple should have 3 elements")
        // TODO: здесь должны были быть константы, но попадаются ident
        //assertIs<AstNode.Decimal>(tuple.elements[0], "1st element should be decimal")
        assertIs<AstNode.AstString>(tuple.elements[1], "2nd element should be string")
        //assertIs<AstNode.AstChar>(tuple.elements[2], "3rd element should be char")
    }
}
