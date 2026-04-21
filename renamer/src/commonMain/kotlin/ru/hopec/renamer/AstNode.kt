package ru.hopec.renamer

import ru.hopec.parser.treesitter.TsSyntaxNode
import ru.hopec.renamer.AstNode.Expr
import ru.hopec.renamer.AstNode.Pattern
import ru.hopec.renamer.AstNode.TypeExpr

data class Program(val list: List<AstNode.TopLevelNode>)

sealed interface AstNode {
    sealed interface TopLevelNode : AstNode
    data class Module(val name: String, val statements: List<Statement>) : TopLevelNode
    sealed interface Statement : TopLevelNode
    class Error : TopLevelNode

    data class FunctionDeclaration(val name: String, val equations: List<FunctionEquation>, val typeExpr: TypeExpr) : Statement
    data class FunctionEquation(val pattern: Pattern, val body: Expr)
    data class DataDeclaration(val name: String, val quantifier: List<String>, val dataConstructors: List<Pair<String, TypeExpr?>>) : Statement
    data class TypeExportDeclaration(val types: List<String>) : Statement
    data class ConstantExportDeclaration(val constants: List<String>) : Statement
    data class ModuleUseDeclaration(val modules: List<String>) : Statement

    sealed interface Expr : AstNode
    data class Ident(val name: String) : Expr
    data class Application(val function: Expr, val arguments: List<Expr>) : Expr
    data class Tuple(val elements: List<Expr>) : Expr
    data class ListExpr(val list: List<Expr>): Expr
    data class SetExpr(val list: List<Expr>): Expr
    data class If(val condition: Expr, val thenBranch: Expr, val elseBranch: Expr) : Expr
    data class Let(val pattern: Pattern, val value: Expr, val body: Expr) : Expr
    data class Lambda(val branches: List<LambdaBranch>) : Expr
    data class LambdaBranch(val pattern: Pattern, val expression: Expr)

    sealed interface Literal : Expr
    data class Decimal(val value: Int) : Literal
    data class AstString(val string: String) : Literal
    data class AstChar(val char: Char) : Literal

    sealed interface Pattern : AstNode
    data class PatternVar(val name: String) : Pattern
    data class PatternConstructor(val constructor: String, val arguments: List<Pattern>) : Pattern
    data class Binding(val pattern: Pattern, val bindName: String) : Pattern
    class Wildcard : Pattern


    sealed interface TypeExpr : AstNode
    data class FunctionalType(val premise: TypeExpr, val result: TypeExpr) : TypeExpr
    data class ProductType(val left: TypeExpr, val right: TypeExpr) : TypeExpr
    data class TypeApplication(val type: TypeExpr, val arguments: List<TypeExpr>) : TypeExpr
    data class OuterType(val name: String) : TypeExpr
    data class TypeVar(val name: String) : TypeExpr
}