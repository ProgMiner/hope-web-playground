package ru.hopec.renamer

data class Program(val list: List<AstNode.TopLevelNode>)

sealed interface AstNode {
    sealed interface TopLevelNode : AstNode
    data class Module(val name: String, val statements: List<Statement>) : TopLevelNode

    sealed interface Statement : TopLevelNode
    object Error : Statement
    data class FunctionDeclaration(val name: String, val equations: MutableList<FunctionEquation>, val boundVars: List<String>, val typeExpr: TypeExpr) : Statement
    data class FunctionEquation(val pattern: Pattern, val body: Expr)
    data class DataDeclaration(val name: String, val boundVars: List<String>, val dataConstructors: List<Pair<String, TypeExpr?>>) : Statement
    data class TypeExportDeclaration(val types: List<String>) : Statement
    data class ConstantExportDeclaration(val constants: List<String>) : Statement
    data class ModuleUseDeclaration(val modules: List<String>) : Statement

    sealed interface Expr : AstNode
    data class IdentExpr(val name: String) : Expr
    data class ApplicationExpr(val function: Expr, val arguments: List<Expr>) : Expr
    data class TupleExpr(val elements: List<Expr>) : Expr
    data class ListExpr(val list: List<Expr>): Expr
    data class SetExpr(val list: List<Expr>): Expr
    data class IfExpr(val condition: Expr, val thenBranch: Expr, val elseBranch: Expr) : Expr
    data class LetExpr(val pattern: Pattern, val value: Expr, val body: Expr) : Expr
    data class LambdaExpr(val branches: List<LambdaBranch>) : Expr
    data class LambdaBranch(val pattern: Pattern, val expression: Expr)

    sealed interface Literal : Expr
    data class Decimal(val value: Int) : Literal
    data class Truval(val bool: Boolean) : Literal
    data class AstString(val string: String) : Literal
    data class AstChar(val char: Char) : Literal

    sealed interface Pattern : AstNode
    data class VarPattern(val name: String) : Pattern
    data class ConstructorPattern(val constructor: String, val arguments: List<Pattern>) : Pattern
    data class TuplePattern(val tuple: List<Pattern>) : Pattern
    data class BindingPattern(val pattern: Pattern, val bindName: String) : Pattern
    object WildcardPattern : Pattern


    sealed interface TypeExpr : AstNode
    data class FunctionalType(val premise: TypeExpr, val result: TypeExpr) : TypeExpr
    data class ProductType(val left: TypeExpr, val right: TypeExpr) : TypeExpr
    data class NamedType(val type: String, val arguments: List<TypeExpr>) : TypeExpr
    data class VarType(val name: String) : TypeExpr
}