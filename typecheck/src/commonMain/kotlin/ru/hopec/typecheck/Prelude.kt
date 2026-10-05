package ru.hopec.typecheck

import ru.hopec.desugarer.DesugaredRepresentation
import ru.hopec.desugarer.DesugaredRepresentation.Declarations
import ru.hopec.desugarer.DesugaredRepresentation.Declarations.Data.Name.Core
import ru.hopec.desugarer.DesugaredRepresentation.Expr
import ru.hopec.desugarer.DesugaredRepresentation.Pattern
import ru.hopec.desugarer.DesugaredRepresentation.Predicate
import ru.hopec.desugarer.DesugaredRepresentation.Type
import ru.hopec.desugarer.consConstr
import ru.hopec.desugarer.eqClass
import ru.hopec.desugarer.equal
import ru.hopec.desugarer.falseConstr
import ru.hopec.desugarer.nilConstr
import ru.hopec.desugarer.trueConstr
import ru.hopec.desugarer.tupleConstr

internal object Prelude {
    val eqType = Type.Arrow(Type.Data.tuple(Type.Variable(0), Type.Variable(0)), Type.Data.truval)

    val classes: Map<Declarations.Class.Name, Declarations.Class> = mapOf(eqClass to Declarations.Class(mapOf(equal to eqType)))

    val primitiveInstances: Map<Pair<Declarations.Class.Name, Declarations.Data.Name>, Declarations.Function.Name> =
        listOf(Core.Num, Core.Char, Core.TruVal).associate { (eqClass to it) to equal }

    private fun ident(name: Declarations.Function.Name) = Expr.Identifier(setOf(name))

    private fun variable(
        name: String,
        binder: Int,
    ) = Expr.Variable(name, binder)

    private fun tuple(
        left: Expr,
        right: Expr,
    ) = Expr.Application(ident(tupleConstr), listOf(left, right))

    private fun tuplePattern(
        left: Pattern,
        right: Pattern,
    ) = Pattern.Data(setOf(tupleConstr), listOf(left, right))

    private fun equals(
        left: Expr,
        right: Expr,
    ) = Expr.Application(ident(equal), listOf(tuple(left, right)))

    private fun and(
        left: Expr,
        right: Expr,
    ) = Expr.If(left, right, ident(falseConstr))

    private val nil = Pattern.Data(setOf(nilConstr), emptyList())

    private fun cons(
        head: String,
        tail: String,
    ) = Pattern.Data(setOf(consConstr), listOf(tuplePattern(Pattern.Variable(head), Pattern.Variable(tail))))

    private val eqList =
        Declarations.Instance(
            eqClass,
            Type.Data.list(Type.Variable(0)),
            1,
            listOf(Predicate(eqClass, 0)),
            mapOf(
                equal to
                    Expr.Lambda(
                        listOf(
                            Expr.Lambda.Branch(tuplePattern(nil, nil), ident(trueConstr)),
                            Expr.Lambda.Branch(
                                tuplePattern(cons("x", "xs"), cons("y", "ys")),
                                and(
                                    equals(variable("x", 3), variable("y", 1)),
                                    equals(variable("xs", 2), variable("ys", 0)),
                                ),
                            ),
                            Expr.Lambda.Branch(Pattern.Wildcard, ident(falseConstr)),
                        ),
                    ),
            ),
        )

    private val eqTuple =
        Declarations.Instance(
            eqClass,
            Type.Data.tuple(Type.Variable(0), Type.Variable(1)),
            2,
            listOf(Predicate(eqClass, 0), Predicate(eqClass, 1)),
            mapOf(
                equal to
                    Expr.Lambda(
                        listOf(
                            Expr.Lambda.Branch(
                                tuplePattern(
                                    tuplePattern(Pattern.Variable("a"), Pattern.Variable("b")),
                                    tuplePattern(Pattern.Variable("c"), Pattern.Variable("d")),
                                ),
                                and(
                                    equals(variable("a", 3), variable("c", 1)),
                                    equals(variable("b", 2), variable("d", 0)),
                                ),
                            ),
                        ),
                    ),
            ),
        )

    val instances = listOf(eqList, eqTuple)
}

internal fun DesugaredRepresentation.withPrelude(): DesugaredRepresentation =
    copy(topLevel = topLevel.copy(instances = Prelude.instances + topLevel.instances))
