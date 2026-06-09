package ru.hopec.typecheck

import ru.hopec.core.Representation
import ru.hopec.desugarer.DesugaredRepresentation
import ru.hopec.desugarer.DesugaredRepresentation.Type
import ru.hopec.desugarer.DesugaredRepresentation.PolymorphicType

/**
 * Type-annotated representation of compilation unit
 *
 * @param modules Map from names of declared modules to their representations
 *
 * @param topLevel Declarations from the global scope
 */
data class TypedRepresentation(
    val modules: Map<String, Module>,
    val topLevel: Declarations,
) : Representation {
    data class Module(
        val public: Declarations,
        val private: Declarations,
    )

    /**
     * Declarations of data and functions
     *
     * @param data Map from data names to their representations
     *
     * @param functions Map from function names to their representations.
     * Data constructors are also represented here as functions
     */
    data class Declarations(
        val data: Map<DesugaredRepresentation.Declarations.Data.Name.Defined, DesugaredRepresentation.Declarations.Data>,
        val functions: Map<DesugaredRepresentation.Declarations.Function.Name, Function>,
    ) {
        /**
         * Function representation
         *
         * For convenience, represented as lambda expression
         */
        data class Function(
            val lambda: Expr.Lambda,
            private val boundTypeVariables: Int,
        ) {
            val type = PolymorphicType(lambda.type, boundTypeVariables)
        }
    }

    sealed interface Expr {
        /** Type of subexpression in the context of the whole expression */
        val type: Type

        data class Application(
            override val type: Type,
            val left: Expr,
            val right: Expr,
        ) : Expr

        /**
         * Identifiers, defined outside of expression (i.e not variables)
         */
        data class Identifier(
            override val type: Type,
            val name: DesugaredRepresentation.Declarations.Function.Name,
        ) : Expr

        /**
         * Variables, defined in expression
         *
         * @param name -- name from RenamedRepresentation
         */
        data class Variable(
            override val type: Type,
            val name: String,
        ) : Expr

        data class Lambda(
            override val type: Type.Arrow,
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
        ) : Expr {
            override val type = positive.type
        }

        data class Let(
            val pattern: Pattern,
            val matcher: Expr,
            val body: Expr,
        ) : Expr {
            override val type = body.type
        }

        sealed interface Literal : Expr, Pattern {
            data class TruVal(
                val value: Boolean,
            ) : Literal {
                override val type = Type.Data.truval
            }

            data class Num(
                val value: Long,
            ) : Literal {
                override val type = Type.Data.num
            }

            data class Char(
                val value: kotlin.Char,
            ) : Literal {
                override val type = Type.Data.char
            }

            data class String(
                val value: kotlin.String,
            ) : Literal {
                override val type = Type.Data.string
            }
        }
    }

    sealed interface Pattern {
        /** Type of subpattern in the context of the whole expression */
        val type: Type

        data class Wildcard(
            override val type: Type,
        ) : Pattern

        data class Variable(
            override val type: Type,
            val name: String,
        ) : Pattern

        data class Data(
            override val type: Type.Data,
            val constructor: DesugaredRepresentation.Declarations.Function.Name.Constructor,
            val args: List<Pattern>,
        ) : Pattern

        data class NamedData(
            val name: String,
            val data: Data,
        ) : Pattern {
            override val type = data.type
        }
    }
}
