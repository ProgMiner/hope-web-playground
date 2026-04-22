package ru.hopec.parser.test

import kotlinx.coroutines.test.runTest
import ru.hopec.core.CompilationContext
import ru.hopec.parser.TreeSitterRepresentation
import ru.hopec.parser.treesitter.parseHope
import ru.hopec.renamer.AstNode
import ru.hopec.renamer.Program
import ru.hopec.renamer.RenamedRepresentation
import ru.hopec.renamer.RenamerPass
import kotlin.collections.listOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RenamerTest {
    private suspend fun startRenamer(input: String) : RenamedRepresentation? {
        val parsed = parseHope(input)
        val treeSitterRep = TreeSitterRepresentation(parsed)
        val pass = RenamerPass()
        val context = CompilationContext()
        return pass.run(treeSitterRep, context)
    }

    suspend fun `function declaration`(): Program {
        val code = """
            dec f : WrongType
            --- f(x) <= x
        """.trimIndent()
        val res = startRenamer(code) ?: error("renamer failed")
        return res.program
    }

    @Test
    fun `test function declaration`() = runTest {
        val program = `function declaration`()
        assertEquals(
            program.list.filterIsInstance<AstNode.FunctionDeclaration>().size,
            1,
            "Should have one function declaration"
        )
    }

    @Test
    fun `test function declaration have equation`() = runTest {
        val program = `function declaration`()
        val functionDeclaration = program.list.filterIsInstance<AstNode.FunctionDeclaration>().first()
        assertEquals(
            functionDeclaration.equations.size,
            1,
            "Should have one function equation"
        )
    }

    suspend fun application(): Program {
        val code = """
            dec * : WrongType
            infix * : 6
            
            dec f : WrongType
            --- f(x) <= x * f 12
        """.trimIndent()
        val res = startRenamer(code) ?: error("renamer failed")
        return res.program
    }

    @Test
    fun `test application`() = runTest {
        val program = application()
        val functionDeclaration = program.list.filterIsInstance<AstNode.FunctionDeclaration>()[1]

        assertEquals(functionDeclaration.equations.first(),
            AstNode.FunctionEquation(
                pattern = AstNode.VarPattern(name="x"),
                body = AstNode.ApplicationExpr(
                    function = AstNode.IdentExpr(name="*"),
                    arguments =
                        listOf(
                            AstNode.IdentExpr(name = "x"),
                            AstNode.ApplicationExpr(
                                function = AstNode.IdentExpr(name = "f"),
                                arguments = listOf( AstNode.IdentExpr(name = "12") )
                            )
                        )
                )
            )
        )
    }

    suspend fun `complex pattern`(): Program {
        val code = """
            infix <::>, + : 6
            
            dec f : WrongType
            --- f(x + xs, a) <= a + x
        """.trimIndent()
        // TODO: Грамматика не позволяет использовать инфиксные конструкторы в паттернах
        val res = startRenamer(code) ?: error("renamer failed")
        return res.program
    }

    @Test
    fun `test complex pattern`() = runTest {
        val program = `complex pattern`()
    }

//    @Test
//    fun `test binding pattern`() = runTest {
//        val code = "--- x@Test <= f"
//        val res = startRenamer(code) ?: error("renamer failed")
//        val list = res.program.list
//        val function = list.first() as AstNode.FunctionEquation
//        assertIs<AstNode.PatternExpression>(function.pattern)
//        assertIs<AstNode.Binding>(function.pattern.expr, "Pattern should be parsed as Binding")
//    }

//    @Test
//    fun `test list pattern`() = runTest {
//        val code = "--- [ x, y ] <= f x y"
//        val res = startRenamer(code) ?: error("renamer failed")
//        val list = res.program.list
//        val function = list.first() as AstNode.FunctionEquation
//        val pattern = function.pattern
//        assertIs<AstNode.PatternExpression>(pattern, "Pattern should be parsed as PatternExpression")
//        assertIs<AstNode.ListExpr>(pattern.expr, "Expression should be parsed as ListExpr")
//    }

//    @Test
//    fun `test multiple application`() = runTest {
//        val code = "--- x <= f (x (y z))"
//        val res = startRenamer(code) ?: error("renamer failed")
//        val list = res.program.list
//        val equation = list.first() as AstNode.FunctionEquation
//        assertTrue(equation.body is AstNode.Application)
//        val application = equation.body
//        assertEquals(application.arguments.size, 1, "Should be 1 element in arguments")
//        assertIs<AstNode.Tuple>(application.arguments[0], "First element should be tuple")
//        val tuple = application.arguments[0] as AstNode.Tuple
//        assertEquals(tuple.elements.size, 3, "Tuple should have 3 elements")
//    }

//    @Test
//    fun `test tuple with const`() = runTest {
//        val code = "--- x <= (42, \"test\", \'c\')"
//        val res = startRenamer(code) ?: error("renamer failed")
//        val list = res.program.list
//        val equation = list.first() as AstNode.FunctionEquation
//        assertTrue(equation.body is AstNode.Tuple)
//        val tuple = equation.body
//        assertEquals(tuple.elements.size, 3, "Tuple should have 3 elements")
//        // TODO: здесь должны были быть константы (?), но попадаются ident
//        //assertIs<AstNode.Decimal>(tuple.elements[0], "1st element should be decimal")
//        assertIs<AstNode.AstString>(tuple.elements[1], "2nd element should be string")
//        //assertIs<AstNode.AstChar>(tuple.elements[2], "3rd element should be char")
//    }

//    @Test
//    fun `test basic types`() = runTest {
//        val code = "data x == Int"
//        val res = startRenamer(code) ?: error("renamer failed")
//        val list = res.program.list
//        val data = list.first() as AstNode.DataDeclaration
//        assertIs<AstNode.IdentType>(data.type, "Type should be parsed as IdentType")
//    }
//
//    @Test
//    fun `test complex types`() = runTest {
//        val code = "data x == List String -> Int"
//        val res = startRenamer(code) ?: error("renamer failed")
//        val list = res.program.list
//        val data = list.first() as AstNode.DataDeclaration
//        assertIs<AstNode.PowType>(data.type, "Type should be parsed as PowType")
//        val binType = data.type
//        assertIs<AstNode.ApplicationTypes>(binType.type1, "Type should be parsed as ApplicationTypes")
//    }

//    @Test
//    fun `test infix declaration`() = runTest {
//        val code = "infix x, y : 10"
//        val res = startRenamer(code) ?: error("renamer failed")
//        val list = res.program.list
//        assertIs<AstNode.InfixDeclaration>(list.first(), "Statement should be parsed as InfixDeclaration")
//        val infix = list.first() as AstNode.InfixDeclaration
//        assertEquals(infix.priority, 10)
//        assertEquals(infix.rightAssoc, true)
//    }

//    @Test
//    fun `test lambda`() = runTest {
//        val code = """
//            --- x <= lambda [] => w
//                            | [y] => y
//        """.trimIndent()
//        val res = startRenamer(code) ?: error("renamer failed")
//        val list = res.program.list
//        assertIs<AstNode.FunctionEquation>(list.first(), "Statement should be parsed as FunctionEquation")
//        val func = list.first() as AstNode.FunctionEquation
//        assertIs<AstNode.Lambda>(func.body, "Expression should be parsed as Lambda")
//        assertEquals(func.body.branches.size, 2)
//    }

    @Test
    fun `test type`() = runTest {
        val code = "data x == empty ++ cons(x)"
        val res = startRenamer(code) ?: error("renamer failed")
        val list = res.program.list
    }

    @Test
    fun `test declaration and equation`() = runTest {
        val code = """
            module test
                dec f : String -> Long
                --- x <= toLong (length x)
            end
        """.trimIndent()

        val res = startRenamer(code) ?: error("renamer failed")
        assertNotNull(res)
    }

    @Test
    fun `test error`() = runTest {
        val noModuleName = """
            module
                --- c <= 10
            end
        """.trimIndent()
        val res = startRenamer(noModuleName)
        assertNull(res)

        val noFunctionName = """
            module test
                dec : String -> Long
            end
        """.trimIndent()
        val res2 = startRenamer(noFunctionName)
        assertNull(res2)
    }
}
