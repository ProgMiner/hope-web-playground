package ru.hopec.typecheck

import ru.hopec.desugarer.Desugarer
import ru.hopec.renamer.RenamedRepresentation
import ru.hopec.typecheck.TypedRepresentation.Declarations.Data.Name as TargetDataName
import ru.hopec.typecheck.TypedRepresentation.Declarations.Function.Name as TargetFunctionName
import ru.hopec.desugarer.DesugaredRepresentation as Ds

internal object DesugaredConverter {
    fun convert(renamed: RenamedRepresentation): DesugaredRepresentation {
        val desugared = Desugarer().renamedToDesugared(renamed)
        return DesugaredRepresentation(
            modules = desugared.modules.mapValues { (_, module) -> convertModule(module) },
            topLevel = convertDeclarations(desugared.topLevel),
        )
    }

    private fun convertModule(module: Ds.Module): DesugaredRepresentation.Module =
        DesugaredRepresentation.Module(
            public = convertDeclarations(module.public),
            private = convertDeclarations(module.private),
        )

    private fun convertDeclarations(declarations: Ds.Declarations): DesugaredRepresentation.Declarations =
        DesugaredRepresentation.Declarations(
            data =
                declarations.data.entries.associate { (name, data) ->
                    convertDefinedName(name) to convertData(data)
                },
            functions =
                declarations.functions.entries.associate { (name, function) ->
                    convertFunctionName(name) to convertFunction(function)
                },
        )

    private fun convertFunction(function: Ds.Declarations.Function): DesugaredRepresentation.Declarations.Function =
        DesugaredRepresentation.Declarations.Function(
            lambda = convertExpr(function.lambda) as DesugaredRepresentation.Expr.Lambda,
            type = convertPolyType(function.type),
        )

    private fun convertData(data: Ds.Declarations.Data): TypedRepresentation.Declarations.Data =
        TypedRepresentation.Declarations.Data(
            constructors = data.constructors.mapValues { (_, args) -> args.map(::convertType) },
            boundTypeVariables = data.boundTypeVariables,
        )

    private fun convertExpr(expr: Ds.Expr): DesugaredRepresentation.Expr =
        when (expr) {
            is Ds.Expr.Application -> {
                val function = convertExpr(expr.function)
                expr.args.fold(function) { accumulator, argument ->
                    DesugaredRepresentation.Expr.Application(accumulator, convertExpr(argument))
                }
            }

            is Ds.Expr.Identifier ->
                DesugaredRepresentation.Expr.Identifier(convertFunctionName(pickName(expr.name)))

            is Ds.Expr.Variable ->
                DesugaredRepresentation.Expr.Variable(expr.name, expr.binder)

            is Ds.Expr.Lambda ->
                DesugaredRepresentation.Expr.Lambda(
                    expr.branches.map { branch ->
                        DesugaredRepresentation.Expr.Lambda.Branch(
                            pattern = branch.pattern?.let(::convertPattern) ?: DesugaredRepresentation.Pattern.Wildcard,
                            body = convertExpr(branch.body),
                        )
                    },
                )

            is Ds.Expr.If ->
                DesugaredRepresentation.Expr.If(
                    condition = convertExpr(expr.condition),
                    positive = convertExpr(expr.positive),
                    negative = convertExpr(expr.negative),
                )

            is Ds.Expr.Let ->
                DesugaredRepresentation.Expr.Let(
                    pattern = convertPattern(expr.pattern),
                    matcher = convertExpr(expr.matcher),
                    body = convertExpr(expr.body),
                )

            is Ds.Literal -> convertLiteralExpr(expr)
        }

    private fun convertLiteralExpr(literal: Ds.Literal): DesugaredRepresentation.Expr.Literal =
        when (literal) {
            is Ds.Literal.Num -> DesugaredRepresentation.Expr.Literal.Num(literal.value)
            is Ds.Literal.Char -> DesugaredRepresentation.Expr.Literal.Char(literal.value)
            is Ds.Literal.String -> DesugaredRepresentation.Expr.Literal.String(literal.value)
            is Ds.Literal.TruVal -> DesugaredRepresentation.Expr.Literal.TruVal(literal.value)
        }

