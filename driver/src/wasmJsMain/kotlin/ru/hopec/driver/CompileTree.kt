@file:OptIn(ExperimentalJsExport::class)

package ru.hopec.driver

import okio.Buffer
import ru.hopec.core.CompilationContext
import ru.hopec.core.GlobalCompilationContext
import ru.hopec.core.JsObject
import ru.hopec.core.StatusSeverity
import ru.hopec.core.TranslationUnitRepresentations
import ru.hopec.core.set
import ru.hopec.core.toJsObject
import kotlin.wasm.unsafe.Pointer
import kotlin.wasm.unsafe.UnsafeWasmMemoryApi
import kotlin.wasm.unsafe.withScopedMemoryAllocator

@OptIn(ExperimentalWasmJsInterop::class, UnsafeWasmMemoryApi::class)
@JsExport
fun compile(input: CompilationInput): JsObject {
    val buffer = Buffer()
    val context = GlobalCompilationContext()
    Hopec(context).run(input.toHopec(), buffer)
    val result = buffer.readByteArray()
    withScopedMemoryAllocator {
        val buffer = Pointer(0U)
        result.forEachIndexed { index, b -> buffer.plus(index).storeByte(b) }
        return CompilationResult(result.size.toUInt(), context).toJsObject()
    }
}

data class CompilationResult(
    val size: UInt,
    val representations: List<TranslationUnitRepresentations>,
) {
    constructor(size: UInt, context: CompilationContext) : this(size, context.trees())

    @OptIn(ExperimentalWasmJsInterop::class)
    fun toJsObject() =
        JsObject().apply {
            set("size", size)
            set("representations", representations.map { it.toJsObject() }.toJsArray())
        }
}

@JsExport
fun statusTreeType(): String = "STATUS"

@JsExport
fun warningPrefix(): String = StatusSeverity.WARNING.prefix()

@JsExport
fun errorPrefix(): String = StatusSeverity.ERROR.prefix()
