package ru.hopec.desugarer

import kotlinx.coroutines.test.runTest
import ru.hopec.core.CompilationContext
import ru.hopec.renamer.AstNode
import ru.hopec.renamer.Program
import ru.hopec.renamer.RenamedRepresentation
import kotlin.collections.emptyList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DesugarerTest {
    private fun startDesugarer(from: RenamedRepresentation): DesugaredRepresentation? {
        val context = CompilationContext()
        return try {
            DesugarerPass.run(from, context)
        } catch (e: Throwable) {
            println(e.message)
            null
        }
    }

    private val charType = AstNode.NamedType("char", emptyList())
    private val numType = AstNode.NamedType("num", emptyList())

    @Test
    fun `test function declaration`() =
        runTest {
            val program =
                RenamedRepresentation(
                    Program(
                        listOf(
                            AstNode.FunctionDeclaration(
                                "f",
                                emptyList(),
                                emptyList(),
                                AstNode.FunctionalType(
                                    charType,
                                    numType,
                                ),
                            ),
                        ),
                    ),
                )
            val desugared = startDesugarer(program)
            assertNotNull(desugared)
        }

    private val typeA =
        AstNode.DataDeclaration(
            "A",
            emptyList(),
            listOf(
                "new" to null,
                "fromChar" to charType,
            ),
        )

    @Test
    fun `test data declaration`() =
        runTest {
            val program =
                RenamedRepresentation(
                    Program(
                        listOf(
                            typeA,
                        ),
                    ),
                )
            val desugared = startDesugarer(program)
            assertNotNull(desugared)
        }

    @Test
    fun `test overload`() =
        runTest {
            val program =
                RenamedRepresentation(
                    Program(
                        listOf(
                            typeA,
                            AstNode.FunctionDeclaration(
                                "new",
                                emptyList(),
                                emptyList(),
                                numType,
                            ),
                            AstNode.FunctionDeclaration(
                                "g",
                                listOf(
                                    AstNode.FunctionEquation(
                                        AstNode.VariablePattern("x"),
                                        AstNode.ApplicationExpr(
                                            AstNode.IdentExpr("new"),
                                            emptyList(),
                                        ),
                                    ),
                                ),
                                emptyList(),
                                numType,
                            ),
                        ),
                    ),
                )
            val desugared = startDesugarer(program) ?: error("desugarer error")
            assertEquals(
                DesugaredRepresentation.Declarations.Function(
                    DesugaredRepresentation.Expr.Lambda(
                        listOf(
                            DesugaredRepresentation.Expr.Lambda.Branch(
                                DesugaredRepresentation.Pattern.Variable("x"),
                                DesugaredRepresentation.Expr.Application(
                                    DesugaredRepresentation.Expr.Identifier(
                                        setOf(
                                            DesugaredRepresentation.Declarations.Function.Name.User(
                                                null,
                                                "new_2",
                                            ),
                                            DesugaredRepresentation.Declarations.Function.Name.Constructor(
                                                DesugaredRepresentation.Declarations.Data.Name.Defined(
                                                    null,
                                                    "A",
                                                ),
                                                "new_0",
                                            ),
                                        ),
                                    ),
                                    emptyList(),
                                ),
                            ),
                        ),
                    ),
                    DesugaredRepresentation.PolymorphicType(
                        DesugaredRepresentation.Type.Data(
                            DesugaredRepresentation.Declarations.Data.Name.Core.Num,
                            emptyList(),
                        ),
                        0,
                    ),
                ),
                desugared.topLevel.functions[
                    DesugaredRepresentation.Declarations.Function.Name
                        .User(null, "g_3"),
                ],
            )
        }

    @Test
    fun `test pattern constructor`() =
        runTest {
            val program =
                RenamedRepresentation(
                    Program(
                        listOf(
                            typeA,
                            AstNode.FunctionDeclaration(
                                "g",
                                listOf(
                                    AstNode.FunctionEquation(
                                        AstNode.VariablePattern("new"),
                                        AstNode.ApplicationExpr(
                                            AstNode.IdentExpr("new"),
                                            emptyList(),
                                        ),
                                    ),
                                ),
                                emptyList(),
                                numType,
                            ),
                        ),
                    ),
                )
            val desugared = startDesugarer(program) ?: error("desugarer error")
            assertEquals(
                DesugaredRepresentation.Declarations.Function(
                    DesugaredRepresentation.Expr.Lambda(
                        listOf(
                            DesugaredRepresentation.Expr.Lambda.Branch(
                                DesugaredRepresentation.Pattern.Data(
                                    setOf(
                                        DesugaredRepresentation.Declarations.Function.Name.Constructor(
                                            DesugaredRepresentation.Declarations.Data.Name
                                                .Defined(null, "A"),
                                            "new_0",
                                        ),
                                    ),
                                    emptyList(),
                                ),
                                DesugaredRepresentation.Expr.Application(
                                    DesugaredRepresentation.Expr.Identifier(
                                        setOf(
                                            DesugaredRepresentation.Declarations.Function.Name.Constructor(
                                                DesugaredRepresentation.Declarations.Data.Name.Defined(
                                                    null,
                                                    "A",
                                                ),
                                                "new_0",
                                            ),
                                        ),
                                    ),
                                    emptyList(),
                                ),
                            ),
                        ),
                    ),
                    DesugaredRepresentation.PolymorphicType(
                        DesugaredRepresentation.Type.Data(
                            DesugaredRepresentation.Declarations.Data.Name.Core.Num,
                            emptyList(),
                        ),
                        0,
                    ),
                ),
                desugared.topLevel.functions[
                    DesugaredRepresentation.Declarations.Function.Name
                        .User(null, "g_2"),
                ],
            )
        }

    @Test
    fun `test local variables`() =
        runTest {
            val program =
                RenamedRepresentation(
                    Program(
                        listOf(
                            AstNode.FunctionDeclaration(
                                "g",
                                listOf(
                                    AstNode.FunctionEquation(
                                        AstNode.VariablePattern("x"),
                                        AstNode.LetExpr(
                                            AstNode.VariablePattern("x"),
                                            AstNode.ApplicationExpr(
                                                AstNode.IdentExpr("cons"),
                                                listOf(AstNode.IdentExpr("x")),
                                            ),
                                            AstNode.IdentExpr("x"),
                                        ),
                                    ),
                                ),
                                emptyList(),
                                numType,
                            ),
                        ),
                    ),
                )
            val desugared = startDesugarer(program) ?: error("desugarer error")

            val function =
                desugared.topLevel.functions[
                    DesugaredRepresentation.Declarations.Function.Name
                        .User(null, "g_0"),
                ] ?: error("function not found")
            val branch = function.lambda.branches.first()
            val let = branch.body as DesugaredRepresentation.Expr.Let
            val matcher = let.matcher as DesugaredRepresentation.Expr.Application
            val arg = matcher.args.first() as DesugaredRepresentation.Expr.Variable
            assertEquals(1, arg.binder)
            val body = let.body as DesugaredRepresentation.Expr.Variable
            assertEquals(1, body.binder)
        }

    @Test
    fun `test module use`() =
        runTest {
            val program =
                RenamedRepresentation(
                    Program(
                        listOf(
                            AstNode.FunctionDeclaration(
                                "g",
                                listOf(
                                    AstNode.FunctionEquation(
                                        AstNode.VariablePattern("x"),
                                        AstNode.IdentExpr("x"),
                                    ),
                                ),
                                emptyList(),
                                numType,
                            ),
                            AstNode.Module(
                                "module1",
                                listOf(
                                    AstNode.FunctionDeclaration(
                                        "g",
                                        listOf(
                                            AstNode.FunctionEquation(
                                                AstNode.VariablePattern("x"),
                                                AstNode.IdentExpr("x"),
                                            ),
                                        ),
                                        emptyList(),
                                        numType,
                                    ),
                                    AstNode.ConstantExportDeclaration(listOf("g")),
                                    // FIXME: сейчас порядок export не влияет, экспортируются все вхождения
                                    AstNode.FunctionDeclaration(
                                        "g",
                                        listOf(
                                            AstNode.FunctionEquation(
                                                AstNode.VariablePattern("x"),
                                                AstNode.IdentExpr("x"),
                                            ),
                                        ),
                                        emptyList(),
                                        numType,
                                    ),
                                ),
                            ),
                            AstNode.Module(
                                "module2",
                                listOf(
                                    AstNode.FunctionDeclaration(
                                        "f",
                                        listOf(
                                            AstNode.FunctionEquation(
                                                null,
                                                AstNode.IdentExpr("g"),
                                            ),
                                        ),
                                        emptyList(),
                                        numType,
                                    ),
                                    AstNode.ModuleUseDeclaration(
                                        listOf("module1"),
                                    ),
                                    AstNode.FunctionDeclaration(
                                        "f",
                                        listOf(
                                            AstNode.FunctionEquation(
                                                null,
                                                AstNode.IdentExpr("g"),
                                            ),
                                        ),
                                        emptyList(),
                                        numType,
                                    ),
                                ),
                            ),
                        ),
                    ),
                )
            val desugared = startDesugarer(program) ?: error("desugarer error")
        }
}
