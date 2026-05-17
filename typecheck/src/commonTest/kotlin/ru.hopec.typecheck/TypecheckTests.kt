package ru.hopec.typecheck

import ru.hopec.typecheck.TypedRepresentation.Declarations
import ru.hopec.typecheck.TypedRepresentation.Declarations.Data.Name.Core
import ru.hopec.typecheck.TypedRepresentation.Type
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

private fun polymorphic(type: Type): TypedRepresentation.PolymorphicType {
    fun maxBinder(type: Type): Int =
        when (type) {
            is Type.Variable -> type.index
            is Type.Arrow -> max(maxBinder(type.argument), maxBinder(type.result))
            is Type.Data -> type.args.maxOfOrNull(::maxBinder) ?: 0
        }

    return TypedRepresentation.PolymorphicType(type, maxBinder(type) + 1)
}

private fun typeVar(i: Int) = Type.Variable(i)

private infix fun Type.arrow(other: Type) = Type.Arrow(this, other)

private fun typedVar(
    type: Type,
    name: String,
) = TypedRepresentation.Expr.Variable(type, name)

private fun dsVar(
    name: String,
    binder: Int,
) = DesugaredRepresentation.Expr.Variable(name, binder)

private fun typedPatVar(
    type: Type,
    name: String,
) = TypedRepresentation.Pattern.Variable(type, name)

private fun dsPatVar(name: String) = DesugaredRepresentation.Pattern.Variable(name)

private fun branches(vararg branches: Pair<TypedRepresentation.Pattern, TypedRepresentation.Expr>) =
    branches.map {
        TypedRepresentation.Expr.Lambda.Branch(it.first, it.second)
    }

private val constructorTrue =
    Declarations.Function.Name.Constructor(
        Declarations.Data.Name.Core.TruVal,
        "true",
    )

private val constructorFalse =
    Declarations.Function.Name.Constructor(
        Declarations.Data.Name.Core.TruVal,
        "false",
    )

private val dsTrue = DesugaredRepresentation.Expr.Identifier(constructorTrue)
private val dsFalse = DesugaredRepresentation.Expr.Identifier(constructorFalse)
private val typedTrue = TypedRepresentation.Expr.Identifier(Type.Data.truval, constructorTrue)
private val typedFalse = TypedRepresentation.Expr.Identifier(Type.Data.truval, constructorFalse)
private val dsWild = DesugaredRepresentation.Pattern.Wildcard

private fun typedWild(type: Type) = TypedRepresentation.Pattern.Wildcard(type)

private val cons = Declarations.Function.Name.Constructor(Core.List, "cons")
private val nil = Declarations.Function.Name.Constructor(Core.List, "nil")
private val makeTuple = Declarations.Function.Name.Constructor(Core.Tuple, "#")

private fun dsBranches(vararg branches: Pair<DesugaredRepresentation.Pattern, DesugaredRepresentation.Expr>) =
    branches.map {
        DesugaredRepresentation.Expr.Lambda.Branch(it.first, it.second)
    }

private infix fun DesugaredRepresentation.Expr.dsAp(right: DesugaredRepresentation.Expr) =
    DesugaredRepresentation.Expr.Application(this, right)

private fun dsTuple(
    left: DesugaredRepresentation.Expr,
    right: DesugaredRepresentation.Expr,
) = (DesugaredRepresentation.Expr.Identifier(makeTuple) dsAp left) dsAp right

private fun dsTuplePat(
    left: DesugaredRepresentation.Pattern,
    right: DesugaredRepresentation.Pattern,
) = DesugaredRepresentation.Pattern.Data(makeTuple, listOf(left, right))

private fun tuplePat(
    left: TypedRepresentation.Pattern,
    right: TypedRepresentation.Pattern,
) = TypedRepresentation.Pattern.Data(Type.Data.tuple(left.type, right.type), makeTuple, listOf(left, right))

class TypecheckTests {
    private fun annotateGlobal(func: DesugaredRepresentation.Declarations.Function) = TypecheckingContext.runFunction(Signature.core, func)

    @Test
    fun smoke() {
        val desugId =
            DesugaredRepresentation.Declarations.Function(
                DesugaredRepresentation.Expr.Lambda(
                    dsBranches(dsPatVar("x") to dsVar("x", 0)),
                ),
                polymorphic(typeVar(0) arrow typeVar(0)),
            )
        val typedId =
            TypedRepresentation.Declarations.Function(
                TypedRepresentation.Expr.Lambda(
                    typeVar(0) arrow typeVar(0),
                    branches(
                        typedPatVar(typeVar(0), "x") to typedVar(typeVar(0), "x"),
                    ),
                ),
                1,
            )
        assertEquals(typedId, annotateGlobal(desugId))
    }

    @Test
    fun letPolymorphism() {
        val poly =
            DesugaredRepresentation.Declarations.Function(
                DesugaredRepresentation.Expr.Lambda(
                    dsBranches(
                        dsPatVar("x") to
                            DesugaredRepresentation.Expr.Let(
                                dsPatVar("idf"),
                                DesugaredRepresentation.Expr.Lambda(
                                    dsBranches(dsPatVar("x") to dsVar("x", 0)),
                                ),
                                dsTuple(dsVar("idf", 0) dsAp dsVar("x", 1), dsVar("idf", 0) dsAp dsTrue),
                            ),
                    ),
                ),
                polymorphic(typeVar(0) arrow Type.Data.tuple(typeVar(0), Type.Data.truval)),
            )

        val result = annotateGlobal(poly)
        assertNotNull(result)
        assertEquals(
            typeVar(1) arrow typeVar(1),
            (
                result.lambda.branches
                    .first()
                    .body as TypedRepresentation.Expr.Let
            ).matcher.type,
        )
    }

