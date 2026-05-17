package ru.hopec.desugarer

import ru.hopec.core.Representation

data class DesugaredRepresentation(val modules: Map<String, Module>, val topLevel: Declarations) :
    Representation {
    /**
     * Module representation
     */
    data class Module(val public: Declarations, val private: Declarations)

    data class Declarations(
        val data: Map<Data.Name.Defined, Data>,
        val functions: Map<Function.Name, Function>
    ) {
        data class Function(val lambda: Expr.Lambda, val type: PolymorphicType) {
            sealed interface Name {
                /**
                 * Core-defined functions like nil, cons, operators etc...
                 *
                 * Represented as string, because there are too much of them
                 */
                data class Core(val name: String) : Name

                /** User defined functions (with **`dec`** keyword) */
                data class User(val module: String?, val name: String) : Name

                /** Core and user defined data constructors*/
                data class Constructor(val data: Declarations.Data.Name, val constructor: String) : Name
            }
        }

        data class Data(val constructors: Map<String, List<Type>>, val boundTypeVariables: Int) {
            /** Resolved data name*/
            sealed interface Name {
                /** Core-defined types*/
                sealed interface Core : Name {
                    data object Char : Core
                    data object TruVal : Core
                    data object Num : Core
                    data object List : Core
                    data object Set : Core
                    data object Tuple : Core
                }

                /** User-defined types*/
                data class Defined(val module: String?, val name: String) : Name
            }
        }
    }

    sealed interface Expr {
        data class Application(val function: Expr, val args: List<Expr>) : Expr

        /**
         * Identifiers, defined outside of expression (i.e not variables)
         */
        data class Identifier(val name: Set<Declarations.Function.Name>) : Expr

        /**
         * Variables, defined in expression
         *
         * @param name -- name from RenamedRepresentation
         *
         * @param binder -- De Brujin index
         */
        data class Variable(val name: String, val binder: Int) : Expr
        data class Lambda(val branches: List<Branch>) : Expr {
            data class Branch(val pattern: Pattern, val body: Expr)
        }

        data class If(val condition: Expr, val positive: Expr, val negative: Expr) : Expr
        data class Let(val pattern: Pattern, val matcher: Expr, val body: Expr) : Expr
        sealed interface Literal : Expr {
            data class TruVal(val value: Boolean) : Literal
            data class Num(val value: Long) : Literal
            data class Char(val value: kotlin.Char) : Literal
            data class String(val value: kotlin.String) : Literal
        }
    }

    sealed interface Pattern {
        data object Wildcard : Pattern
        data class Variable(val name: String) : Pattern
        data class Data(
            val constructor: Set<Declarations.Function.Name.Constructor>,
            val args: List<Pattern>
        ) : Pattern

        data class NamedData(val name: String, val data: Data) : Pattern
    }

    data class PolymorphicType(val type: Type, val boundTypeVariables: Int)

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
                fun tuple(left: Type, right: Type) = Data(Declarations.Data.Name.Core.Tuple, arrayListOf(left, right))
            }
        }
    }
}