    private fun convertPattern(pattern: Ds.Pattern): DesugaredRepresentation.Pattern =
        when (pattern) {
            Ds.Pattern.Wildcard -> DesugaredRepresentation.Pattern.Wildcard

            is Ds.Pattern.Variable -> DesugaredRepresentation.Pattern.Variable(pattern.name)

            is Ds.Pattern.Data ->
                DesugaredRepresentation.Pattern.Data(
                    constructor = convertConstructorName(pickConstructor(pattern.constructor)),
                    args = pattern.args.map(::convertPattern),
                )

            is Ds.Pattern.NamedData ->
                DesugaredRepresentation.Pattern.NamedData(
                    name = pattern.name,
                    data = convertPattern(pattern.data) as DesugaredRepresentation.Pattern.Data,
                )

            is Ds.Literal.TruVal ->
                DesugaredRepresentation.Pattern.Data(
                    constructor =
                        TargetFunctionName.Constructor(
                            TargetDataName.Core.TruVal,
                            if (pattern.value) "true" else "false",
                        ),
                    args = emptyList(),
                )

            is Ds.Literal.Num, is Ds.Literal.Char, is Ds.Literal.String ->
                throw IllegalStateException("Literal patterns other than truval are not supported by the type checker")
        }

    private fun convertPolyType(type: Ds.PolymorphicType): TypedRepresentation.PolymorphicType =
        TypedRepresentation.PolymorphicType(convertType(type.type), type.boundTypeVariables)

    private fun convertType(type: Ds.Type): TypedRepresentation.Type =
        when (type) {
            is Ds.Type.Variable -> TypedRepresentation.Type.Variable(type.index)
            is Ds.Type.Arrow -> TypedRepresentation.Type.Arrow(convertType(type.argument), convertType(type.result))
            is Ds.Type.Data -> TypedRepresentation.Type.Data(convertDataName(type.constructor), type.args.map(::convertType))
        }

    private fun convertFunctionName(name: Ds.Declarations.Function.Name): TargetFunctionName =
        when (name) {
            is Ds.Declarations.Function.Name.Core -> TargetFunctionName.Core(name.name)
            is Ds.Declarations.Function.Name.User -> TargetFunctionName.User(name.module, name.name)
            is Ds.Declarations.Function.Name.Constructor -> convertConstructorName(name)
        }

    private fun convertConstructorName(name: Ds.Declarations.Function.Name.Constructor): TargetFunctionName.Constructor =
        TargetFunctionName.Constructor(convertDataName(name.data), name.constructor)

    private fun convertDataName(name: Ds.Declarations.Data.Name): TargetDataName =
        when (name) {
            Ds.Declarations.Data.Name.Core.Char -> TargetDataName.Core.Char
            Ds.Declarations.Data.Name.Core.TruVal -> TargetDataName.Core.TruVal
            Ds.Declarations.Data.Name.Core.Num -> TargetDataName.Core.Num
            Ds.Declarations.Data.Name.Core.List -> TargetDataName.Core.List
            Ds.Declarations.Data.Name.Core.Set -> TargetDataName.Core.Set
            Ds.Declarations.Data.Name.Core.Tuple -> TargetDataName.Core.Tuple
            is Ds.Declarations.Data.Name.Defined -> convertDefinedName(name)
        }

    private fun convertDefinedName(name: Ds.Declarations.Data.Name.Defined): TargetDataName.Defined =
        TargetDataName.Defined(name.module, name.name)

    private fun pickName(names: Set<Ds.Declarations.Function.Name>): Ds.Declarations.Function.Name =
        names.firstOrNull() ?: throw IllegalStateException("Identifier has no resolved candidates")

    private fun pickConstructor(
        names: Set<Ds.Declarations.Function.Name.Constructor>,
    ): Ds.Declarations.Function.Name.Constructor =
        names.firstOrNull() ?: throw IllegalStateException("Constructor pattern has no resolved candidates")
}
