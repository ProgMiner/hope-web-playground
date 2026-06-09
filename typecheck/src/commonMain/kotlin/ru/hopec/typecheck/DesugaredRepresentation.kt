package ru.hopec.typecheck

import ru.hopec.renamer.RenamedRepresentation

internal data class DesugaredRepresentation(
    val modules: Map<String, Module>,
    val topLevel: Declarations,
) {
    /**
     * Module representation
     */
    data class Module(
        val public: Declarations,
        val private: Declarations,
    )

    data class Declarations(
        val data: Map<TypedRepresentation.Declarations.Data.Name.Defined, TypedRepresentation.Declarations.Data>,
        val functions: Map<TypedRepresentation.Declarations.Function.Name, Function>,
    ) {
        data class Function(
            val lambda: Expr.Lambda,
            val type: TypedRepresentation.PolymorphicType,
        )
    }

    sealed interface Expr {
        data class Application(
            val left: Expr,
            val right: Expr,
        ) : Expr

        /**
         * Identifiers, defined outside of expression (i.e not variables)
         */
        data class Identifier(
            val name: TypedRepresentation.Declarations.Function.Name,
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
                val pattern: Pattern,
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

        sealed interface Literal : Expr {
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
    }

    sealed interface Pattern {
        data object Wildcard : Pattern

        data class Variable(
            val name: String,
        ) : Pattern

        data class Data(
            val constructor: TypedRepresentation.Declarations.Function.Name.Constructor,
            val args: List<Pattern>,
        ) : Pattern

        data class NamedData(
            val name: String,
            val data: Data,
        ) : Pattern
    }

    companion object {
        fun fromRenamed(renamedRepresentation: RenamedRepresentation): DesugaredRepresentation = TODO()
    }
}