    @Test
    fun nestedLet() {
        val poly =
            DesugaredRepresentation.Declarations.Function(
                DesugaredRepresentation.Expr.Lambda(
                    dsBranches(
                        dsPatVar("x") to
                            DesugaredRepresentation.Expr.Let(
                                dsPatVar("flp"),
                                DesugaredRepresentation.Expr.Lambda(
                                    dsBranches(
                                        dsTuplePat(dsPatVar("z"), dsPatVar("y")) to
                                            dsTuple(
                                                DesugaredRepresentation.Expr.Let(
                                                    dsPatVar("idf"),
                                                    DesugaredRepresentation.Expr.Lambda(
                                                        dsBranches(dsPatVar("u") to dsVar("u", 0)),
                                                    ),
                                                    dsVar("idf", 0),
                                                ),
                                                dsVar("y", 0),
                                            ),
                                    ),
                                ),
                                dsTuple(
                                    dsVar("flp", 0),
                                    DesugaredRepresentation.Expr.Let(
                                        dsPatVar("v"),
                                        DesugaredRepresentation.Expr.Lambda(
                                            dsBranches(
                                                dsPatVar("p") to
                                                    dsVar(
                                                        "p",
                                                        0,
                                                    ),
                                            ),
                                        ),
                                        dsVar("v", 0),
                                    ),
                                ),
                            ),
                    ),
                ),
                polymorphic(
                    typeVar(0) arrow
                        Type.Data.tuple(
                            (
                                Type.Data.tuple(
                                    typeVar(1),
                                    typeVar(2),
                                ) arrow Type.Data.tuple(typeVar(3) arrow typeVar(3), typeVar(2))
                            ),
                            typeVar(1) arrow typeVar(1),
                        ),
                ),
            )

        val result = annotateGlobal(poly)
        assertNotNull(result)
    }

    @Test
    fun complexPattern() {
        val desugFunction =
            DesugaredRepresentation.Declarations.Function(
                DesugaredRepresentation.Expr.Lambda(
                    dsBranches(
                        DesugaredRepresentation.Pattern.NamedData(
                            "foo",
                            DesugaredRepresentation.Pattern.Data(
                                cons,
                                listOf(
                                    dsTuplePat(
                                        dsTuplePat(
                                            dsWild,
                                            DesugaredRepresentation.Pattern.Data(constructorTrue, listOf()),
                                        ),
                                        dsWild,
                                    ),
                                ),
                            ),
                        ) to dsTrue,
                    ),
                ),
                polymorphic(Type.Data.list(Type.Data.tuple(Type.Variable(0), Type.Data.truval)) arrow Type.Data.truval),
            )

        assertNotNull(annotateGlobal(desugFunction))
    }

    @Test
    fun moreSpecificTypeDefinition() {
        val desugFunction =
            DesugaredRepresentation.Declarations.Function(
                DesugaredRepresentation.Expr.Lambda(
                    dsBranches(
                        DesugaredRepresentation.Pattern.NamedData(
                            "foo",
                            DesugaredRepresentation.Pattern.Data(
                                cons,
                                listOf(
                                    dsTuplePat(
                                        dsTuplePat(
                                            dsWild,
                                            DesugaredRepresentation.Pattern.Data(constructorTrue, listOf()),
                                        ),
                                        dsWild,
                                    ),
                                ),
                            ),
                        ) to dsTuple(dsVar("foo", 0), dsFalse),
                    ),
                ),
                polymorphic(
                    Type.Data.list(
                        Type.Data.tuple(
                            Type.Data.num,
                            Type.Data.truval,
                        ),
                    ) arrow
                        Type.Data.tuple(
                            Type.Data.list(Type.Data.tuple(Type.Data.num, Type.Data.truval)),
                            Type.Data.truval,
                        ),
                ),
            )

        assertNotNull(annotateGlobal(desugFunction))
    }

    @Test
    fun multipleBranches() {
        val desugIsNil =
            DesugaredRepresentation.Declarations.Function(
                DesugaredRepresentation.Expr.Lambda(
                    dsBranches(
                        DesugaredRepresentation.Pattern.Data(cons, listOf(dsTuplePat(dsWild, dsWild))) to dsFalse,
                        DesugaredRepresentation.Pattern.Data(nil, listOf()) to dsTrue,
                    ),
                ),
                polymorphic(Type.Data.list(Type.Variable(0)) arrow Type.Data.truval),
            )
        val typedIsNil =
            TypedRepresentation.Declarations.Function(
                TypedRepresentation.Expr.Lambda(
                    Type.Data.list(Type.Variable(0)) arrow Type.Data.truval,
                    branches(
                        TypedRepresentation.Pattern.Data(
                            Type.Data.list(typeVar(0)),
                            cons,
                            listOf(tuplePat(typedWild(typeVar(0)), typedWild(Type.Data.list(typeVar(0))))),
                        ) to typedFalse,
                        TypedRepresentation.Pattern.Data(Type.Data.list(typeVar(0)), nil, listOf()) to typedTrue,
                    ),
                ),
                1,
            )
        assertEquals(typedIsNil, annotateGlobal(desugIsNil))
    }
}
