package ru.hopec.desugarer

import ru.hopec.core.Representation

data class DesugaredRepresentation(
    val modules: Map<String, Module>,
    val topLevel: Declarations,
) : Representation {
    /**
     * Module representation
     */
    data class Module(
        val public: Declarations,
        val private: Declarations,
    )

    data class Declarations(
        val data: Map<Data.Name.Defined, Data>,
        val functions: Map<Function.Name, Function>,
        val classes: Map<Class.Name, Class> = emptyMap(),
        val instances: List<Instance> = emptyList(),
    ) {
        /**
         * Type class with a single type parameter
         *
         * Dictionary of a class with a single method is the method itself,
         * otherwise it is a tuple `(m1, (m2, .. mn))` of methods in declaration order
         *
         * @param methods -- method types, the class parameter is `Type.Variable(0)`
         */
        data class Class(
            val methods: Map<Function.Name, Type>,
        ) {
            sealed interface Name {
                val name: String

                /** Built-in classes like Eq */
                data class Core(
                    override val name: String,
                ) : Name

                data class Defined(
                    val module: String?,
                    override val name: String,
                ) : Name
            }
        }

        /**
         * Instance `class (head a1 .. an) given context`
         *
         * @param head -- `Type.Data` applied to distinct type variables bound by the instance
         *
         * @param methods -- implementations of class methods, their types are derived from the class
         */
        data class Instance(
            val cls: Class.Name,
            val head: Type.Data,
            val boundTypeVariables: Int,
            val context: List<Predicate>,
            val methods: Map<Function.Name, Expr.Lambda>,
        ) {
            /** Name of the function computing dictionary of this instance */
            val dictionary: Function.Name.User
                get() = instanceDictionaryName(cls, head.constructor)

            /** Name of the function implementing [method] in a class with several methods */
            fun method(method: Function.Name): Function.Name.User {
                val methodName =
                    when (method) {
                        is Function.Name.Core -> method.name
                        is Function.Name.User -> method.name
                        is Function.Name.Constructor -> method.constructor
                    }
                return Function.Name.User(null, "${dictionary.name}.$methodName")
            }
        }

        data class Function(
            val lambda: Expr.Lambda,
            val type: PolymorphicType,
        ) {
            sealed interface Name {
                /**
                 * Core-defined functions like nil, cons, operators etc...
                 *
                 * Represented as string, because there are too much of them
                 */
                data class Core(
                    val name: String,
                ) : Name

                /** User defined functions (with **`dec`** keyword) */
                data class User(
                    val module: String?,
                    val name: String,
                ) : Name

                /** Core and user defined data constructors*/
                data class Constructor(
                    val data: Declarations.Data.Name,
                    val constructor: String,
                ) : Name
            }
        }

        data class Data(
            val constructors: Map<String, List<Type>>,
            val boundTypeVariables: Int,
        ) {
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

                    data object Unit : Core
                }

                /** User-defined types*/
                data class Defined(
                    val module: String?,
                    val name: String,
                ) : Name
            }
        }
    }

    sealed interface Expr {
        data class Application(
            val function: Expr,
            val args: List<Expr>,
        ) : Expr

        /**
         * Identifiers, defined outside of expression (i.e not variables)
         */
        data class Identifier(
            val name: Set<Declarations.Function.Name>,
        ) : Expr

        /**
         * Variables, defined in expression
         *
         * @param name -- name from RenamedRepresentation
         *
         * @param binder -- De Brujin index
         */
        data class Variable(
            val name: String,
            val binder: Int,
        ) : Expr

        data class Lambda(
            val branches: List<Branch>,
        ) : Expr {
            data class Branch(
                val pattern: Pattern?,
                val body: Expr,
            )
        }

        data class If(
            val condition: Expr,
            val positive: Expr,
            val negative: Expr,
        ) : Expr

        data class Let(
            val pattern: Pattern,
            val matcher: Expr,
            val body: Expr,
        ) : Expr
    }

    sealed interface Pattern {
        data object Wildcard : Pattern

        data class Variable(
            val name: String,
        ) : Pattern

        data class Data(
            val constructor: Set<Declarations.Function.Name.Constructor>,
            val args: List<Pattern>,
        ) : Pattern

        data class NamedData(
            val name: String,
            val data: Data,
        ) : Pattern
    }

    sealed interface Literal :
        Expr,
        Pattern {
        data class TruVal(
            val value: Boolean,
        ) : Literal

        data class Num(
            val value: Long,
        ) : Literal

        data class Char(
            val value: kotlin.Char,
        ) : Literal

        data class String(
            val value: kotlin.String,
        ) : Literal
    }

    /**
     * Qualified type scheme `forall a1 .. an. context => type`
     */
    data class PolymorphicType(
        val type: Type,
        val boundTypeVariables: Int,
        val context: List<Predicate> = emptyList(),
    )

    /**
     * Class constraint `cls a`
     *
     * @param variable -- De Brujin index of the constrained type variable
     */
    data class Predicate(
        val cls: Declarations.Class.Name,
        val variable: Int,
    )

    sealed interface Type {
        /** Type variable, represented as De Brujin index */
        data class Variable(
            val index: Int,
        ) : Type

        data class Arrow(
            val argument: Type,
            val result: Type,
        ) : Type

        data class Data(
            val constructor: Declarations.Data.Name,
            val args: List<Type>,
        ) : Type {
            companion object {
                val char = Data(Declarations.Data.Name.Core.Char, arrayListOf())
                val truval = Data(Declarations.Data.Name.Core.TruVal, arrayListOf())
                val num = Data(Declarations.Data.Name.Core.Num, arrayListOf())
                val string = Data(Declarations.Data.Name.Core.List, arrayListOf(char))

                val unit = Data(Declarations.Data.Name.Core.Unit, arrayListOf())

                fun list(arg: Type) = Data(Declarations.Data.Name.Core.List, arrayListOf(arg))

                fun set(arg: Type) = Data(Declarations.Data.Name.Core.Set, arrayListOf(arg))

                fun tuple(
                    left: Type,
                    right: Type,
                ) = Data(Declarations.Data.Name.Core.Tuple, arrayListOf(left, right))
            }
        }
    }
}

/**
 * Name of the generated function which builds dictionary for an instance
 *
 * Contains dots, so it never clashes with user identifiers
 */
fun instanceDictionaryName(
    cls: DesugaredRepresentation.Declarations.Class.Name,
    head: DesugaredRepresentation.Declarations.Data.Name,
): DesugaredRepresentation.Declarations.Function.Name.User {
    val clsName =
        when (cls) {
            is DesugaredRepresentation.Declarations.Class.Name.Core -> cls.name
            is DesugaredRepresentation.Declarations.Class.Name.Defined -> "${cls.module ?: "top"}.${cls.name}"
        }
    val headName =
        when (head) {
            is DesugaredRepresentation.Declarations.Data.Name.Core -> head.toString()
            is DesugaredRepresentation.Declarations.Data.Name.Defined -> "${head.module ?: "top"}.${head.name}"
        }
    return DesugaredRepresentation.Declarations.Function.Name
        .User(null, "inst.$clsName.$headName")
}
