package ru.hopec.typecheck

import ru.hopec.typecheck.TypedRepresentation.*

data class Signature(
    val functions: Map<String, Type>,
    val data: Map<String, Data>,
) {
    fun extended(other: Module): Signature {
        val extendedFunctions = functions.toMutableMap()
        other.public.functions.forEach { entry -> extendedFunctions[entry.key] = entry.value.lambda.type }
        val extendedData = data.toMutableMap()
        extendedData.putAll(other.public.data)
        return Signature(extendedFunctions, extendedData)
    }
}
