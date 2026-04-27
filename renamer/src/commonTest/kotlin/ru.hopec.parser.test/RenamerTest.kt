package ru.hopec.parser.test

import kotlinx.coroutines.test.runTest
import ru.hopec.core.CompilationContext
import ru.hopec.parser.TreeSitterRepresentation
import ru.hopec.parser.treesitter.parseHope
import ru.hopec.renamer.AstNode
import ru.hopec.renamer.AstNode.FunctionDeclaration
import ru.hopec.renamer.Program
import ru.hopec.renamer.RenamedRepresentation
import ru.hopec.renamer.RenamerPass
import kotlin.collections.get
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
            program.list.filterIsInstance<FunctionDeclaration>().size,
            1,
            "Should have one function declaration"
        )
    }

    @Test
    fun `test function declaration have equation`() = runTest {
        val program = `function declaration`()
        val functionDeclaration = program.list.filterIsInstance<FunctionDeclaration>().first()
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
        val functionDeclaration = program.list.filterIsInstance<FunctionDeclaration>()[1]

        assertEquals(functionDeclaration.equations.first(),
            AstNode.FunctionEquation(
                pattern = AstNode.BindingPattern(pattern = AstNode.WildcardPattern, bindName = "x"),
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
            dec f : WrongType
            --- f(g(a :: l)) <= a 
        """.trimIndent()
        val res = startRenamer(code) ?: error("renamer failed")
        return res.program
    }

    @Test
    fun `test complex pattern`() = runTest {
        val program = `complex pattern`()
        val functionDeclaration = program.list.filterIsInstance<FunctionDeclaration>()[0]
        assertEquals(functionDeclaration.equations.first(),
            AstNode.FunctionEquation(
                pattern = AstNode.ConstructorPattern(
                    constructor = "g",
                    arguments = listOf (
                        AstNode.ConstructorPattern(
                            constructor = "::",
                            arguments = listOf (
                                AstNode.BindingPattern(pattern = AstNode.WildcardPattern, bindName = "a"),
                                AstNode.BindingPattern(pattern = AstNode.WildcardPattern, bindName = "l")
                            )
                        )
                    )
                ), body = AstNode.IdentExpr(name = "a")
            )

        )
    }

    @Test
    fun `test binding pattern`() = runTest {
        val code = """
            dec f : WrongType
            --- f(Test @ a :: xs) <= x
        """.trimIndent()
        val res = startRenamer(code) ?: error("renamer failed")
    }

    @Test
    fun `test function overload`() = runTest {
        val code = """
            dec f : WrongType1
            --- f(x :: xs) <= x
            
            dec f : WrongType2
            --- f(a, b) <= a
        """.trimIndent()

        val res = startRenamer(code) ?: error("renamer failed")
        assertEquals(
            res.program,
            Program(
                list = listOf(
                    FunctionDeclaration(
                        name = "f", equations = listOf(AstNode.FunctionEquation(
                            pattern = AstNode.ConstructorPattern(
                                constructor = "::",
                                arguments = listOf(AstNode.BindingPattern(pattern = AstNode.WildcardPattern, bindName = "x"),
                                    AstNode.BindingPattern(pattern = AstNode.WildcardPattern, bindName = "xs")
                                )
                            ), body = AstNode.IdentExpr(name = "x")
                        )),
                        boundVars=emptyList(),
                        typeExpr= AstNode.NamedType(type = "WrongType1", arguments = emptyList())
                    ),
                    FunctionDeclaration(name="f", equations= listOf(
                        AstNode.FunctionEquation(
                            pattern = AstNode.TuplePattern(tuple = listOf(
                                AstNode.BindingPattern(pattern = AstNode.WildcardPattern, bindName = "a"),
                                AstNode.BindingPattern(pattern = AstNode.WildcardPattern, bindName = "b")
                            )),
                            body= AstNode.IdentExpr(name = "a")
                        )),
                        boundVars=emptyList(),
                        typeExpr= AstNode.NamedType(type = "WrongType2", arguments = emptyList())
                    )))
        )
    }

    @Test
    fun `test list pattern`() = runTest {
        val code = """
            dec f : WrongType
            --- f([x, y]) <= f x y
            """.trimIndent()
        val res = startRenamer(code) ?: error("renamer failed")
        val list = res.program.list
        val functionDeclaration = list.filterIsInstance<FunctionDeclaration>()[0]
        val pattern = functionDeclaration.equations[0].pattern
        assertEquals(pattern,
            AstNode.ConstructorPattern(
                constructor = "::",
                arguments = listOf(
                    AstNode.BindingPattern(pattern = AstNode.WildcardPattern, bindName = "x"),
                    AstNode.ConstructorPattern(
                        constructor = "::",
                        arguments = listOf(
                            AstNode.BindingPattern(pattern = AstNode.WildcardPattern, bindName = "y"),
                            AstNode.BindingPattern(pattern = AstNode.WildcardPattern, bindName = "nil")
                        )
                    )
                )
            )
        )
    }

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

    @Test
    fun `test lambda`() = runTest {
        val code = """
            dec f : WrongType
            --- f(x) <= lambda nil => error
                            | a :: xs => a
        """.trimIndent()
        val res = startRenamer(code) ?: error("renamer failed")
        val list = res.program.list
        val function = list.filterIsInstance<FunctionDeclaration>()[0]
        assertEquals(function.equations[0].body,
            AstNode.LambdaExpr(branches = listOf(
                    AstNode.LambdaBranch(
                        pattern = AstNode.BindingPattern(
                            pattern = AstNode.WildcardPattern,
                            bindName = "empty"
                    ),
                    expression = AstNode.IdentExpr(name = "error")
            ), AstNode.LambdaBranch(
                    pattern = AstNode.ConstructorPattern(
                        constructor = "::",
                        arguments = listOf(
                                AstNode.BindingPattern(pattern = AstNode.WildcardPattern, bindName = "a"),
                                AstNode.BindingPattern(pattern = AstNode.WildcardPattern, bindName = "xs"))
                        ), expression = AstNode.IdentExpr(name = "a")
                    )
                )
            )
        )
    }

    @Test
    fun `test data constructors`() = runTest {
        val code = "data x == empty ++ cons(x)"
        val res = startRenamer(code) ?: error("renamer failed")
        val list = res.program.list

        assertEquals(list[0],
            AstNode.DataDeclaration(name = "x",
                boundVars = emptyList(),
                dataConstructors =
                    listOf(Pair("empty", null), Pair("cons", AstNode.NamedType(type = "x", arguments = emptyList())))
            )
        )
    }

    @Test
    fun `test module use`() = runTest {
        val code = """
            module test
                dec <> : WrongType
                infix <> : 6
                --- a <> b <= a + b
                pubconst <>
            end
            
            module test2
                uses test
                dec f : WrongType
                --- f(x) <= a <> b
            end
        """.trimIndent()

        val res = startRenamer(code) ?: error("renamer failed")
        assertEquals(
            res.program.list[1],
            AstNode.Module(
                name = "test2",
                statements = listOf(
                    AstNode.ModuleUseDeclaration(modules = listOf("test")),
                    FunctionDeclaration(
                        name = "f",
                        equations = listOf(AstNode.FunctionEquation(
                            pattern = AstNode.BindingPattern(pattern = AstNode.WildcardPattern,
                            bindName = "x"
                        ),
                        body = AstNode.ApplicationExpr(function = AstNode.IdentExpr(name = "<>"),
                            arguments = listOf(AstNode.IdentExpr(name = "a"), AstNode.IdentExpr(name = "b"))
                        )
                        )),
                        boundVars = emptyList(), typeExpr= AstNode.NamedType(type = "WrongType", arguments = emptyList())
                    )
                )
            )
        )
    }

    @Test
    fun `test error`() = runTest {
        val noFunctionName = """
            module test
                dec : String -> Long
            end
        """.trimIndent()
        val res = startRenamer(noFunctionName) ?: error("renamer failed")
        assertIs<AstNode.Module>(res.program.list[0])
        assertIs<AstNode.Error>((res.program.list[0] as AstNode.Module).statements[0])
    }
}
