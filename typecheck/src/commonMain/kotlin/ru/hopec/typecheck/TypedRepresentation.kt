package ru.hopec.typecheck

import ru.hopec.core.Representation

/**
 * Type-annotated representation of compilation unit
 *
 * @param modules Map from names of declared modules to their representations
 *
 * @param topLevel Declarations from the global scope
 */
data class TypedRepresentation(val modules: Map<String, Module>, val topLevel: Declarations) : Representation {
    data class Module(val public: Declarations, val private: Declarations)

    /**
     * Declarations of data and functions
     *
     * @param data Map from data names to their representations
     *
     * @param functions Map from function names to their representations.
     * Data constructors are also represented here as functions
     * */
    data class Declarations(val data: Map<String, Data>, val functions: Map<String, Function>)

    /**
     * Declared data representation
     *
     * @param constructors Map from constructors names to list of their arguments
     * */
    data class Data(val constructors: Map<String, List<Type>>)

    /**
     * Function representation
     *
     * For convenience, represented as lambda expression
     */
    data class Function(val lambda: Expr.Lambda)

    sealed interface Expr {
        /** Type of subexpression in the context of the whole expression */
        val type: Type

        data class Application(override val type: Type, val left: Expr, val right: Expr) : Expr
        data class Identifier(override val type: Type, val name: String) : Expr
        data class Lambda(override val type: Type.Function, val branches: List<Branch>) : Expr {
            data class Branch(val pattern: Pattern, val body: Expr)
        }

        data class If(val condition: Expr, val positive: Expr, val negative: Expr) : Expr {
            override val type = positive.type
        }

        data class Let(val pattern: Pattern, val matcher: Expr, val body: Expr) : Expr {
            override val type = body.type
        }

        sealed interface Literal : Expr {
            data class TruVal(val value: Boolean) : Literal {
                override val type = Type.Basic.Decimal
            }

            data class Decimal(val value: Long) : Literal {
                override val type = Type.Basic.Decimal
            }

            data class Char(val value: kotlin.Char) : Literal {
                override val type = Type.Basic.Char
            }

            data class String(val value: kotlin.String) : Literal {
                override val type = Type.Basic.String
            }
        }
    }

    sealed interface Pattern {
        /** Type of subpattern in the context of the whole expression */
        val type: Type

        data class Wildcard(override val type: Type) : Pattern
        data class Variable(override val type: Type, val name: String) : Pattern
        data class Data(override val type: Type.Data, val constructor: String, val args: List<Pattern>) : Pattern
        data class NamedData(val name: String, val data: Data) : Pattern {
            override val type = data.type
        }
    }

    sealed interface Type {
        data class Function(val argument: Type, val result: Type) : Type
        data class Data(val constructor: String, val args: List<Type>) : Type

        /** Type variable, represented as De Brujin index */
        data class Variable(val index: Int) : Type
        sealed interface Basic : Type {
            companion object {
                val String = Data("List", arrayListOf(Char))
            }

            data object TruVal : Basic
            data object Decimal : Basic
            data object Char : Basic
        }
    }
}
