package ru.hopec.renamer.cst

import ru.hopec.renamer.AstNode
import ru.hopec.renamer.AstNode.Pattern
import ru.hopec.renamer.AstNode.TypeExpr

sealed interface CstNode {
    sealed interface TopLevelNode : CstNode
    data class Module(val name: String, val statements: List<Statement>) : TopLevelNode
    sealed interface Statement : TopLevelNode


    data class FunctionEquation(val pattern: Pattern, val body: Expr) : Statement
    data class FunctionDeclaration(val names: List<String>, val typeExpr: TypeExpr) : Statement
    data class DataDeclaration(val name: String, val params: List<String>, val type: TypeExpr) : Statement
//    data class InfixDeclaration(val names: List<String>, val priority: Int, val rightAssoc: Boolean) : AstNode.Statement

    sealed interface Expr : CstNode
    data class Decimal(val value: Int) : Expr
    data class Ident(val name: String) : Expr
    data class Binding(val name: String, val bind: String) : Expr
    data class AstString(val string: String) : Expr
    data class AstChar(val char: Char) : Expr


    data class Operator(val function: AstNode.Expr, val arguments: List<Expr>) : Expr
    data class Operand(val operand: Expr) : Expr
    data class Tuple(val elements: List<Expr>) : Expr
}