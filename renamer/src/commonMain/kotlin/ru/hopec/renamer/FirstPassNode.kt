package ru.hopec.renamer

import ru.hopec.parser.treesitter.TsSyntaxNode

data class FirstPassProgram(
    val list: List<FirstPassNode.TopLevelNode>,
    val modules: Map<String, Map<String, Infix>>,
    val globalInfixes: Map<String, Infix>,
    val importedFiles: Set<String>,
)

sealed interface FirstPassNode {
    sealed interface TopLevelNode : AstNode

    data class Module(
        val name: String,
        val statements: List<Statement>,
        val infixes: Map<String, Infix>,
    ) : TopLevelNode

    sealed interface Statement : TopLevelNode {
        data class NotParsed(
            val node: TsSyntaxNode,
        ) : Statement

        data class Error(
            val error: RenamerException,
        ) : Statement

        data class FunctionDeclaration(
            val name: String,
            val boundVars: List<String>,
            val typeExpr: TypeExpr,
        ) : Statement

        data class DataDeclaration(
            val name: String,
            val boundVars: List<String>,
            val dataConstructors: List<Pair<String, TypeExpr?>>,
        ) : Statement

        data class InfixDeclaration(
            val operators: List<Pair<String, Infix>>,
        ) : Statement

        data class ModuleUseDeclaration(
            val modules: List<String>,
        ) : Statement

        data class ConstantExportDeclaration(
            val constants: List<String>,
        ) : Statement
    }

    sealed interface TypeExpr : AstNode {
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
