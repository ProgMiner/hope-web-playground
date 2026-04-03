package ru.hopec.driver

import okio.Sink
import ru.hopec.codegen.CodeGenPass
import ru.hopec.core.CompilationContext
import ru.hopec.core.then
import ru.hopec.parser.TreeSitterRepresentation
import ru.hopec.parser.treesitter.Tree
import ru.hopec.renamer.RenamerPass
import ru.hopec.typecheck.TypeCheckPass
import kotlin.wasm.unsafe.Pointer
import kotlin.wasm.unsafe.UnsafeWasmMemoryApi
import kotlin.wasm.unsafe.withScopedMemoryAllocator

class Hopec(private val context: CompilationContext) {

    fun run(input: Tree, output: Sink): Any? {
        val wasm = RenamerPass()
            .then(TypeCheckPass())
            .then(CodeGenPass())
            .run(TreeSitterRepresentation(input), context) ?: return null
        return wasm.compile(output)
    }
}

@OptIn(ExperimentalWasmJsInterop::class, ExperimentalJsExport::class, UnsafeWasmMemoryApi::class)
@JsExport
fun compile(input: Tree): JsNumber {
//    val buffer = Buffer()
//    Hopec(CompilationContext()).run(input, result) ?: return null
//    val result = buffer.readByteArray()
    val result = byteArrayOf(
        0x00, 0x61, 0x73, 0x6D, 0x01, 0x00, 0x00, 0x00,
        0x01, 0x0A, 0x02, 0x60, 0x02, 0x7F, 0x7F, 0x01,
        0x7F, 0x60, 0x00, 0x00, 0x03, 0x03, 0x02, 0x00,
        0x01, 0x04, 0x04, 0x01, 0x70, 0x00, 0x01, 0x05,
        0x03, 0x01, 0x00, 0x00, 0x06, 0x06, 0x01, 0x7F,
        0x00, 0x41, 0x08, 0x0B, 0x07, 0x18, 0x03, 0x06,
        0x6D, 0x65, 0x6D, 0x6F, 0x72, 0x79, 0x02, 0x00,
        0x05, 0x74, 0x61, 0x62, 0x6C, 0x65, 0x01, 0x00,
        0x03, 0x61, 0x64, 0x64, 0x00, 0x00, 0x09, 0x07,
        0x01, 0x00, 0x41, 0x00, 0x0B, 0x01, 0x01, 0x0A,
        0x0C, 0x02, 0x07, 0x00, 0x20, 0x00, 0x20, 0x01,
        0x6A, 0x0B, 0x02, 0x00, 0x0B
    )
    withScopedMemoryAllocator { allocator ->
        val buffer = Pointer(0U)
        result.forEachIndexed { index, b -> buffer.plus(index).storeByte(b) }
        return result.size.toJsNumber()
    }
}