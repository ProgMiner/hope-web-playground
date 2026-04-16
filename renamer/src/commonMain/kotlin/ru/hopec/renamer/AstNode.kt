package ru.hopec.renamer

sealed interface AstNode {
    sealed interface TopLevelNode : AstNode
    data class Module(val name: String, val statements: List<Statement>) : AstNode, TopLevelNode
    sealed interface Statement : AstNode, TopLevelNode

    data class FunctionEquation(val pattern: Pattern, val body: Expr) : Statement
    data class FunctionDeclaration(val names: List<String>, val typeExpr: TypeExpr) : Statement
    data class DataDeclaration(val name: String, val params: List<String>, val type: TypeExpr) : Statement
    data class InfixDeclaration(val names: List<String>, val priority: Int, val rightAssoc: Boolean) : Statement
    data class TypeVaribleDeclaration(val types: List<String>) : Statement
    data class TypeExportDeclaration(val types: List<String>) : Statement
    data class ConstantExportDeclaration(val constants: List<String>) : Statement
    data class ModuleUseDeclaration(val modules: List<String>) : Statement

    sealed interface Expr : AstNode
    data class Decimal(val value: Int) : Expr
    data class Ident(val name: String) : Expr
    data class AstString(val string: String) : Expr
    data class AstChar(val char: Char) : Expr
    data class Application(val function: Expr, val arguments: List<Expr>) : Expr
    data class Tuple(val elements: List<Expr>) : Expr
    data class ListExpr(val list: List<Expr>): Expr
    data class SetExpr(val list: List<Expr>): Expr
    data class If(val condition: Expr, val thenBranch: Expr, val elseBranch: Expr) : Expr
    data class Let(val pattern: Pattern, val value: Expr, val body: Expr) : Expr
    data class Lambda(val branches: List<LambdaBranch>) : Expr
    data class LambdaBranch(val pattern: Pattern, val expression: Expr)

    sealed interface Pattern : AstNode
    data class Patterns(val patterns: List<Pattern>) : Pattern
    data class Wildcard(val placeholder: Boolean = true) : Pattern
    data class PatternExpression(val expr: Expr) : Pattern

    sealed interface TypeExpr : AstNode
    data class PowType(val type1: TypeExpr, val type2: TypeExpr) : TypeExpr
    data class SumType(val type1: TypeExpr, val type2: TypeExpr) : TypeExpr
    data class ProductType(val type1: TypeExpr, val type2: TypeExpr) : TypeExpr
    data class ApplicationTypes(val type: TypeExpr, val arguments: List<TypeExpr>) : TypeExpr
    data class IdentType(val name: String) : TypeExpr
}