package ru.hopec.renamer

sealed interface AstNode {

    // или module, или statement
    sealed interface TopLevelNode : AstNode

    // module ... end
    data class Module(val name: String, val statements: List<Statement>) : AstNode, TopLevelNode

    sealed interface Statement : AstNode, TopLevelNode

    data class FunctionEquation(val pattern: Pattern, val body: Expr) : Statement
    data class FunctionDeclaration(val names: List<String>, val typeExpr: TypeExpr) : Statement

//    data class DataDeclaration
    // TODO: Добавить DataDeclaration, InfixDeclaration и тд

    sealed interface Expr : AstNode

    data class Decimal(val value: Int) : Expr
    data class Ident(val name: String) : Expr
    data class AstString(val string: String) : Expr
    data class AstChar(val char: Char) : Expr


    data class Application(val function: Expr, val arguments: List<Expr>) : Expr
    data class Tuple(val elements: List<Expr>) : Expr

    data class If(val condition: Expr, val thenBranch: Expr, val elseBranch: Expr) : Expr

    data class Let(val pattern: Pattern, val value: Expr, val body: Expr) : Expr

    sealed interface Pattern : AstNode
    data class Wildcard(val placeholder: Boolean = true) : Pattern
    data class IdentPattern(val name: String) : Pattern
    data class ArrayPattern(val array: List<Expr>) : Pattern
    data class ListPattern(val list: List<Expr>) : Pattern

    //TODO: реализовать алгебраические типы
    data class TypeExpr(val rawText: String) : AstNode
}