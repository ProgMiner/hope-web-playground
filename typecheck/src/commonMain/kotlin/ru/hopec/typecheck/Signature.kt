package ru.hopec.typecheck

import ru.hopec.desugarer.DesugaredRepresentation
import ru.hopec.desugarer.DesugaredRepresentation.Declarations
import ru.hopec.desugarer.DesugaredRepresentation.Declarations.Data.Name.Core
import ru.hopec.desugarer.DesugaredRepresentation.PolymorphicType
import ru.hopec.desugarer.DesugaredRepresentation.Type

internal data class Signature(
    val functions: Map<Declarations.Function.Name, PolymorphicType>,
    val data: Map<Declarations.Data.Name, Declarations.Data>,
) {
    fun extend(other: Declarations): Signature {
        val extendedFunctions = functions.toMutableMap()
        other.functions.forEach { entry -> extendedFunctions[entry.key] = entry.value.type }
        val extendedData = data.toMutableMap()
        extendedData.putAll(other.data.toMap())
        other.data.forEach { (dataName, dataDecl) ->
            val resultType =
                Type.Data(dataName, (0 until dataDecl.boundTypeVariables).map { Type.Variable(it) })
            dataDecl.constructors.forEach { (ctorName, args) ->
                val ctorKey = Declarations.Function.Name.Constructor(dataName, ctorName)
                val type =
                    if (args.isEmpty()) {
                        resultType
                    } else {
                        val argType = args.reduceRight { left, right -> Type.Data.tuple(left, right) }
                        Type.Arrow(argType, resultType)
                    }
                extendedFunctions[ctorKey] = PolymorphicType(type, dataDecl.boundTypeVariables)
            }
        }
        return Signature(extendedFunctions, extendedData)
    }

    fun extendLocal(other: DesugaredRepresentation.Module): Signature = extend(other.public).extend(other.private)

    fun extend(other: DesugaredRepresentation.Module): Signature = extend(other.public)

    fun extendAll(others: Iterable<DesugaredRepresentation.Module>): Signature = others.fold(this) { acc, cur -> acc.extend(cur) }

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

        val core =
            Signature(
                mapOf(
                    Declarations.Function.Name.Constructor(Core.TruVal, "true") to PolymorphicType(Type.Data.truval, 0),
                    Declarations.Function.Name.Constructor(Core.TruVal, "false") to PolymorphicType(Type.Data.truval, 0),
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
                    Declarations.Function.Name.Core("io.print") to
                        PolymorphicType(
                            Type.Arrow(Type.Data.string, Type.Data.unit),
                            0,
                        ),
                    Declarations.Function.Name.Core("io.getChar") to
                        PolymorphicType(Type.Data.char, 0),
                ),
                mapOf(
                    Core.Num to Declarations.Data(mapOf(), 0),
                    Core.Char to Declarations.Data(mapOf(), 0),
                    Core.Unit to Declarations.Data(mapOf(), 0),
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
