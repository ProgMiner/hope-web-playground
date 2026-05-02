package ru.hopec.typecheck

import ru.hopec.typecheck.DesugaredRepresentation
import ru.hopec.typecheck.TypedRepresentation.Declarations
import ru.hopec.typecheck.TypedRepresentation.Declarations.Data.Name.Core
import ru.hopec.typecheck.TypedRepresentation.PolymorphicType
import ru.hopec.typecheck.TypedRepresentation.Type

internal data class Signature(
    val functions: Map<Declarations.Function.Name, PolymorphicType>,
    val data: Map<Declarations.Data.Name, Declarations.Data>,
) {
    fun extend(other: DesugaredRepresentation.Declarations): Signature {
        val extendedFunctions = functions.toMutableMap()
        other.functions.forEach { entry -> extendedFunctions[entry.key] = entry.value.type }
        val extendedData = data.toMutableMap()
        extendedData.putAll(other.data.toMap())
        return Signature(extendedFunctions, extendedData)
    }

    fun extendLocal(other: DesugaredRepresentation.Module): Signature = extend(other.public).extend(other.private)

    fun extend(other: DesugaredRepresentation.Module): Signature = extend(other.public)

    fun extendAll(others: Iterable<DesugaredRepresentation.Module>): Signature = others.fold(this) { acc, cur -> acc.extend(cur) }

    companion object {
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
                    Declarations.Function.Name.Constructor(Core.Tuple, "#") to
                        PolymorphicType(
                            Type.Arrow(
                                Type.Variable(0),
                                Type.Arrow(Type.Variable(1), Type.Data.tuple(Type.Variable(0), Type.Variable(1))),
                            ),
                            2,
                        ),
                    Declarations.Function.Name.Core("+") to
                        PolymorphicType(
                            Type.Arrow(
                                Type.Data.tuple(
                                    Type.Data.num,
                                    Type.Data.num,
                                ),
                                Type.Data.num,
                            ),
                            0,
                        ),
                ),
                mapOf(
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
