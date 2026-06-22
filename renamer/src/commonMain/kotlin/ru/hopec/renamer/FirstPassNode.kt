package ru.hopec.renamer

import ru.hopec.parser.treesitter.TsSyntaxNode

data class FirstPassProgram(
    val list: List<FirstPassNode.TopLevelNode>,
    val modules: Map<String, Map<String, Infix>>,
    val globalInfixes: Map<String, Infix>,
    val imported: Set<String>,
)

sealed class FirstPassNode(
    open val node: TsSyntaxNode,
) {
    sealed class TopLevelNode(
        override val node: TsSyntaxNode,
    ) : FirstPassNode(node)

    data class Module(
        override val node: TsSyntaxNode,
        val name: String,
        val statements: List<Statement>,
        val infixes: Map<String, Infix>,
    ) : TopLevelNode(node)

    sealed class Statement(
        override val node: TsSyntaxNode,
    ) : TopLevelNode(node) {
        data class NotParsed(
            override val node: TsSyntaxNode,
        ) : Statement(node)

        data class Error(
            override val node: TsSyntaxNode,
            val error: RenamerException,
        ) : Statement(node)

        data class FunctionDeclaration(
            override val node: TsSyntaxNode,
            val name: String,
            val boundVars: List<String>,
            val typeExpr: TypeExpr,
        ) : Statement(node)

        data class DataDeclaration(
            override val node: TsSyntaxNode,
            val name: String,
            val boundVars: List<String>,
            val dataConstructors: List<Pair<String, TypeExpr?>>,
        ) : Statement(node)

        data class InfixDeclaration(
            override val node: TsSyntaxNode,
            val operators: List<Pair<String, Infix>>,
        ) : Statement(node)

        data class ModuleUseDeclaration(
            override val node: TsSyntaxNode,
            val modules: List<String>,
        ) : Statement(node)

        data class ConstantExportDeclaration(
            override val node: TsSyntaxNode,
            val constants: List<String>,
        ) : Statement(node)
    }

    sealed interface TypeExpr {
        data class FunctionalType(
            val premise: TypeExpr,
            val result: TypeExpr,
        ) : TypeExpr

        data class ProductType(
            val left: TypeExpr,
            val right: TypeExpr,
        ) : TypeExpr

        data class NamedType(
            val type: String,
            val arguments: List<TypeExpr>,
        ) : TypeExpr

        data class VarType(
            val name: String,
        ) : TypeExpr
    }
}
