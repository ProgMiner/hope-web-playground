package ru.hopec.codegen.runtime

import ru.hopec.desugarer.DesugaredRepresentation.Declarations.Function.Name as FuncName

internal object WatImports {
    const val PRINT =
        "(import \"env\" \"print\" (func \$import.io.print (param i32)))"

    const val GET_CHAR =
        "(import \"env\" \"getChar\" (func \$import.io.getChar (result i32)))"

    val ALL: List<String> = listOf(PRINT, GET_CHAR)

    fun snippetsFor(used: Set<FuncName.Core>): List<String> =
        used.mapNotNull { core ->
            when (core.name) {
                "io.print" -> PRINT
                "io.getChar" -> GET_CHAR
                else -> null
            }
        }

    fun isBuiltin(name: FuncName.Core): Boolean =
        name.name == "io.print" || name.name == "io.getChar" || name.name == "io.resetHeap"

    fun importId(name: FuncName.Core): String =
        when (name.name) {
            "io.print" -> "\$import.io.print"
            "io.getChar" -> "\$import.io.getChar"
            else -> error("Not an io import: ${name.name}")
        }

    fun isRuntimeBuiltin(name: FuncName.Core): Boolean = name.name == "io.resetHeap"
}
