package ru.hopec.driver

import okio.Buffer
import ru.hopec.core.CompilationContext
import ru.hopec.parser.treesitter.JsTree
import ru.hopec.parser.treesitter.Tree
import kotlin.wasm.unsafe.Pointer
import kotlin.wasm.unsafe.UnsafeWasmMemoryApi
import kotlin.wasm.unsafe.withScopedMemoryAllocator

@OptIn(ExperimentalWasmJsInterop::class, ExperimentalJsExport::class, UnsafeWasmMemoryApi::class)
@JsExport
fun compile(input: Tree): JsNumber {
    val buffer = Buffer()
    Hopec(CompilationContext()).run(JsTree(input), buffer)
    val result = buffer.readByteArray()
    withScopedMemoryAllocator {
        val buffer = Pointer(0U)
        result.forEachIndexed { index, b -> buffer.plus(index).storeByte(b) }
        return result.size.toJsNumber()
    }
}
