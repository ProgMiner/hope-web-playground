package ru.hopec.desugarer

import ru.hopec.core.CompilationContext
import ru.hopec.core.Service
import ru.hopec.desugarer.DesugaredRepresentation.Declarations
import ru.hopec.desugarer.DesugaredRepresentation.PolymorphicType
import ru.hopec.desugarer.DesugaredRepresentation.Type
import ru.hopec.desugarer.DesugaredRepresentation.Declarations.Data.Name.Core

class SignatureService private constructor(
    private val context: CompilationContext,
    val functions: MutableMap<Declarations.Function.Name, PolymorphicType> = mutableMapOf(),
    val data: MutableMap<Declarations.Data.Name, Declarations.Data> = mutableMapOf()
) : Service {
    fun addFunction(name: Declarations.Function.Name, type: PolymorphicType) {
        functions[name] = type
    }

    fun addData(name: Declarations.Data.Name, value: Declarations.Data) {
        data[name] = value
    }

    fun getFunction(name: Declarations.Function.Name): PolymorphicType {
        val type = functions[name]
        if (type != null) return type

        val module = when (name) {
            is Declarations.Function.Name.Core -> throw IllegalStateException("core names should be in signature")
            is Declarations.Function.Name.User -> {
                name.module
            }
            is Declarations.Function.Name.Constructor -> {
                when (name.data) {
                    is Declarations.Data.Name.Core -> throw IllegalStateException("core data constructors should be in signature")
                    is Declarations.Data.Name.Defined -> {
                        name.data.module
                    }
                }
            }
        } ?: throw IllegalStateException("top level functions should already be in signature")
        desugarModule(module)
        return functions[name] ?: throw IllegalStateException("type not in signature after corresponding module desugaring")
    }

    private fun desugarModule(module: String) {
        context.resolveModule(module)?.runPass(DesugarerPass)
    }

    companion object {
        private val binaryNumOp =
            PolymorphicType(
                Type.Arrow(
                    Type.Data.tuple(Type.Data.num, Type.Data.num),
                    Type.Data.num,
                ),
                0,
            )

        private val numComparison =
            PolymorphicType(
                Type.Arrow(
                    Type.Data.tuple(Type.Data.num, Type.Data.num),
                    Type.Data.truval,
                ),
                0,
            )

        fun core(context: CompilationContext) =
            SignatureService(
                context,
                mutableMapOf(
                    Declarations.Function.Name.Constructor(Core.TruVal, "true") to PolymorphicType(Type.Data.truval, 0),
                    Declarations.Function.Name.Constructor(Core.TruVal, "false") to PolymorphicType(
                        Type.Data.truval,
                        0
                    ),
                    Declarations.Function.Name.Constructor(
                        Core.List,
                        "nil",
                    ) to PolymorphicType(Type.Data.list(Type.Variable(0)), 1),
                    Declarations.Function.Name.Constructor(Core.List, "cons") to
                            PolymorphicType(
                                Type.Arrow(
                                    Type.Data.tuple(Type.Variable(0), Type.Data.list(Type.Variable(0))),
                                    Type.Data.list(Type.Variable(0)),
                                ),
                                1,
                            ),
                    Declarations.Function.Name.Constructor(
                        Core.Set,
                        "emptySet",
                    ) to PolymorphicType(Type.Data.set(Type.Variable(0)), 1),
                    Declarations.Function.Name.Constructor(Core.Set, "setCons") to
                            PolymorphicType(
                                Type.Arrow(
                                    Type.Data.tuple(Type.Variable(0), Type.Data.set(Type.Variable(0))),
                                    Type.Data.set(Type.Variable(0)),
                                ),
                                1,
                            ),
                    Declarations.Function.Name.Constructor(Core.Tuple, "#") to
                            PolymorphicType(
                                Type.Arrow(
                                    Type.Variable(0),
                                    Type.Arrow(Type.Variable(1), Type.Data.tuple(Type.Variable(0), Type.Variable(1))),
                                ),
                                2,
                            ),
                    Declarations.Function.Name.Core("+") to binaryNumOp,
                    Declarations.Function.Name.Core("-") to binaryNumOp,
                    Declarations.Function.Name.Core("*") to binaryNumOp,
                    Declarations.Function.Name.Core("div") to binaryNumOp,
                    Declarations.Function.Name.Core("mod") to binaryNumOp,
                    Declarations.Function.Name.Core("<") to numComparison,
                    Declarations.Function.Name.Core("<=") to numComparison,
                    Declarations.Function.Name.Core(">") to numComparison,
                    Declarations.Function.Name.Core(">=") to numComparison,
                    Declarations.Function.Name.Core("=") to
                            PolymorphicType(
                                Type.Arrow(
                                    Type.Data.tuple(Type.Variable(0), Type.Variable(0)),
                                    Type.Data.truval,
                                ),
                                1,
                            ),
                ),
                mutableMapOf(
                    Core.Num to Declarations.Data(mapOf(), 0),
                    Core.Char to Declarations.Data(mapOf(), 0),
                    Core.TruVal to
                            Declarations.Data(
                                mapOf(
                                    "true" to listOf(),
                                    "false" to listOf(),
                                ),
                                0,
                            ),
                    Core.List to
                            Declarations.Data(
                                mapOf(
                                    "nil" to listOf(),
                                    "cons" to listOf(Type.Variable(0)),
                                ),
                                1,
                            ),
                    Core.Set to
                            Declarations.Data(
                                mapOf(
                                    "emptySet" to listOf(),
                                ),
                                1,
                            ),
                    Core.Tuple to
                            Declarations.Data(
                                mapOf(
                                    "#" to listOf(Type.Variable(0), Type.Variable(1)),
                                ),
                                2,
                            ),
                ),
            )
    }
}

fun CompilationContext.signatureService() =
    services().get<SignatureService>() ?: throw IllegalStateException("No signature service in compilation context")
