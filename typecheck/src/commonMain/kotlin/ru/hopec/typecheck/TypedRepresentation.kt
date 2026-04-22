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
     */
    data class Declarations(val data: Map<Data.Name.Defined, Data>, val functions: Map<Declarations.Function.Name, Function>) {
        /**
         * Declared data representation
         *
         * @param constructors Map from constructors names to list of their arguments
         */
        data class Data(val constructors: Map<String, List<Type>>) {
            /** Resolved data name*/
            sealed interface Name {
                /** Core-defined types*/
                sealed interface Core : Name {
                    data object Char : Core
                    data object TruVal : Core
                    data object Num : Core
                    data object List : Core
                    data object Set : Core
                }

                /** User-defined types*/
                data class Defined(val module: String?, val name: String)
            }
        }

        /**
         * Function representation
         *
         * For convenience, represented as lambda expression
         */
        data class Function(val lambda: Expr.Lambda) {
            sealed interface Name {
                /**
                 * Core-defined functions like nil, cons, operators etc...
                 *
                 * Represented as string, because there are too much of them
                 */
                data class Core(val name: String)

                /** User defined functions (with **`dec`** keyword) */
                data class User(val module: String?, val name: String)

                /** Core and user defined data constructors*/
                data class Constructor(val data: Data.Name, val constructor: String)
            }
        }
    }

    sealed interface Expr {
        /** Type of subexpression in the context of the whole expression */
        val type: Type

        data class Application(override val type: Type, val left: Expr, val right: Expr) : Expr
        data class Identifier(override val type: Type, val name: String) : Expr
        data class Lambda(override val type: Type.Arrow, val branches: List<Branch>) : Expr {
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
                override val type = Type.Data.truval
            }

            data class Num(val value: Long) : Literal {
                override val type = Type.Data.num
            }

            data class Char(val value: kotlin.Char) : Literal {
                override val type = Type.Data.char
            }

            data class String(val value: kotlin.String) : Literal {
                override val type = Type.Data.string
            }
        }
    }

    sealed interface Pattern {
        /** Type of subpattern in the context of the whole expression */
        val type: Type

        data class Wildcard(override val type: Type) : Pattern
        data class Variable(override val type: Type, val name: String) : Pattern
        data class Data(
            override val type: Type.Data,
            val constructor: Declarations.Function.Name.Constructor,
            val args: List<Pattern>
        ) : Pattern

        data class NamedData(val name: String, val data: Data) : Pattern {
            override val type = data.type
        }
    }

    sealed interface Type {
        /** Type variable, represented as De Brujin index */
        data class Variable(val index: Int) : Type
        data class Arrow(val argument: Type, val result: Type) : Type
        data class Data(val constructor: Declarations.Data.Name, val args: List<Type>) : Type {
            companion object {
                val char = Data(Declarations.Data.Name.Core.Char, arrayListOf())
                val truval = Data(Declarations.Data.Name.Core.TruVal, arrayListOf())
                val num = Data(Declarations.Data.Name.Core.Num, arrayListOf())
                val string = Data(Declarations.Data.Name.Core.List, arrayListOf(char))

                fun list(arg: Type) = Data(Declarations.Data.Name.Core.List, arrayListOf(arg))
                fun set(arg: Type) = Data(Declarations.Data.Name.Core.Set, arrayListOf(arg))
            }
        }
    }
}
