package ru.hopec.parser.test

import kotlinx.coroutines.test.runTest
import ru.hopec.core.CompilationContext
import ru.hopec.parser.TreeSitterRepresentation
import ru.hopec.parser.treesitter.parseHope
import ru.hopec.renamer.AstNode
import ru.hopec.renamer.RenamedRepresentation
import ru.hopec.renamer.RenamerPass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RenamerTest {

    suspend fun startRenamer(input: String) : RenamedRepresentation {
        val parsed = parseHope(input)
        val treeSitterRep = TreeSitterRepresentation(parsed)
        val pass = RenamerPass()
        val context = CompilationContext()
        return pass.run(treeSitterRep, context) ?: error("failed to run")
    }

    @Test
    fun `test function equation`() = runTest {
        val code = "--- x <= 42"
        val res = startRenamer(code)

        assertTrue(res.topLevelNodes.first() is AstNode.FunctionEquation, "Should be parsed as function equation");
        val equation = res.topLevelNodes.first() as AstNode.FunctionEquation
        assertTrue(equation.pattern is AstNode.PatternExpression, "Should be parsed as ident pattern")
        assertTrue(equation.body is AstNode.Ident)
        println(equation.body.name)
    }

    @Test
    fun `test application`() = runTest {
        val code = "--- x <= f x y"
        val res = startRenamer(code)

        assertTrue(res.topLevelNodes.first() is AstNode.FunctionEquation, "Should be parsed as function equation");
        val equation = res.topLevelNodes.first() as AstNode.FunctionEquation
        assertTrue(equation.pattern is AstNode.PatternExpression, "Should be parsed as ident pattern")
        assertTrue(equation.body is AstNode.Application)
        val application = equation.body
        assertTrue(application.function is AstNode.Ident, "Should be parsed as ident")
        assertEquals(application.arguments.size, 2, "Should be 2 elements in arguments")
        assertTrue(application.arguments[0] is AstNode.Ident, "First element should be ident")
        assertTrue(application.arguments[1] is AstNode.Ident, "Second element should be ident")
    }

    @Test
    fun `test multiple pattern`() = runTest {
        val code = "--- x y <= f x y"
        val res = startRenamer(code)
        val function = res.topLevelNodes.first() as AstNode.FunctionEquation
        assertIs<AstNode.Patterns>(function.pattern, "Pattern should be parsed as Patterns")
    }

    @Test
    fun `test list pattern`() = runTest {
        val code = "--- [ x, y ] <= f x y"
        val res = startRenamer(code)

        val function = res.topLevelNodes.first() as AstNode.FunctionEquation
        val pattern = function.pattern
        assertIs<AstNode.PatternExpression>(pattern, "Pattern should be parsed as PatternExpression")
        assertIs<AstNode.ListExpr>(pattern.expr, "Expression should be parsed as ListExpr")
        assertIs<AstNode.Ident>(pattern.expr.list[0], "Expression should be parsed as Ident")
        assertIs<AstNode.Ident>(pattern.expr.list[1], "Expression should be parsed as Ident")
    }

    @Test
    fun `test tuple`() = runTest {
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
    fun `test const`() = runTest {
        val code = "--- x <= (42, \"test\", \'c\')"
        val res = startRenamer(code)
        assertTrue(res.topLevelNodes.first() is AstNode.FunctionEquation, "Should be parsed as function equation")
        val equation = res.topLevelNodes.first() as AstNode.FunctionEquation
        assertTrue(equation.body is AstNode.Tuple)
        val tuple = equation.body
        assertEquals(tuple.elements.size, 3, "Tuple should have 3 elements")
        // TODO: здесь должны были быть константы, но попадаются ident
        //assertIs<AstNode.Decimal>(tuple.elements[0], "1st element should be decimal")
        assertIs<AstNode.AstString>(tuple.elements[1], "2nd element should be string")
        //assertIs<AstNode.AstChar>(tuple.elements[2], "3rd element should be char")
    }

    @Test
    fun `test data declaration`() = runTest {
        val code = "data x == Int"
        val res = startRenamer(code)
        assertTrue(res.topLevelNodes.first() is AstNode.DataDeclaration, "Should be parsed as data declaration")
    }

    @Test
    fun `test basic types`() = runTest {
        val code = "data x == Int"
        val res = startRenamer(code)
        val data = res.topLevelNodes.first() as AstNode.DataDeclaration
        assertIs<AstNode.IdentType>(data.type, "Type should be parsed as IdentType")
    }

    @Test
    fun `test complex types`() = runTest {
        val code = "data x == List String -> Int"
        val res = startRenamer(code)
        val data = res.topLevelNodes.first() as AstNode.DataDeclaration
        assertIs<AstNode.BinaryType>(data.type, "Type should be parsed as BinaryType")
        val binType = data.type
        assertIs<AstNode.ApplicationTypes>(binType.type1, "Type should be parsed as ApplicationTypes")
    }

}

