package ru.hopec.parser.test

import kotlinx.coroutines.test.runTest
import ru.hopec.core.CompilationContext
import ru.hopec.core.GlobalCompilationContext
import ru.hopec.core.StatusSeverity
import ru.hopec.parser.TreeSitterRepresentation
import ru.hopec.parser.treesitter.parseHope
import ru.hopec.renamer.AstNode
import ru.hopec.renamer.AstNode.FunctionDeclaration
import ru.hopec.renamer.Program
import ru.hopec.renamer.RenamedRepresentation
import ru.hopec.renamer.RenamerPass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RenamerTest {
    private suspend fun contextAfterPass(input: String): CompilationContext {
        val context = GlobalCompilationContext()
        runPass(input, context)
        return context
    }

    private suspend fun startRenamer(input: String): RenamedRepresentation {
        val context = GlobalCompilationContext()
        val result = runPass(input, context) ?: error("renamer failed")
        assertEquals(StatusSeverity.INFO, context.result().severity)
        return result
    }

    private suspend fun runPass(
        input: String,
        context: CompilationContext,
    ): RenamedRepresentation? = RenamerPass.run(tree(input), context)

    private suspend fun tree(input: String) = TreeSitterRepresentation(parseHope(input))

    suspend fun functionDeclaration(): Program =
        startRenamer(
            """
            dec f : WrongType
            --- f(x) <= x
            """.trimIndent(),
        ).program

    @Test
    fun `test function declaration`() =
        runTest {
            val program = functionDeclaration()
            assertEquals(
                1,
                program.list.filterIsInstance<FunctionDeclaration>().size,
                "Should have one function declaration",
            )
        }

    @Test
    fun `test function declaration have equation`() =
        runTest {
            val program = functionDeclaration()
            val functionDeclaration = program.list.filterIsInstance<FunctionDeclaration>().first()
            assertEquals(
                1,
                functionDeclaration.equations.size,
                "Should have one function equation",
            )
        }

    suspend fun badFunctionDeclaration() =
        contextAfterPass(
            """
            dec f : WrongType -> (typ ->)
            --- f(x) <= x
            """.trimIndent(),
        )

    @Test
    fun `bad function declaration has errors`() =
        runTest {
            assertEquals(StatusSeverity.ERROR, badFunctionDeclaration().result().severity)
        }

    suspend fun application(): Program =
        startRenamer(
            """
            dec * : WrongType
            infix * : 6

            dec f : WrongType
            --- f(x) <= x * f 12
            """.trimIndent(),
        ).program

    @Test
    fun `test application`() =
        runTest {
            val program = application()
            val functionDeclaration = program.list.filterIsInstance<FunctionDeclaration>()[1]

            assertEquals(
                AstNode.FunctionEquation(
                    pattern = AstNode.VariablePattern("x"),
                    body =
                        AstNode.ApplicationExpr(
                            function = AstNode.IdentExpr(name = "*"),
                            arguments =
                                listOf(
                                    AstNode.IdentExpr(name = "x"),
                                    AstNode.ApplicationExpr(
                                        function = AstNode.IdentExpr(name = "f"),
                                        arguments = listOf(AstNode.DecimalLiteral(value = 12)),
                                    ),
                                ),
                        ),
                ),
                functionDeclaration.equations.first(),
            )
        }

    suspend fun `complex pattern`(): Program =
        startRenamer(
            """
            dec f : WrongType
            --- f(g(a :: l)) <= a
            """.trimIndent(),
        ).program

    @Test
    fun `test complex pattern`() =
        runTest {
            val program = `complex pattern`()
            val functionDeclaration = program.list.filterIsInstance<FunctionDeclaration>()[0]
            assertEquals(
                AstNode.FunctionEquation(
                    pattern =
                        AstNode.ConstructorPattern(
                            constructor = "g",
                            arguments =
                                listOf(
                                    AstNode.ConstructorPattern(
                                        constructor = "::",
                                        arguments =
                                            listOf(
                                                AstNode.VariablePattern("a"),
                                                AstNode.VariablePattern("l"),
                                            ),
                                    ),
                                ),
                        ),
                    body = AstNode.IdentExpr(name = "a"),
                ),
                functionDeclaration.equations.first(),
            )
        }

    @Test
    fun `test const pattern`() =
        runTest {
            startRenamer(
                """
                dec f : WrongType
                --- f(42, 'c') <= a
                """.trimIndent(),
            )
        }

    @Test
    fun `test empty pattern`() =
        runTest {
            startRenamer(
                """
                dec f : WrongType
                --- f <= a
                --- f() <= a
                """.trimIndent(),
            )
        }

    @Test
    fun `test binding pattern`() =
        runTest {
            startRenamer(
                """
                dec f : WrongType
                --- f(Test @ a :: xs) <= x
                """.trimIndent(),
            )
        }

    @Test
    fun `test function overload`() =
        runTest {
            val code =
                """
                dec f : WrongType1
                --- f(x :: xs) <= x

                dec f : WrongType2
                --- f(a, b) <= a
                """.trimIndent()

            val res = startRenamer(code)
            assertEquals(
                Program(
                    list =
                        listOf(
                            FunctionDeclaration(
                                name = "f",
                                equations =
                                    listOf(
                                        AstNode.FunctionEquation(
                                            pattern =
                                                AstNode.ConstructorPattern(
                                                    constructor = "::",
                                                    arguments =
                                                        listOf(
                                                            AstNode.VariablePattern("x"),
                                                            AstNode.VariablePattern("xs"),
                                                        ),
                                                ),
                                            body = AstNode.IdentExpr(name = "x"),
                                        ),
                                    ),
                                boundVars = emptyList(),
                                typeExpr = AstNode.NamedType(type = "WrongType1", arguments = emptyList()),
                            ),
                            FunctionDeclaration(
                                name = "f",
                                equations =
                                    listOf(
                                        AstNode.FunctionEquation(
                                            pattern =
                                                AstNode.TuplePattern(
                                                    tuple =
                                                        listOf(
                                                            AstNode.VariablePattern("a"),
                                                            AstNode.VariablePattern("b"),
                                                        ),
                                                ),
                                            body = AstNode.IdentExpr(name = "a"),
                                        ),
                                    ),
                                boundVars = emptyList(),
                                typeExpr = AstNode.NamedType(type = "WrongType2", arguments = emptyList()),
                            ),
                        ),
                ),
                res.program,
            )
        }

    @Test
    fun `test list pattern`() =
        runTest {
            val list =
                startRenamer(
                    """
                    dec f : WrongType
                    --- f([x, y]) <= f (x, y)
                    """.trimIndent(),
                ).program.list
            val functionDeclaration = list.filterIsInstance<FunctionDeclaration>()[0]
            val pattern = functionDeclaration.equations[0].pattern
            assertEquals(
                AstNode.ConstructorPattern(
                    constructor = "::",
                    arguments =
                        listOf(
                            AstNode.VariablePattern("x"),
                            AstNode.ConstructorPattern(
                                constructor = "::",
                                arguments =
                                    listOf(
                                        AstNode.VariablePattern("y"),
                                        AstNode.VariablePattern("nil"),
                                    ),
                            ),
                        ),
                ),
                pattern,
            )
        }

    @Test
    fun `test tuple with const`() =
        runTest {
            val list =
                startRenamer(
                    """
                    dec f : WrongType
                    --- f(x) <= (42, "test", 'c', true)
                    """.trimIndent(),
                ).program.list
            val functionDeclaration = list.filterIsInstance<FunctionDeclaration>()[0]
            val tuple = functionDeclaration.equations[0].body
            assertEquals(
                AstNode.TupleExpr(
                    elements =
                        listOf(
                            AstNode.DecimalLiteral(value = 42),
                            AstNode.StringLiteral(string = "test"),
                            AstNode.CharLiteral(char = 'c'),
                            AstNode.TruvalLiteral(bool = true),
                        ),
                ),
                tuple,
            )
        }

    @Test
    fun `test lambda`() =
        runTest {
            val list =
                startRenamer(
                    """
                    dec f : WrongType
                    --- f(x) <= lambda nil => error
                                    | a :: xs => a
                    """.trimIndent(),
                ).program.list
            val function = list.filterIsInstance<FunctionDeclaration>()[0]
            assertEquals(
                AstNode.LambdaExpr(
                    branches =
                        listOf(
                            AstNode.LambdaBranch(
                                pattern = AstNode.VariablePattern("nil"),
                                expression = AstNode.IdentExpr(name = "error"),
                            ),
                            AstNode.LambdaBranch(
                                pattern =
                                    AstNode.ConstructorPattern(
                                        constructor = "::",
                                        arguments =
                                            listOf(
                                                AstNode.VariablePattern("a"),
                                                AstNode.VariablePattern("xs"),
                                            ),
                                    ),
                                expression = AstNode.IdentExpr(name = "a"),
                            ),
                        ),
                ),
                function.equations[0].body,
            )
        }

    @Test
    fun `test data constructors`() =
        runTest {
            val list = startRenamer("data x == empty ++ cons(x)").program.list

            assertEquals(
                AstNode.DataDeclaration(
                    name = "x",
                    boundVars = emptyList(),
                    dataConstructors =
                        listOf(
                            Pair("empty", null),
                            Pair("cons", AstNode.NamedType(type = "x", arguments = emptyList())),
                        ),
                ),
                list[0],
            )
        }

    @Test
    fun `test module use`() =
        runTest {
            val code =
                """
                module test
                    dec <> : WrongType
                    infix <> : 6
                    --- a <> b <= a :: b
                    pubconst <>
                end

                module test2
                    uses test
                    dec f : WrongType
                    --- f(x) <= a <> b
                end
                """.trimIndent()

            val res = startRenamer(code)
            assertEquals(
                AstNode.Module(
                    name = "test2",
                    statements =
                        listOf(
                            AstNode.ModuleUseDeclaration(modules = listOf("test")),
                            FunctionDeclaration(
                                name = "f",
                                equations =
                                    listOf(
                                        AstNode.FunctionEquation(
                                            pattern = AstNode.VariablePattern("x"),
                                            body =
                                                AstNode.ApplicationExpr(
                                                    function = AstNode.IdentExpr(name = "<>"),
                                                    arguments =
                                                        listOf(
                                                            AstNode.IdentExpr(name = "a"),
                                                            AstNode.IdentExpr(name = "b"),
                                                        ),
                                                ),
                                        ),
                                    ),
                                boundVars = emptyList(),
                                typeExpr = AstNode.NamedType(type = "WrongType", arguments = emptyList()),
                            ),
                        ),
                ),
                res.program.list[1],
            )
        }

    @Test
    fun `test error`() =
        runTest {
            val res =
                startRenamer(
                    """
                    module test
                        dec : String -> Long
                    end
                    """.trimIndent(),
                )
            assertIs<AstNode.Module>(res.program.list[0])
            assertIs<AstNode.Error>((res.program.list[0] as AstNode.Module).statements[0])
        }

    @Test
    fun `test example`() =
        runTest {
            startRenamer(
                """
                module ordered_trees
                  pubtype otree
                  pubconst empty, insert, flatten

                  data otree == empty ++ tip(num) ++ node(otree # num # otree)

                  dec insert : num#otree -> otree
                  dec flatten : otree -> list num

                  --- insert(n,empty) <= tip(n)
                  --- insert(n,tip(m))
                        <= n<m then node(tip(n),m,empty)
                               else node(empty,m,tip(n))
                  --- insert(n,node(t1,m,t2))
                        <= n<m then node(insert(n,t1),m,t2)
                               else node(t1,m,insert(n,t2))

                  --- flatten(empty) <= nil
                  --- flatten(tip(n)) <= [n]
                  --- flatten(node(t1,n,t2))
                        <= flatten(t1) <> (n :: flatten(t2))

                end


                module list_iterators
                  pubconst *, ##

                  typevar alpha, beta

                  dec * : (alpha->beta)#list alpha -> list beta
                  dec ## : (alpha#beta->beta)#(list alpha#beta)
                          -> beta

                  infix *, ## : 6

                  --- f * nil <= nil
                  --- f * (a :: al) <= (f a) :: (f * al)

                  --- g ## (nil,b) <= b
                  --- g ## (a :: al , b) <= g ## (al,g(a,b))

                end


                module tree_sort
                  pubconst sort
                  uses ordered_trees, list_iterators

                  dec sort : list num -> list num

                  --- sort(l) <= flatten(insert ## (l,empty))

                end
                """.trimIndent(),
            )
        }
}
