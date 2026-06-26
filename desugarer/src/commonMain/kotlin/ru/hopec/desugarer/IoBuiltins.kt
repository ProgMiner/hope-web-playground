package ru.hopec.desugarer

import ru.hopec.renamer.AstNode
import ru.hopec.desugarer.DesugaredRepresentation.Declarations.Function.Name.Core as CoreFunction

object IoBuiltins {
    val MODULES = listOf("io", "std")

    val PRINT = CoreFunction("io.print")
    val GET_CHAR = CoreFunction("io.getChar")
    val RESET_HEAP = CoreFunction("io.resetHeap")

    private val bySourceName =
        mapOf(
            "print" to PRINT,
            "getChar" to GET_CHAR,
            "resetHeap" to RESET_HEAP,
        )

    fun isBuiltinName(name: String): Boolean = name in bySourceName

    fun coreName(sourceName: String): CoreFunction = bySourceName[sourceName] ?: error("Unknown io builtin: $sourceName")

    fun isBuiltinStub(function: AstNode.FunctionDeclaration): Boolean {
        val equation = function.equations.singleOrNull() ?: return false
        val body = equation.body
        return body is AstNode.DecimalLiteral && body.value == 0L
    }
}
